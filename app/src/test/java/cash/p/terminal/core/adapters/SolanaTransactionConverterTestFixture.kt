package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.models.FullTokenTransfer
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.TokenTransfer
import io.horizontalsystems.solanakit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

internal object SolanaTransactionConverterTestFixture {

    const val USER_ADDRESS = "USER_ADDRESS"

    val solanaBlockchain = Blockchain(BlockchainType.Solana, "Solana", null)

    val baseToken = Token(
        coin = Coin(uid = "solana", name = "Solana", code = "SOL"),
        blockchain = solanaBlockchain,
        type = TokenType.Native,
        decimals = 9
    )

    val source = TransactionSource(
        blockchain = solanaBlockchain,
        account = mockk<Account>(relaxed = true),
        meta = null
    )

    fun createConverter(coinManager: ICoinManager): SolanaTransactionConverter {
        val solanaKit = mockk<SolanaKit>(relaxed = true)
        every { solanaKit.receiveAddress } returns USER_ADDRESS
        val solanaKitWrapper = mockk<SolanaKitWrapper>(relaxed = true)
        every { solanaKitWrapper.solanaKit } returns solanaKit

        return SolanaTransactionConverter(
            coinManager = coinManager,
            source = source,
            baseToken = baseToken,
            solanaKitWrapper = solanaKitWrapper
        )
    }

    fun tokenTransferTransaction(
        hash: String,
        from: String,
        to: String,
        mint: String,
        incoming: Boolean,
        amount: BigDecimal,
        decimals: Int = 6,
    ): FullTransaction {
        val transaction = Transaction(
            hash = hash,
            timestamp = 1_700_000_000L,
            from = from,
            to = to,
            amount = null,
            pending = false
        )
        val tokenTransfer = FullTokenTransfer(
            tokenTransfer = TokenTransfer(
                transactionHash = hash,
                mintAddress = mint,
                incoming = incoming,
                amount = amount
            ),
            mintAccount = MintAccount(address = mint, decimals = decimals, isNft = false)
        )
        return FullTransaction(transaction, listOf(tokenTransfer))
    }
}
