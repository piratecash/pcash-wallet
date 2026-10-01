package cash.p.terminal.core.adapters.thorchain

import cash.p.terminal.R
import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineThorchainSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTransactionStatusAdapter
import cash.p.terminal.core.SignedOfflineThorchainTransaction
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.ThorchainKitWrapper
import cash.p.terminal.core.managers.thorchainSeed
import cash.p.terminal.core.managers.toAdapterState
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.TransactionSender.SendError
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.BigInteger

class ThorchainAdapter(
    thorchainKitWrapper: ThorchainKitWrapper,
    private val wallet: Wallet,
    dispatcherProvider: DispatcherProvider,
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    INativeBalanceProvider,
    ISendMemoAdapter,
    OfflineTransactionAdapter<SignedOfflineThorchainTransaction>,
    OfflineTransactionStatusAdapter {

    private val kit = thorchainKitWrapper.thorchainKit
    private val nativeFee = thorchainKitWrapper.nativeFee
    private val network = kit.network
    private val coroutineScope = CoroutineScope(dispatcherProvider.default + SupervisorJob())

    private val denom = when (val tokenType = wallet.token.type) {
        TokenType.Native -> network.nativeDenom
        is TokenType.ThorchainAsset -> tokenType.denom
        else -> throw IllegalArgumentException("Unsupported THORChain token type: $tokenType")
    }
    private val isNative = denom == network.nativeDenom

    override val balanceState: AdapterState
        get() = kit.syncStateFlow.value.toAdapterState()

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = kit.syncStateFlow.map { }

    override val transactionsSyncState: AdapterState
        get() = kit.transactionsSyncStateFlow.value.toAdapterState()

    override val transactionsSyncStateUpdatedFlow: Flow<Unit>
        get() = kit.transactionsSyncStateFlow.map { }

    override val balanceData: BalanceData
        get() = BalanceData(balance(denom))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = kit.getDenomBalanceFlow(denom).map { }

    override val nativeBalanceData: BalanceData
        get() = BalanceData(balance(network.nativeDenom))

    override val nativeBalanceUpdatedFlow: Flow<Unit>
        get() = kit.getDenomBalanceFlow(network.nativeDenom).map { }

    // Paid in the native coin, so a token wallet has nothing to reserve from its own balance.
    override val fee: StateFlow<BigDecimal> =
        if (isNative) {
            nativeFee.map { it.toAmount() }.stateIn(coroutineScope, SharingStarted.Eagerly, sendFee)
        } else {
            MutableStateFlow(BigDecimal.ZERO)
        }

    override val sendFee: BigDecimal
        get() = nativeFee.value.toAmount()

    override val sendFeeUpdatedFlow: Flow<Unit>
        get() = nativeFee.map { }

    // Reads the live fee: `fee` is re-published asynchronously and may lag behind sendFeeUpdatedFlow.
    override val maxSpendableBalance: BigDecimal
        get() = (balance(denom) - if (isNative) sendFee else BigDecimal.ZERO).max(BigDecimal.ZERO)

    override val receiveAddress: String
        get() = kit.receiveAddress

    override val isMainNet = true

    override val debugInfo = ""

    override val statusInfo: Map<String, Any>
        get() = kit.statusInfo()

    override fun attachLocalData() = Unit

    // ThorchainKitManager owns the kit's network, the fee request included.
    override fun resumeNetwork() = Unit

    override fun pauseNetwork() = Unit

    override fun stop() {
        coroutineScope.cancel()
    }

    override suspend fun refresh() {
        kit.refresh()
    }

    override fun validate(address: String) {
        Address.fromString(address, network)
    }

    override suspend fun getMinimumSendAmount(address: String): BigDecimal? = null

    override suspend fun send(amount: BigDecimal, address: String, memo: String?): String {
        checkFeeCovered(amount)
        return submitted { kit.send(Address.fromString(address, network), amount.toBaseUnits(), denom, memo, signer()) }
    }

    // MsgDeposit: a swap into the protocol's own module, used when the quote names no inbound vault.
    suspend fun deposit(asset: String, amount: BigDecimal, memo: String): String {
        checkFeeCovered(amount)
        return submitted { kit.deposit(Asset.fromString(asset), amount.toBaseUnits(), memo, signer()) }
    }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineThorchainTransaction {
        require(request is OfflineThorchainSignRequest) { "OfflineThorchainSignRequest is required" }
        checkFeeCovered(request.amount)
        val signed = kit.signSend(
            Address.fromString(request.address, network),
            request.amount.toBaseUnits(),
            signer(),
            denom,
            request.memo,
        )
        return SignedOfflineThorchainTransaction(
            rawHex = signed.raw.toRawHexString(),
            txHash = signed.hash.thorchainTxHash(),
            fee = sendFee,
        )
    }

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val normalizedRawHex = rawTransactionHex.trim()
        require(OfflineTransactionPayloadEncoder.isRawTransactionHex(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val raw = normalizedRawHex.hexToByteArray()
        return try {
            BroadcastRawTransactionResult(
                kit.broadcastRawTransaction(raw).thorchainTxHash(),
                BroadcastRawTransactionStatus.Submitted,
            )
        } catch (e: SendError.PossiblyAccepted) {
            BroadcastRawTransactionResult(e.txHash.thorchainTxHash(), BroadcastRawTransactionStatus.OutcomeUnknown)
        } catch (_: SendError.SequenceConsumed) {
            BroadcastRawTransactionResult(TxBuilder.txHash(raw), BroadcastRawTransactionStatus.SeqnoConsumed)
        }
    }

    override suspend fun transactionExists(txHash: String): Boolean =
        kit.transactionExists(txHash.thorchainTxHash())

    /** Null when the native balance covers the fee, and for a native send the amount as well. */
    fun feeShortfall(amount: BigDecimal): LocalizedException? {
        val nativeBalance = balance(network.nativeDenom)
        val required = if (isNative) amount + sendFee else sendFee
        if (nativeBalance >= required) return null
        return LocalizedException(
            R.string.Error_InsufficientBalanceForFee,
            network.assetResolver.assetFor(network.nativeDenom).ticker,
            nativeBalance.stripTrailingZeros().toPlainString(),
        )
    }

    fun recordUid(txHash: String) = ThorchainTransactionRecord.uid(txHash, denom)

    private fun checkFeeCovered(amount: BigDecimal) {
        feeShortfall(amount)?.let { throw it }
    }

    // An ambiguous broadcast counts as sent: history sync settles it, a retry could double-spend.
    private inline fun submitted(broadcast: () -> String): String =
        try {
            broadcast()
        } catch (e: SendError.PossiblyAccepted) {
            e.txHash.thorchainTxHash()
        }

    // Derived per call so the private key is not held for the adapter's lifetime.
    private fun signer() = Signer.getInstance(wallet.account.type.thorchainSeed(), network)

    private fun balance(bankDenom: String) = kit.getDenomBalance(bankDenom).toAmount()

    private fun BigInteger.toAmount() = toBigDecimal().movePointLeft(network.decimals)

    private fun BigDecimal.toBaseUnits() = movePointRight(network.decimals).toBigInteger()

    private companion object {
        // Cosmos tx hashes are uppercase hex; Midgard and THORNode report them that way.
        fun String.thorchainTxHash() = canonicalTransactionHash().uppercase()
    }
}
