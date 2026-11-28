package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountSpeculos.fetchStoredKey
import cash.p.terminal.ledger.account.AccountSpeculos.seed
import cash.p.terminal.ledger.account.AccountSpeculos.signingKey
import cash.p.terminal.ledger.account.AccountSpeculos.verifyAddress
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.runBlocking
import org.stellar.sdk.Account
import org.stellar.sdk.AssetTypeNative
import org.stellar.sdk.KeyPair
import org.stellar.sdk.Network
import org.stellar.sdk.Transaction
import org.stellar.sdk.TransactionBuilder
import org.stellar.sdk.operations.PaymentOperation
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Needs speculos with the Stellar app; see [Speculos]. Ed25519 is deterministic, so signatures must match the seed's. */
class LedgerStellarSpeculosTest {

    private val seedKeyPair = KeyPair.fromBip39Seed(seed, 0)

    @Test
    fun fetchKey_stellarApp_matchesSeedAccountId() = withStellar { session ->
        assertEquals(seedKeyPair.accountId, fetchStoredKey(session, xlm).key.value)
    }

    @Test
    fun verify_stellarAddress_isConfirmedOnDevice() = withStellar { verifyAddress(it, xlm) }

    // Built as stellar-kit builds a payment; a WalletConnect envelope reaches the signer as the same Transaction.
    @Test
    fun signTransaction_xlmPayment_matchesSeedSignature() = withStellar {
        val payment = PaymentOperation.builder()
            .destination(RECIPIENT)
            .asset(AssetTypeNative())
            .amount(BigDecimal("1.5"))
            .build()
        val transaction = TransactionBuilder(Account(seedKeyPair.accountId, SEQUENCE), Network.PUBLIC)
            .addOperation(payment)
            .setTimeout(TIMEOUT_SECONDS)
            .setBaseFee(Transaction.MIN_BASE_FEE)
            .build()

        val signature = Speculos.approving { LedgerStellarSigner(signingKey(it, xlm)).signTransaction(transaction) }

        assertContentEquals(seedKeyPair.sign(transaction.hash()), signature.signature.signature)
    }

    private fun withStellar(block: suspend (LedgerSession) -> Unit) = runBlocking { Speculos.withSession("Stellar", block) }

    private companion object {
        const val RECIPIENT = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        const val SEQUENCE = 123_456_789L
        const val TIMEOUT_SECONDS = 180L
        val xlm = TokenQuery(BlockchainType.Stellar, TokenType.Native)
    }
}
