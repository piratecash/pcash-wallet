package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.account.AccountFixtures.SOLANA_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.STELLAR_ACCOUNT_ID
import cash.p.terminal.ledger.account.AccountFixtures.STELLAR_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.THORCHAIN_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.THORCHAIN_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.TON_PUBLIC_KEY
import cash.p.terminal.ledger.account.AccountFixtures.TRON_ADDRESS
import cash.p.terminal.ledger.account.AccountFixtures.TRON_PUBLIC_KEY
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LedgerAccountAppTest {

    private val fetchers = LedgerAppKeyFetchers(LedgerAccountApp.entries.map(::LedgerAccountKeyFetcher))

    @Test
    fun fetchKey_solanaApp_storesHexPublicKeyAtLedgerPath() = runTest {
        val device = FakeAccountApp(mapOf(GET_SOLANA_PUBKEY to SOLANA_PUBLIC_KEY.hexToByteArray()))

        val key = fetch(device, solana)

        assertEquals(SOLANA_PUBLIC_KEY, key.key)
        assertEquals("m/44'/501'/0'", key.spec.derivationPath)
        assertEquals(NO_DISPLAY, device.single()[P1])
    }

    @Test
    fun fetchKey_tronApp_storesBase58Address() = runTest {
        val key = fetch(FakeAccountApp(mapOf(GET_TRON_ADDRESS to tronAddressAnswer())), trc20)

        assertEquals(TRON_ADDRESS, key.key)
        assertEquals(TRON_PUBLIC_KEY, key.publicKey.toHexString())
        assertEquals("m/44'/195'/0'/0/0", key.spec.derivationPath)
    }

    @Test
    fun fetchKey_stellarApp_storesAccountId() = runTest {
        val device = FakeAccountApp(mapOf(GET_STELLAR_PUBKEY to STELLAR_PUBLIC_KEY.hexToByteArray()))

        assertEquals(STELLAR_ACCOUNT_ID, fetch(device, stellarAsset).key)
    }

    @Test
    fun fetchKey_tonApp_storesHexPublicKeyAtLedgerLivePath() = runTest {
        val device = FakeAccountApp(mapOf(GET_TON_PUBKEY to TON_PUBLIC_KEY.hexToByteArray()))

        val key = fetch(device, jetton)

        assertEquals(TON_PUBLIC_KEY, key.key)
        assertEquals("m/44'/607'/0'/0'/0'/0'", key.spec.derivationPath)
    }

    @Test
    fun fetchKey_thorchainApp_storesThorAddressWithCompressedKey() = runTest {
        val key = fetch(FakeAccountApp(mapOf(GET_THORCHAIN_ADDRESS to thorchainAddressAnswer())), securedAsset)

        assertEquals(THORCHAIN_ADDRESS, key.key)
        assertEquals(THORCHAIN_PUBLIC_KEY, key.publicKey.toHexString())
        assertEquals("m/44'/931'/0'/0/0", key.spec.derivationPath)
    }

    @Test
    fun fetcherFor_chainTokens_shareTheirChainApp() {
        assertEquals("Solana", fetchers.fetcherFor(solana)?.appName)
        assertEquals("Solana", fetchers.fetcherFor(TokenQuery(BlockchainType.Solana, TokenType.Spl("mint")))?.appName)
        assertEquals("Tron", fetchers.fetcherFor(TokenQuery(BlockchainType.Tron, TokenType.Native))?.appName)
        assertEquals("Tron", fetchers.fetcherFor(trc20)?.appName)
        assertEquals("Stellar", fetchers.fetcherFor(TokenQuery(BlockchainType.Stellar, TokenType.Native))?.appName)
        assertEquals("Stellar", fetchers.fetcherFor(stellarAsset)?.appName)
        assertEquals("TON", fetchers.fetcherFor(TokenQuery(BlockchainType.Ton, TokenType.Native))?.appName)
        assertEquals("TON", fetchers.fetcherFor(jetton)?.appName)
        assertEquals("THORChain", fetchers.fetcherFor(TokenQuery(BlockchainType.Thorchain, TokenType.Native))?.appName)
        assertEquals("THORChain", fetchers.fetcherFor(securedAsset)?.appName)
        assertNull(fetchers.fetcherFor(TokenQuery(BlockchainType.Mayachain, TokenType.Native)))
        assertNull(fetchers.fetcherFor(TokenQuery(BlockchainType.Ethereum, TokenType.Native)))
    }

    @Test
    fun verify_everyApp_asksDeviceToDisplayAddress() = runTest {
        val verifier = LedgerAccountAddressVerifier()
        val cases = listOf(
            Triple(solana, FakeAccountApp(mapOf(GET_SOLANA_PUBKEY to ByteArray(32))), P1),
            Triple(trc20, FakeAccountApp(mapOf(GET_TRON_ADDRESS to tronAddressAnswer())), P1),
            Triple(stellarAsset, FakeAccountApp(mapOf(GET_STELLAR_PUBKEY to ByteArray(32))), P2),
            Triple(jetton, FakeAccountApp(mapOf(GET_TON_PUBKEY to ByteArray(32))), P1),
            Triple(securedAsset, FakeAccountApp(mapOf(GET_THORCHAIN_ADDRESS to thorchainAddressAnswer())), P1),
        )

        cases.forEach { (query, device, flagIndex) ->
            verifier.verify(device, wallet(query), "address")

            assertEquals(DISPLAY, device.single()[flagIndex], query.id)
        }
        assertFalse(verifier.supports(TokenQuery(BlockchainType.Bitcoin, TokenType.Native)))
    }

    private suspend fun fetch(device: FakeAccountApp, query: TokenQuery) =
        checkNotNull(fetchers.fetcherFor(query)).fetchKey(device, query)

    private fun tronAddressAnswer(): ByteArray {
        val publicKey = TRON_PUBLIC_KEY.hexToByteArray()
        return byteArrayOf(publicKey.size.toByte()) + publicKey +
            byteArrayOf(TRON_ADDRESS.length.toByte()) + TRON_ADDRESS.encodeToByteArray()
    }

    private fun thorchainAddressAnswer() =
        THORCHAIN_PUBLIC_KEY.hexToByteArray() + THORCHAIN_ADDRESS.encodeToByteArray()

    private fun wallet(query: TokenQuery) = mockk<Wallet> { every { token.tokenQuery } returns query }

    private companion object {
        const val GET_SOLANA_PUBKEY = 0x05
        const val GET_TRON_ADDRESS = 0x02
        const val GET_STELLAR_PUBKEY = 0x02
        const val GET_TON_PUBKEY = 0x05
        const val GET_THORCHAIN_ADDRESS = 0x5504
        const val P1 = 2
        const val P2 = 3
        const val NO_DISPLAY: Byte = 0
        const val DISPLAY: Byte = 1
        val solana = TokenQuery(BlockchainType.Solana, TokenType.Native)
        val trc20 = TokenQuery(BlockchainType.Tron, TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
        val stellarAsset = TokenQuery(BlockchainType.Stellar, TokenType.Asset("USDC", STELLAR_ACCOUNT_ID))
        val jetton = TokenQuery(BlockchainType.Ton, TokenType.Jetton("EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"))
        val securedAsset = TokenQuery(BlockchainType.Thorchain, TokenType.ThorchainAsset("btc-btc"))
    }
}
