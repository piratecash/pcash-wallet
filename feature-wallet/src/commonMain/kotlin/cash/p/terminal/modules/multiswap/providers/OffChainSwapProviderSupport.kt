package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.isEvm
import cash.p.terminal.core.isUtxoBased
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.resources.Res
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.isLitecoinMweb
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.bitcoincore.storage.UtxoFilters
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigDecimal
import cash.p.terminal.resources.swap_create_wallet_description
import cash.p.terminal.resources.swap_create_wallet_description_with_zcash

/** The order needs a deposit memo that the input network cannot carry. */
class SwapDepositMemoUnsupported : Throwable()

/** Input networks whose deposit transaction can carry a provider memo. */
internal val MEMO_CHAINS = setOf(
    BlockchainType.Stellar,
    BlockchainType.Ton,
    BlockchainType.Thorchain,
    BlockchainType.Mayachain,
)

private val BCH_PREFIX = Regex("^bitcoincash:", RegexOption.IGNORE_CASE)

// Off-chain providers reject the CashAddr prefix that bitcoin-kit includes in BCH addresses.
internal fun Token.normalizeSwapAddress(address: String): String =
    if (blockchainType == BlockchainType.BitcoinCash) address.replaceFirst(BCH_PREFIX, "") else address

class OffChainSwapProviderSupport(
    private val walletUseCase: WalletUseCase,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val adapterManager: IAdapterManager,
    private val swapProviderTransactionFactory: SwapProviderTransactionFactory,
    private val refundAddressReservation: ZcashRefundAddressReservation,
) {
    fun getCreateTokenActionRequired(
        tokens: List<Token>,
        useTransparentZcashRefundAddress: Boolean = true,
    ): ActionCreate? {
        val tokenIn = tokens.firstOrNull()
        val tokenZCashToCreate = if (
            tokenIn != null &&
            useTransparentZcashRefundAddress &&
            requiresTransparentRefundAddress(tokenIn)
        ) {
            refundAddressReservation.transparentToken()
        } else {
            null
        }
        val needCreateTransparentWallet =
            tokenZCashToCreate != null && walletUseCase.getWallet(tokenZCashToCreate) == null
        val tokensToAdd = tokens.filterTo(mutableSetOf()) { token ->
            walletUseCase.getWallet(token) == null
        }
        if (needCreateTransparentWallet) {
            tokensToAdd.add(tokenZCashToCreate)
        }

        return if (tokensToAdd.isNotEmpty()) {
            ActionCreate(
                inProgress = false,
                descriptionResId = if (!needCreateTransparentWallet) {
                    Res.string.swap_create_wallet_description
                } else {
                    Res.string.swap_create_wallet_description_with_zcash
                },
                tokensToAdd = tokensToAdd
            )
        } else {
            null
        }
    }

    fun getCreateTokenActionRequired(
        tokenIn: Token,
        tokenOut: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): ActionCreate? {
        return getCreateTokenActionRequired(
            tokens = listOf(tokenIn, tokenOut),
            useTransparentZcashRefundAddress = useTransparentZcashRefundAddress,
        )
    }

    /** Throws [ZcashRefundAddressUnavailable] instead of falling back to another address. */
    suspend fun temporaryRefundAddress(
        tokenIn: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): String? = if (usesTemporaryRefundAddress(tokenIn, useTransparentZcashRefundAddress)) {
        refundAddressReservation.current()
    } else {
        null
    }

    suspend fun refundAddressFor(
        tokenIn: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): String = temporaryRefundAddress(tokenIn, useTransparentZcashRefundAddress)
        ?: walletUseCase.getReceiveAddress(tokenIn)

    /** Call right before the request that creates the order. */
    suspend fun onRefundAddressSent(
        tokenIn: Token,
        address: String,
        useTransparentZcashRefundAddress: Boolean = true,
    ) {
        if (usesTemporaryRefundAddress(tokenIn, useTransparentZcashRefundAddress)) {
            refundAddressReservation.markSent(address)
        }
    }

    /** Marks the address sent even on a cache hit, so the caller's order cache must key on it. */
    suspend fun getRefundAddress(
        tokenIn: Token,
        useTransparentZcashRefundAddress: Boolean = true,
    ): String = refundAddressFor(tokenIn, useTransparentZcashRefundAddress).also {
        onRefundAddressSent(tokenIn, it, useTransparentZcashRefundAddress)
    }

    fun buildSwapProviderTransaction(
        provider: SwapProvider,
        transactionId: String,
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        amountOut: BigDecimal,
        subProviderId: String? = null,
        externalId: String? = null,
        walletAddress: String? = null,
    ) = swapProviderTransactionFactory.build(
        provider, transactionId, tokenIn, tokenOut, amountIn, amountOut,
        subProviderId = subProviderId, externalId = externalId, walletAddress = walletAddress
    )

    fun buildTransactionData(
        tokenIn: Token,
        amountIn: BigDecimal,
        depositAddress: String,
        memo: String?,
    ): SendTransactionData {
        return when {
            tokenIn.blockchainType.isEvm -> buildEvmTransactionData(tokenIn, amountIn, depositAddress)

            tokenIn.blockchainType == BlockchainType.Tron -> {
                SendTransactionData.Tron.Regular(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            tokenIn.blockchainType == BlockchainType.Stellar -> {
                SendTransactionData.Stellar.Regular(
                    amount = amountIn,
                    address = depositAddress,
                    memo = memo.orEmpty()
                )
            }

            tokenIn.blockchainType == BlockchainType.Thorchain ||
                    tokenIn.blockchainType == BlockchainType.Mayachain ->
                buildThorchainTransactionData(amountIn, depositAddress, memo)

            tokenIn.blockchainType == BlockchainType.Solana -> {
                SendTransactionData.Solana.Regular(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            tokenIn.blockchainType.isUtxoBased -> {
                // An MWEB send has no OP_RETURN output: the memo would be dropped silently.
                if (tokenIn.isLitecoinMweb && !memo.isNullOrBlank()) throw SwapDepositMemoUnsupported()
                SendTransactionData.Btc(
                    address = depositAddress,
                    memo = memo.orEmpty(),
                    amount = amountIn,
                    recommendedGasRate = null,
                    minimumSendAmount = null,
                    changeToFirstInput = false,
                    utxoFilters = UtxoFilters(),
                    feesMap = emptyMap()
                )
            }

            tokenIn.blockchainType == BlockchainType.Ton -> {
                SendTransactionData.Ton(
                    amount = amountIn,
                    address = depositAddress,
                    memo = memo
                )
            }

            tokenIn.blockchainType == BlockchainType.Monero -> {
                SendTransactionData.Monero(
                    amount = amountIn,
                    address = depositAddress
                )
            }

            tokenIn.isNativeBeam() -> SendTransactionData.Beam(depositAddress, amountIn, memo)

            else -> SendTransactionData.Unsupported
        }
    }

    fun onTransactionCompleted(
        transaction: SwapProviderTransaction,
        result: SendTransactionResult,
        depositTransactionHash: String? = null,
    ) {
        swapProviderTransactionsStorage.save(
            transaction.copy(
                outgoingRecordUid = result.getRecordUid(),
                date = System.currentTimeMillis(),
                depositTransactionHash = depositTransactionHash ?: transaction.depositTransactionHash,
            )
        )
    }

    private fun buildEvmTransactionData(
        tokenIn: Token,
        amountIn: BigDecimal,
        depositAddress: String,
    ): SendTransactionData {
        val adapter = adapterManager.getAdapterForToken<ISendEthereumAdapter>(tokenIn)
            ?: throw IllegalStateException("Ethereum adapter not found")
        val transactionData = adapter.getTransactionData(
            amountIn,
            Address(depositAddress)
        )

        return SendTransactionData.Evm(transactionData, null, amount = amountIn, recipientAddress = depositAddress)
    }

    // The deposit address is shared by all orders: without the memo the funds are not attributed to this one.
    private fun buildThorchainTransactionData(
        amountIn: BigDecimal,
        depositAddress: String,
        memo: String?,
    ): SendTransactionData =
        if (memo.isNullOrBlank()) {
            SendTransactionData.Unsupported
        } else {
            SendTransactionData.Thorchain.Send(address = depositAddress, amount = amountIn, memo = memo)
        }

    private fun Token.isNativeBeam() = blockchainType == BlockchainType.Beam && type == TokenType.Native

    private fun usesTemporaryRefundAddress(tokenIn: Token, useTransparentZcashRefundAddress: Boolean) =
        useTransparentZcashRefundAddress && requiresTransparentRefundAddress(tokenIn)

    private fun requiresTransparentRefundAddress(tokenIn: Token): Boolean =
        tokenIn.blockchainType == BlockchainType.Zcash &&
                (tokenIn.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified) ||
                        tokenIn.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded))
}
