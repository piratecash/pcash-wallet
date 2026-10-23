package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountSpeculos.fetchStoredKey
import cash.p.terminal.ledger.account.AccountSpeculos.seed
import cash.p.terminal.ledger.account.AccountSpeculos.signingKey
import cash.p.terminal.ledger.account.AccountSpeculos.verifyAddress
import cash.p.terminal.ledger.domain.LedgerBlindSigningRequiredException
import cash.p.terminal.ledger.ethereum.InMemoryPreferences
import cash.p.terminal.ledger.ethereum.LedgerCalClient
import cash.p.terminal.ledger.ethereum.LedgerCalClientTest.Companion.CERTIFICATE_DATA
import cash.p.terminal.ledger.ethereum.LedgerCalClientTest.Companion.CERTIFICATE_PROD_SIGNATURE
import cash.p.terminal.ledger.ethereum.LedgerCalClientTest.Companion.TRUSTED_NAME
import cash.p.terminal.ledger.ethereum.LedgerTokenDescriptorStore
import cash.p.terminal.ledger.ethereum.SignedDescriptor
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.solana.SolanaAppClient
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import com.solana.core.Transaction
import com.solana.core.TransactionInstruction
import com.solana.programs.SystemProgram
import com.solana.programs.TokenProgram
import com.solana.vendor.TweetNaclFast
import com.solana.vendor.bip32.wallet.DerivableType
import com.solana.vendor.bip32.wallet.SolanaBip44
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.transactions.ComputeBudgetProgram
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Needs speculos with the Solana app; see [Speculos]. Ed25519 is deterministic, so signatures must match the seed's. */
class LedgerSolanaSpeculosTest {

    private val seedAccount: HotAccount by lazy {
        val privateKey = SolanaBip44().getPrivateKeyFromSeed(seed, DerivableType.BIP44)
        HotAccount(TweetNaclFast.Signature.keyPair_fromSeed(privateKey).secretKey)
    }

    @Test
    fun fetchKey_solanaApp_matchesSeedKeyAtLedgerPath() = withSolana { session ->
        val stored = fetchStoredKey(session, sol)

        assertEquals(seedAccount.publicKey.toBase58(), PublicKey(stored.key.value.hexToByteArray()).toBase58())
    }

    @Test
    fun verify_solanaAddress_isConfirmedOnDevice() = withSolana { verifyAddress(it, sol) }

    // The kit adds compute budget instructions to every transfer; the app still shows it in clear.
    @Test
    fun sign_solTransferWithPriorityFeeBlindSigningOff_matchesSeedSignature() = withSolana {
        setBlindSigning(it, enabled = false)

        assertSignsLikeSeed(it, sol, SystemProgram.transfer(seedAccount.publicKey, PublicKey(RECIPIENT), 1_000_000))
    }

    @Test
    fun sign_splTransferBlindSigningOn_matchesSeedSignature() = withSolana {
        setBlindSigning(it, enabled = true)

        assertSignsLikeSeed(it, spl, splTransfer())
    }

    @Test
    fun sign_splTransferBlindSigningOff_throwsBlindSigningInstruction() = withSolana {
        setBlindSigning(it, enabled = false)
        val signer = LedgerSolanaSigner(signingKey(it, spl))

        val error = assertFailsWith<LedgerBlindSigningRequiredException> { transaction(splTransfer()).sign(signer) }
        assertEquals("Solana", error.appName)
        Speculos.dismiss("Blind signing must")
    }

    // Speculos trusts only Ledger's test PKI, so a production certificate is refused mid-session.
    @Test
    fun sign_splTransferOnlineDeviceRefusesCertificate_fallsBackToBlindSigning() = withSolana {
        setBlindSigning(it, enabled = true)
        val calClient = mockk<LedgerCalClient> {
            coEvery { certificate(any(), any(), any()) } returns
                SignedDescriptor(CERTIFICATE_DATA.hexToByteArray(), CERTIFICATE_PROD_SIGNATURE.hexToByteArray())
            coEvery { solanaTokenDescriptor(any()) } returns null
            coEvery { solanaTrustedName(any(), any()) } returns TRUSTED_NAME.hexToByteArray()
        }
        val clearSigning = LedgerSolanaClearSigning(calClient, LedgerTokenDescriptorStore(InMemoryPreferences()))

        assertSignsLikeSeed(it, spl, splTransfer(), clearSigning)
        coVerify { calClient.solanaTrustedName(any(), any()) }
    }

    private fun withSolana(block: suspend (LedgerSession) -> Unit) = runBlocking { Speculos.withSession(SOLANA, block) }

    private suspend fun assertSignsLikeSeed(
        session: LedgerSession,
        query: TokenQuery,
        instruction: TransactionInstruction,
        clearSigning: LedgerSolanaClearSigning? = null,
    ) {
        val signer = LedgerSolanaSigner(signingKey(session, query), clearSigning)
        val signed = transaction(instruction)
        Speculos.approving { signed.sign(signer) }

        assertContentEquals(transaction(instruction).apply { sign(seedAccount) }.signature, signed.signature)
    }

    // The instructions solana-kit sends: compute budget first, then the transfer.
    private fun transaction(instruction: TransactionInstruction) = Transaction().apply {
        add(ComputeBudgetProgram.setComputeUnitLimit(units = 300_000))
        add(ComputeBudgetProgram.setComputeUnitPrice(microLamports = 500_000))
        add(instruction)
        setRecentBlockHash(RECENT_BLOCKHASH)
    }

    private fun splTransfer() = TokenProgram.transferChecked(
        PublicKey(SOURCE_TOKEN_ACCOUNT),
        PublicKey(DESTINATION_TOKEN_ACCOUNT),
        1_500_000,
        6,
        seedAccount.publicKey,
        PublicKey(USDC),
    )

    private suspend fun setBlindSigning(session: LedgerSession, enabled: Boolean) {
        val configuration = session.withApp(SOLANA, "0") { SolanaAppClient(it).getAppConfiguration() }
        if (configuration.blindSigningEnabled != enabled) Speculos.toggleSetting("Blind signing")
    }

    private companion object {
        const val SOLANA = "Solana"
        const val RECIPIENT = "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"
        const val SOURCE_TOKEN_ACCOUNT = "3uetDDizgTtadDHZzyy9BqxrjQcozMEkxzbKLjYYLsr7"
        const val DESTINATION_TOKEN_ACCOUNT = "8Ckp5ZyGuYyzbnNhKjQFsg9nV1C5UxhgfhUFBMBjrNHJ"
        const val RECENT_BLOCKHASH = "EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N"
        val sol = TokenQuery(BlockchainType.Solana, TokenType.Native)
        const val USDC = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        val spl = TokenQuery(BlockchainType.Solana, TokenType.Spl(USDC))
    }
}
