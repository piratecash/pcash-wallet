package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.FakeKeyFetcher
import cash.p.terminal.ledger.FakeLedgerDevice
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.RECIPIENT_ADDRESS
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.bitcoinBip84
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.funding
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.key
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.litecoinBip84
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.lockingScript
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.mutableTransaction
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.output
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.rawHex
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.spend
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.storedKey
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.hardware.BtcPreviousTransactionProvider
import io.horizontalsystems.bitcoincore.extensions.hexToByteArray
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.messages.MessageSignError
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LedgerBtcSignerTest {

    private val inputKey = key(KEY_A)
    private val previous = funding(ScriptType.P2WPKH, inputKey, 60_000)
    private val storage = mockk<IHardwarePublicKeyStorage>()
    private val account = mockk<Account> {
        every { id } returns ACCOUNT_ID
        every { type } returns AccountType.LedgerDevice("device", "NanoSPlus", STORED_KEY)
        every { isHardwareWalletAccount } returns true
    }
    private val keptPrevious = object : BtcPreviousTransactionProvider {
        override fun getRawTransaction(hash: String): String? =
            rawHex(previous).takeIf { hash == previous.header.hash.toReversedHex() }
    }

    @Test
    fun signFullTransaction_otherKeyOnDevice_throwsIdentityMismatchWithoutSigning() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Bitcoin" to "2.1.0"))
        coEvery { storage.getKey(ACCOUNT_ID, BlockchainType.Bitcoin, bitcoinBip84.tokenType) } returns
            storedKey(bitcoinBip84, ACCOUNT_ID, STORED_KEY)
        val access = LedgerAppAccess(
            LedgerSession(device.provider),
            LedgerAppKeyFetchers(listOf(FakeKeyFetcher("Bitcoin", device, mapOf(bitcoinBip84 to "another-key")))),
            storage,
            LedgerUserOperations(),
        )
        val transaction = spendTransaction()

        assertFailsWith<LedgerIdentityMismatchException> { signer(access).signFullTransaction(transaction) }
        assertUnsigned(transaction)
    }

    @Test
    fun signFullTransaction_rejectedOnDevice_throwsCancellationAndLeavesInputsUnsigned() = runTest {
        val access = accessFailingWith(LedgerRejectedException())
        val transaction = spendTransaction()

        assertFailsWith<LedgerRejectedException> { signer(access).signFullTransaction(transaction) }
        assertUnsigned(transaction)
    }

    @Test
    fun signFullTransaction_previousTransactionMissing_throwsWithoutOpeningDevice() = runTest {
        val access = mockk<LedgerAppAccess>()
        val signer = LedgerBtcSigner(
            access,
            account,
            storedKey(bitcoinBip84, ACCOUNT_ID, STORED_KEY),
            object : BtcPreviousTransactionProvider {
                override fun getRawTransaction(hash: String): String? = null
            },
        ).apply { setTransactionSerializer(BtcTxFixtures.serializer) }

        assertFailsWith<LedgerSigningFailedException> { signer.signFullTransaction(spendTransaction()) }
        coVerify(exactly = 0) { access.signWithLedgerApp<Any>(any(), any(), any()) }
    }

    @Test
    fun signStandardMessage_upstreamVector_setsHeaderPerScriptTypeAndKeepsRs() = runTest {
        val deviceSignature = Base64.getDecoder().decode(HELLO_WORLD_TESTNET_SIGNATURE)

        mapOf(ScriptType.P2PKH to 32, ScriptType.P2WPKHSH to 36, ScriptType.P2WPKH to 40).forEach { (scriptType, header) ->
            val signature = messageSigner(bitcoinDevice(deviceSignature))
                .signStandardMessage(speculosKey(TESTNET_HELLO_WORLD_PATH), scriptType, "Hello world!")

            assertEquals(header, signature[0].toInt() and 0xFF, scriptType.name)
            assertContentEquals(deviceSignature.copyOfRange(1, 65), signature.copyOfRange(1, 65))
        }
    }

    // Recorded from the mainnet app on speculos (ledger-kit BitcoinMainnetSpeculosTest); recovering the path's key verifies them.
    @Test
    fun signStandardMessage_mainnetSpeculosSignatures_recoverKeyOfTheirPath() = runTest {
        listOf(
            Triple("m/44'/0'/0'/0/0", ScriptType.P2PKH, "20063dcbfd423c3c79671fa2dd083abd03736f98da76dd85d7ae03658717c16f020741b48308d9e062fc6931a61ef9c1a1f4c197c755611dc15e50ff401e48e63e"),
            Triple("m/49'/0'/0'/0/0", ScriptType.P2WPKHSH, "1fd51d8cbab1555a832a9a346e01422d37d4fda98d18c5918aebcf8d670d06c54f53a3e239079641ab0fd1415354195b477c6ac742c84d591d31fadaa9e9b6b632"),
            Triple("m/84'/0'/0'/0/0", ScriptType.P2WPKH, "1f0bac04efba27daf3b04c19b35f7820aaab911b63f3d489f521dc379a1026dbe86df21553395037c04f42c1445139eefab4dd8da7ab96fab807c15eab6dbc324d"),
        ).forEach { (path, scriptType, deviceSignature) ->
            val signature = messageSigner(bitcoinDevice(deviceSignature.hexToByteArray()))
                .signStandardMessage(speculosKey(path), scriptType, "Hello world!")

            assertEquals(deviceSignature.substring(2), signature.toHexString().substring(2), path)
        }
    }

    @Test
    fun signStandardMessage_changeAddress_asksDeviceForItsPath() = runTest {
        val device = bitcoinDevice(Base64.getDecoder().decode(HELLO_WORLD_TESTNET_SIGNATURE))
        val publicKey = key(speculosKey(TESTNET_HELLO_WORLD_PATH).publicKey, external = false, index = 7)

        messageSigner(device).signStandardMessage(publicKey, ScriptType.P2WPKH, "Hello world!")

        val apdu = device.sent.single { it[0] == 0xE1.toByte() && it[1] == 0x10.toByte() }
        assertEquals("058000005480000000800000000000000100000007", apdu.copyOfRange(5, 26).toHexString())
    }

    @Test
    fun signStandardMessage_bitcoinAppBelow230_throwsAppVersionTooOldWithoutSigning() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Bitcoin" to "2.2.4"))

        assertFailsWith<LedgerException.AppVersionTooOld> {
            messageSigner(device).signStandardMessage(speculosKey(TESTNET_HELLO_WORLD_PATH), ScriptType.P2WPKH, "Hello world!")
        }
        assertTrue(device.sent.none { it[0] == 0xE1.toByte() })
    }

    @Test
    fun signStandardMessage_signedByOtherKey_throwsSignatureMismatch() = runTest {
        val device = bitcoinDevice(Base64.getDecoder().decode(HELLO_WORLD_TESTNET_SIGNATURE))

        assertFailsWith<MessageSignError.SignatureMismatch> {
            messageSigner(device).signStandardMessage(inputKey, ScriptType.P2WPKH, "Hello world!")
        }
    }

    @Test
    fun signStandardMessage_litecoin_throwsUnsupportedFormatWithoutOpeningDevice() = runTest {
        val access = mockk<LedgerAppAccess>()
        val signer = LedgerBtcSigner(access, account, storedKey(litecoinBip84, ACCOUNT_ID, STORED_KEY), keptPrevious)

        assertFailsWith<MessageSignError.UnsupportedFormat> {
            signer.signStandardMessage(inputKey, ScriptType.P2WPKH, "Hello world!")
        }
        coVerify(exactly = 0) { access.signWithLedgerApp<Any>(any(), any(), any<String>(), any()) }
    }

    private fun bitcoinDevice(messageSignature: ByteArray) =
        FakeLedgerDevice(installedApps = mapOf("Bitcoin" to "2.3.0"), messageSignature = messageSignature)

    private fun messageSigner(device: FakeLedgerDevice): LedgerBtcSigner {
        coEvery { storage.getKey(ACCOUNT_ID, BlockchainType.Bitcoin, bitcoinBip84.tokenType) } returns
            storedKey(bitcoinBip84, ACCOUNT_ID, STORED_KEY)
        val access = LedgerAppAccess(
            LedgerSession(device.provider),
            LedgerAppKeyFetchers(listOf(FakeKeyFetcher("Bitcoin", device, mapOf(bitcoinBip84 to STORED_KEY)))),
            storage,
            LedgerUserOperations(),
        )
        return LedgerBtcSigner(access, account, storedKey(bitcoinBip84, ACCOUNT_ID, STORED_KEY), keptPrevious)
    }

    /** The key speculos and app-bitcoin-new tests derive from their shared seed. */
    private fun speculosKey(path: String): PublicKey {
        val seed = Mnemonic().toSeed(SPECULOS_MNEMONIC.split(" "))
        return key(HDWallet(seed, 0, HDWallet.Purpose.BIP84).privateKey(path).pubKey, external = true)
    }

    private fun accessFailingWith(error: Exception) = mockk<LedgerAppAccess> {
        coEvery { signWithLedgerApp<List<ByteArray>>(account, bitcoinBip84, any()) } throws error
    }

    private fun signer(access: LedgerAppAccess) =
        LedgerBtcSigner(access, account, storedKey(bitcoinBip84, ACCOUNT_ID, STORED_KEY), keptPrevious)
            .apply { setTransactionSerializer(BtcTxFixtures.serializer) }

    private fun spendTransaction() = mutableTransaction(
        inputs = listOf(spend(previous, ScriptType.P2WPKH, inputKey)),
        outputs = listOf(output(59_000, lockingScript(ScriptType.P2WPKH, key(KEY_B)), RECIPIENT_ADDRESS)),
    )

    private fun assertUnsigned(transaction: MutableTransaction) {
        assertTrue(transaction.inputsToSign.all { it.input.witness.isEmpty() && it.input.sigScript.isEmpty() })
    }

    private companion object {
        const val ACCOUNT_ID = "ledger-account"
        const val STORED_KEY = "stored-key"
        const val KEY_A = "031869567d5e88d988ff7baf6827983f89530ddd79dbaeadaa6ec538a8f03dea8b"
        const val TESTNET_HELLO_WORLD_PATH = "m/84'/1'/0'/0/0"
        // app-bitcoin-new tests/test_sign_message.py
        const val HELLO_WORLD_TESTNET_SIGNATURE =
            "IEOK4+JMK7FToR7XMzFCoAYh1nud1IKm9Wq3vXLSVk/lBay8rHCRp9bP6riyR5NDqXYyYf7cXgMQTHNz3SemwZI="
        const val SPECULOS_MNEMONIC = "glory promote mansion idle axis finger extra february uncover one trip resource " +
            "lawn turtle enact monster seven myth punch hobby comfort wild raise skin"
        const val KEY_B = "03a975f065a0f5ecfe849ba3d31d7fdadf4fdbea233ffc095057e9977f54f237d1"
    }
}
