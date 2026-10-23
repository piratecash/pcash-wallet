package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.FakeKeyFetcher
import cash.p.terminal.ledger.FakeLedgerDevice
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.RECIPIENT_ADDRESS
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.bitcoinBip84
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.funding
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.key
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
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.hardware.BtcPreviousTransactionProvider
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
        const val KEY_B = "03a975f065a0f5ecfe849ba3d31d7fdadf4fdbea233ffc095057e9977f54f237d1"
    }
}
