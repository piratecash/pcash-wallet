package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountFixtures.TRX_TRANSFER
import cash.p.terminal.ledger.account.AccountSpeculos.fetchStoredKey
import cash.p.terminal.ledger.account.AccountSpeculos.seed
import cash.p.terminal.ledger.account.AccountSpeculos.signingKey
import cash.p.terminal.ledger.account.AccountSpeculos.verifyAddress
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.network.RawData
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.runBlocking
import java.security.Security
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Needs speculos with the Tron app, every setting off; see [Speculos]. The app signs with RFC 6979 nonces, as
 * tron-kit's software signer does, so signatures must match the seed's.
 */
class LedgerTronSpeculosTest {

    @BeforeTest
    fun setUp() {
        // tron-kit hashes addresses with the Keccak of the provider the running app installs.
        Security.addProvider(InternalBouncyCastleProvider.getInstance())
    }

    @Test
    fun fetchKey_tronApp_matchesSeedAddress() = withTron { session ->
        val privateKey = Signer.privateKey(seed, Network.Mainnet)

        assertEquals(Signer.address(privateKey, Network.Mainnet).base58, fetchStoredKey(session, trx).key.value)
    }

    @Test
    fun verify_tronAddress_isConfirmedOnDevice() = withTron { verifyAddress(it, usdt) }

    @Test
    fun sign_trxTransfer_matchesSeedSignature() = withTron { assertSignsLikeSeed(it, trx, TRX_TRANSFER) }

    // A USDT transfer signs in clear with every Tron setting off.
    @Test
    fun sign_trc20Transfer_matchesSeedSignature() = withTron { assertSignsLikeSeed(it, usdt, TRC20_TRANSFER) }

    private fun withTron(block: suspend (LedgerSession) -> Unit) = runBlocking { Speculos.withSession("Tron", block) }

    private suspend fun assertSignsLikeSeed(session: LedgerSession, query: TokenQuery, rawDataHex: String) {
        val transaction = CreatedTransaction(false, "", RawData(emptyList(), "", "", 0, 0, null), rawDataHex, null)

        val signature = Speculos.approving { LedgerTronSigner(signingKey(session, query)).sign(transaction) }

        assertContentEquals(Signer.getInstance(seed, Network.Mainnet).sign(transaction), signature)
    }

    private companion object {
        const val TRC20_TRANSFER =
            "0a023dce220895da42177db0050740e0d4b6c19c335aae01081f12a9010a31747970652e676f6f676c65617069732e636f6d" +
                "2f70726f746f636f6c2e54726967676572536d617274436f6e747261637412740a15417e70497d5c6c768aab8fa892da766a" +
                "ba58b389a6121541a614f803b6fd780986a42c78ec9c7f77e6ded13c2244a9059cbb000000000000000000000000e2ae49db" +
                "6a70b9b4757d2137a43b69b24a44578000000000000000000000000000000000000000000000000000000000002625a07080" +
                "80b3c19c3390018087a70e"
        val trx = TokenQuery(BlockchainType.Tron, TokenType.Native)
        val usdt = TokenQuery(BlockchainType.Tron, TokenType.Eip20("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
    }
}
