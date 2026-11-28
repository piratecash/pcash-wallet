package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor
import com.solana.core.PublicKey
import com.solana.core.Transaction
import com.solana.core.TransactionInstruction
import com.solana.programs.AssociatedTokenProgram
import com.solana.programs.TokenProgram
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * Keys and signatures of the speculos seed, derived independently with SLIP-10/BIP-32 and RFC 8032/6979
 * (ledger-kit speculos vectors). Transactions use made-up accounts and blocks.
 */
object AccountFixtures {
    const val SOLANA_PUBLIC_KEY = "1572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c"
    const val SOLANA_ADDRESS = "2SjBBhnRiESRWYPqSti1mM2u47aBa6vBg2YHVqcEmTKH"
    const val SOL_TRANSFER =
        "010001031572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c2121212121212121212121212121" +
            "2121212121212121212121212121212121210000000000000000000000000000000000000000000000000000000000000000" +
            "424242424242424242424242424242424242424242424242424242424242424201020200010c0200000040420f0000000000"
    const val SOL_TRANSFER_SIGNATURE =
        "9ac63d8b3402c3f759e34f88dfc47327615c85f0c42d34f116419afb1f2ee725a39a68a0d95e5266338ee11762001b5ef16c" +
            "9881f79c3ed2aa8efd3e3f6e170a"

    const val TRON_ADDRESS = "TMVkf58Mk6JiVHXFqScrFCCfy5cgMoKhN1"
    const val TRON_PUBLIC_KEY =
        "0468607df0f5bd24773280155303c61818140d8836cfbe0e34e6ed8ce7ddd14ae1ba772c18c87e9a89cbe1cdf0c6049579f02" +
            "5259f595e70c3160a32b515f6d9be"
    const val TRX_TRANSFER =
        "0a023dce220895da42177db0050740e0d4b6c19c335a67080112630a2d747970652e676f6f676c65617069732e636f6d2f70" +
            "726f746f636f6c2e5472616e73666572436f6e747261637412320a15417e70497d5c6c768aab8fa892da766aba58b389a612" +
            "1541e2ae49db6a70b9b4757d2137a43b69b24a44578018e0c65b708080b3c19c33"
    const val TRX_TRANSFER_SIGNATURE =
        "323ec5bf7d2e62a3bb67170f4a3beed07063bdb70805a277aa204fe4f93f3fad78b6c5079e72b77786182716b5ecde959945" +
            "ec116db579f2e39c08bf927b65f300"

    const val STELLAR_PUBLIC_KEY = "9a222500cf47b03d05edec04ed3294cece1de727ccadb401f47d6b4b230e81a0"
    const val STELLAR_ACCOUNT_ID = "GCNCEJIAZ5D3APIF5XWAJ3JSSTHM4HPHE7GK3NAB6R6WWSZDB2A2BQ5B"

    /** Public-network signature base of a payment: network id, envelope type 2, then the transaction XDR. */
    const val STELLAR_PAYMENT =
        "7ac33997544e3175d266bd022439b22cdb16508c01163f26e5cb2a3e1045a97900000002000000009a222500cf47b03d05ed" +
            "ec04ed3294cece1de727ccadb401f47d6b4b230e81a0000000640000001cbe991a1400000000000000000000000100000000" +
            "00000001000000003030303030303030303030303030303030303030303030303030303030303030000000000000000000bc" +
            "614e00000000"
    const val STELLAR_PAYMENT_SIGNATURE =
        "aa31757c16d0140d46fd3abf8368a6dc2cf729d71b8c79e2486d23eb917014905c12e9dc61fb92f44f4f37c7b37c17b26f52" +
            "966f528135e87800f55ad0640007"

    const val TON_PUBLIC_KEY = "9aff66dcc01b79787f6038c8070b8f7b9f27e381297c846a59f743bb075ed61c"
    const val TON_RAW_ADDRESS = "0:d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d2679"

    const val THORCHAIN_PUBLIC_KEY = "0335f35211b872a0a6a5e0cb54885e82e8d71c7c945111d6f782db1254ba2a2d8e"
    const val THORCHAIN_ADDRESS = "thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca"

