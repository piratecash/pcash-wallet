package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.CHANGE_ADDRESS
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.RECIPIENT_ADDRESS
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.funding
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.lockingScript
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.mutableTransaction
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.output
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.rawHex
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.spec
import cash.p.terminal.ledger.bitcoin.BtcTxFixtures.spend
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.BtcPreviousTransactionProvider
import cash.p.terminal.wallet.hardware.HardwareAddressLocator
import cash.p.terminal.wallet.hardware.HardwareAddressPosition
import io.horizontalsystems.bitcoincore.extensions.hexToByteArray
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.io.BitcoinInputMarkable
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.hdwalletkit.ECKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.Utils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs speculos with the Bitcoin or the Litecoin app; see [Speculos]. */
class LedgerBtcSpeculosTest {

    private val seedKeys = HDKeychain(Mnemonic().toSeed(Speculos.SEED.split(" ")))

    @Test
    fun fetchKey_bitcoinEveryPurpose_matchesKeysDerivedFromSeed() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Bitcoin.appName) { session ->
            assertKeysMatchSeed(
                session,
                LedgerBtcApp.Bitcoin,
                mapOf(
                    BtcTxFixtures.bitcoinBip44 to HDExtendedKeyVersion.xpub,
                    BtcTxFixtures.bitcoinBip49 to HDExtendedKeyVersion.ypub,
                    BtcTxFixtures.bitcoinBip84 to HDExtendedKeyVersion.zpub,
                    BtcTxFixtures.bitcoinBip86 to HDExtendedKeyVersion.xpub,
                ),
            )
        }
    }

    @Test
    fun fetchKey_litecoinLegacyApp_matchesKeysDerivedFromSeed() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Litecoin.appName) { session ->
            assertKeysMatchSeed(
                session,
                LedgerBtcApp.Litecoin,
                mapOf(
                    BtcTxFixtures.litecoinBip44 to HDExtendedKeyVersion.Ltub,
                    BtcTxFixtures.litecoinBip84 to HDExtendedKeyVersion.zpub,
                ),
            )
        }
    }

    @Test
    fun signFullTransaction_bitcoinNativeSegwitWithChange_signsWithTheInputKey() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Bitcoin.appName) { session ->
            assertSignsWithInputKey(session, LedgerBtcApp.Bitcoin, BtcTxFixtures.bitcoinBip84, ScriptType.P2WPKH)
        }
    }

    @Test
    fun signFullTransaction_bitcoinTaprootWithChange_signsWithTheTweakedInputKey() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Bitcoin.appName) { session ->
            assertSignsWithInputKey(session, LedgerBtcApp.Bitcoin, BtcTxFixtures.bitcoinBip86, ScriptType.P2TR)
        }
    }

    @Test
    fun signFullTransaction_litecoinLegacyWithChange_signsWithTheInputKey() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Litecoin.appName) { session ->
            assertSignsWithInputKey(session, LedgerBtcApp.Litecoin, BtcTxFixtures.litecoinBip44, ScriptType.P2PKH)
        }
    }

    @Test
    fun signFullTransaction_litecoinNativeSegwitWithChange_signsWithTheInputKey() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Litecoin.appName) { session ->
            assertSignsWithInputKey(session, LedgerBtcApp.Litecoin, BtcTxFixtures.litecoinBip84, ScriptType.P2WPKH)
        }
    }

    @Test
    fun verify_bitcoinNativeSegwitAddress_isConfirmedOnDevice() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Bitcoin.appName) { session ->
            verifyOnDevice(session, LedgerBtcApp.Bitcoin, BtcTxFixtures.bitcoinBip84)
        }
    }

    @Test
    fun verify_litecoinLegacyAddress_isConfirmedOnDevice() = runBlocking {
        Speculos.withSession(LedgerBtcApp.Litecoin.appName) { session ->
            verifyOnDevice(session, LedgerBtcApp.Litecoin, BtcTxFixtures.litecoinBip44)
        }
    }

    // The device rejects a policy or path that is not its own, so a completed display proves the request.
    private suspend fun verifyOnDevice(session: LedgerSession, app: LedgerBtcApp, query: TokenQuery) {
        val fetcher = LedgerBtcKeyFetcher(app)
        val storedKey = session.withApp(app.appName, "0") { fetcher.fetchKey(it, query) }.toHardwarePublicKey(ACCOUNT_ID)
        val receiveAdapter = mockk<IReceiveAdapter>(moreInterfaces = arrayOf(HardwareAddressLocator::class)) {
            coEvery { (this@mockk as HardwareAddressLocator).locate(ADDRESS) } returns HardwareAddressPosition(false, 2)
        }
        val adapterManager = mockk<IAdapterManager>()
        val wallet = mockk<Wallet> {
            every { token.tokenQuery } returns query
            every { hardwarePublicKey } returns storedKey
        }
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns receiveAdapter

        Speculos.approving {
            session.withApp(app.appName, "0") { LedgerBtcAddressVerifier(adapterManager).verify(it, wallet, ADDRESS) }
        }
    }

    private suspend fun assertKeysMatchSeed(
        session: LedgerSession,
        app: LedgerBtcApp,
        expectedVersions: Map<TokenQuery, HDExtendedKeyVersion>,
    ) {
        val fetcher = LedgerBtcKeyFetcher(app)
        val keys = session.withApp(app.appName, "0") { transport ->
            expectedVersions.keys.map { fetcher.fetchKey(transport, it) }
        }
        keys.zip(expectedVersions.values).forEach { (key, version) ->
            val account = seedKeys.getKeyByPath(key.spec.derivationPath)
            assertEquals(account.serializePublic(version.value), key.key, key.spec.derivationPath)
            assertContentEquals(account.pubKey, key.publicKey)
        }
    }

    private suspend fun assertSignsWithInputKey(
        session: LedgerSession,
        app: LedgerBtcApp,
        query: TokenQuery,
        type: ScriptType,
    ) {
        val accountPath = spec(query).derivationPath
        val inputKey = seedKey("$accountPath/0/0", external = true)
        val changeKey = seedKey("$accountPath/1/0", external = false)
        val previous = funding(type, inputKey, 100_000)
        val transaction = mutableTransaction(
            inputs = listOf(spend(previous, type, inputKey)),
            outputs = listOf(
                output(60_000, lockingScript(type, seedKey("m/44'/0'/1'/0/0", external = true)), RECIPIENT_ADDRESS),
                output(39_000, lockingScript(type, changeKey), CHANGE_ADDRESS),
            ),
            changeKey = changeKey,
        )

        val signed = Speculos.approving { signer(session, app, query, previous).signFullTransaction(transaction) }

        val raw = BtcTxFixtures.serializer.deserialize(BitcoinInputMarkable(signed.serializedTx.hexToByteArray()))
        assertTrue(inputKey.verifies(signatureOf(raw, type), transaction, type))
    }

    private suspend fun signer(
        session: LedgerSession,
        app: LedgerBtcApp,
        query: TokenQuery,
        previous: FullTransaction,
    ): LedgerBtcSigner {
        val fetcher = LedgerBtcKeyFetcher(app)
        val storedKey = session.withApp(app.appName, "0") { fetcher.fetchKey(it, query) }.toHardwarePublicKey(ACCOUNT_ID)
        val storage = mockk<IHardwarePublicKeyStorage> {
            coEvery { getKey(ACCOUNT_ID, query.blockchainType, query.tokenType) } returns storedKey
        }
        val account = mockk<Account> {
            every { id } returns ACCOUNT_ID
            every { type } returns AccountType.LedgerDevice("speculos", "NanoSPlus", storedKey.key.value)
            every { isHardwareWalletAccount } returns true
        }
        val previousTransactions = object : BtcPreviousTransactionProvider {
            override fun getRawTransaction(hash: String): String? =
                rawHex(previous).takeIf { hash == previous.header.hash.toReversedHex() }
        }
        val access = LedgerAppAccess(session, LedgerAppKeyFetchers(listOf(fetcher)), storage, LedgerUserOperations())
        return LedgerBtcSigner(access, account, storedKey, previousTransactions)
            .apply { setTransactionSerializer(BtcTxFixtures.serializer) }
    }

    private fun seedKey(path: String, external: Boolean): PublicKey =
        BtcTxFixtures.key(seedKeys.getKeyByPath(path).pubKey, external)

    private fun signatureOf(raw: FullTransaction, type: ScriptType): ByteArray = when (type) {
        ScriptType.P2PKH -> raw.inputs.single().sigScript.let { it.copyOfRange(1, 1 + it[0].toInt()) }
        else -> raw.inputs.single().witness.first()
    }

    // Checked against the kit's own sighash for input 0: SIGHASH_DEFAULT for taproot, SIGHASH_ALL otherwise.
    private fun PublicKey.verifies(signature: ByteArray, transaction: MutableTransaction, type: ScriptType): Boolean {
        val (tx, inputs, outputs) = Triple(transaction.transaction, transaction.inputsToSign, transaction.outputs)
        val serializer = BtcTxFixtures.serializer
        if (type == ScriptType.P2TR) {
            val preimage = serializer.serializeForTaprootSignature(tx, inputs, outputs, 0)
            return ECKey(publicKey).tweakedOutputKey.verifySchnorr(Utils.taggedHash("TapSighash", preimage), signature)
        }
        assertEquals(SIGHASH_ALL, signature.last())
        val preimage = serializer.serializeForSignature(tx, inputs, outputs, 0, type.isWitness) +
            byteArrayOf(SIGHASH_ALL, 0, 0, 0)
        return ECKey(publicKey).verifySignature(preimage, signature.copyOf(signature.size - 1))
    }

    private companion object {
        const val ACCOUNT_ID = "speculos-account"
        const val ADDRESS = "address-shown-on-the-phone"
        const val SIGHASH_ALL: Byte = 1
    }
}