    const val ACCOUNT_ID = "ledger-account"

    const val SPL_OWNER = "2SjBBhnRiESRWYPqSti1mM2u47aBa6vBg2YHVqcEmTKH"
    const val SPL_RECIPIENT = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    const val SPL_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    const val SPL_SOURCE = "3uetDDizgTtadDHZzyy9BqxrjQcozMEkxzbKLjYYLsr7"
    const val SPL_DESTINATION = "FGETo8T8wMcN2wCjav8VK6eh3dLk63evNDPxzLSJra8B"

    /** A USDC TransferChecked as solana-kit builds it, creating the recipient's account when [newRecipient]. */
    fun splTransferMessage(newRecipient: Boolean = false): ByteArray {
        val createAccount = AssociatedTokenProgram.createAssociatedTokenAccountInstruction(
            mint = PublicKey(SPL_MINT),
            associatedAccount = PublicKey(SPL_DESTINATION),
            owner = PublicKey(SPL_RECIPIENT),
            payer = PublicKey(SPL_OWNER),
        )
        val transfer = TokenProgram.transferChecked(
            PublicKey(SPL_SOURCE), PublicKey(SPL_DESTINATION), 1_500_000, 6, PublicKey(SPL_OWNER), PublicKey(SPL_MINT),
        )
        return solanaMessage(*listOfNotNull(createAccount.takeIf { newRecipient }, transfer).toTypedArray())
    }

    fun solanaMessage(vararg instructions: TransactionInstruction): ByteArray = Transaction().apply {
        add(*instructions)
        feePayer = PublicKey(SPL_OWNER)
        setRecentBlockHash("EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N")
    }.serializeMessage()

    val account = mockk<Account> {
        every { id } returns ACCOUNT_ID
        every { type } returns AccountType.LedgerDevice("device", "NanoSPlus", "wallet-key")
        every { isHardwareWalletAccount } returns true
    }

    /** The key the fetcher stores for [query]: its Ledger path, [key] and [publicKey]. */
    fun storedKey(query: TokenQuery, key: String, publicKey: ByteArray = byteArrayOf()) = HardwarePublicKey(
        accountId = ACCOUNT_ID,
        blockchainType = query.blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = query.tokenType,
        key = SecretString(key),
        derivationPath = checkNotNull(HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)).derivationPath,
        publicKey = publicKey,
        derivedPublicKey = publicKey,
    )

    /** A signing key whose device check passes and whose app answers through [device]. */
    fun signingKey(device: LedgerTransport, storedKey: HardwarePublicKey): LedgerSigningKey {
        val access = mockk<LedgerAppAccess> {
            coEvery { signWithLedgerApp<Any>(account, any(), any()) } coAnswers {
                thirdArg<suspend (LedgerTransport) -> Any>().invoke(device)
            }
        }
        return LedgerSigningKey(access, account, storedKey)
    }
}

/**
 * Answers every instruction (keyed by INS, or by `CLA shl 8 or INS`) with its fixed payload, a chunked command only
 * on the APDU [isLastChunk] picks; records each APDU.
 */
class FakeAccountApp(
    private val answers: Map<Int, ByteArray> = emptyMap(),
    private val isLastChunk: (ByteArray) -> Boolean = { true },
) : LedgerTransport {
    val apdus = mutableListOf<ByteArray>()

    override suspend fun exchange(apdu: ByteArray): ByteArray {
        apdus += apdu
        val ins = apdu[1].toInt() and 0xFF
        val claIns = (apdu[0].toInt() and 0xFF) shl 8 or ins
        val answer = answers[claIns] ?: answers[ins] ?: ByteArray(0)
        return (if (isLastChunk(apdu)) answer else ByteArray(0)) + OK
    }

    override fun close() = Unit

    fun single(): ByteArray = apdus.single()

    fun data(): ByteArray = single().copyOfRange(APDU_HEADER, single().size)

    private companion object {
        const val APDU_HEADER = 5
        val OK = byteArrayOf((StatusWord.OK shr 8).toByte(), StatusWord.OK.toByte())
    }
}
