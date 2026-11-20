package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTronSignRequest
import cash.p.terminal.core.SignedOfflineTronTransaction
import cash.p.terminal.core.canonicalTransactionHash
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.core.managers.RawTransactionHex
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.send.tron.toTronFeeBreakdown
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.ReceiveAccountStatus
import io.horizontalsystems.tronkit.models.AccountResources
import io.horizontalsystems.tronkit.models.AccountStatus
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.RawTransactionBroadcastStatus
import io.horizontalsystems.tronkit.models.RawTransactionRetryMetadata
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.network.NowBlock
import io.horizontalsystems.tronkit.transaction.Fee
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

abstract class BaseTronAdapter(
    tronKitWrapper: TronKitWrapper,
    val decimal: Int
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    ISendTronAdapter,
    OfflineTransactionAdapter<SignedOfflineTronTransaction> {

    val tronKit = tronKitWrapper.tronKit
    protected val signer: Signer? = tronKitWrapper.signer

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var networkJob: Job? = null
    // Starts at the worst case (activating the recipient), so the first estimate is always an observable change.
    private val _fee = MutableStateFlow(
        tronKit.accountActivationFees().toTronFeeBreakdown(TronAdapter.decimal).totalFee
    )

    // In TRX: the cost of sending the whole balance to an activated address.
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()

    override val debugInfo: String
        get() = ""

    override val statusInfo: Map<String, Any>
        get() = tronKit.statusInfo()

    // IAdapter

    override fun resumeNetwork() {
        networkJob = coroutineScope.launch {
            balanceUpdatedFlow.collect {
                estimateFeeForMax()
            }
        }
    }

    override fun pauseNetwork() {
        networkJob?.cancel()
    }

    override fun stop() {
        coroutineScope.cancel()
    }

    // A failed estimate keeps the previous value.
    private suspend fun estimateFeeForMax() {
        tryOrNull {
            val amount = maxOf(balanceData.available, BigDecimal.ONE.movePointLeft(decimal))
            val breakdown = estimateFee(amount, tronKit.address).toTronFeeBreakdown(TronAdapter.decimal)
            _fee.value = breakdown.totalFee.stripTrailingZeros()
        }
    }

    // IReceiveAdapter

    override suspend fun accountStatus(address: String): ReceiveAccountStatus =
        tronKit.accountStatus(Address.fromBase58(address)).toReceiveAccountStatus(hasSigner = signer != null)

    override val receiveAddress: String
        get() = tronKit.address.base58

    override val isMainNet: Boolean
        get() = tronKit.network == Network.Mainnet

    // ISendTronAdapter

    override val accountResourcesFlow: StateFlow<AccountResources?>
        get() = tronKit.accountResourcesFlow

    override suspend fun refreshAccountResources() = withContext(Dispatchers.IO) {
        tronKit.refreshAccountResources()
    }

    override fun accountActivationFees(): List<Fee> = tronKit.accountActivationFees()

    override suspend fun estimateFee(contract: Contract): List<Fee> = withContext(Dispatchers.IO) {
        tronKit.estimateFee(contract)
    }

    override suspend fun estimateFee(amount: BigDecimal, to: Address): List<Fee> =
        estimateFee(transferContract(amount, to))

    override suspend fun estimateFee(transaction: CreatedTransaction): List<Fee> = withContext(Dispatchers.IO) {
        tronKit.estimateFee(transaction)
    }

    override suspend fun send(amount: BigDecimal, to: Address, feeLimit: Long?): String =
        send(transferContract(amount, to), feeLimit)

    override suspend fun send(contract: Contract, feeLimit: Long?): String {
        val signer = signer ?: throw TronSignerNotInitializedException()

        return tronKit.send(contract, signer, feeLimit)
    }

    override suspend fun send(createdTransaction: CreatedTransaction): String {
        val signer = signer ?: throw TronSignerNotInitializedException()

        return tronKit.send(createdTransaction, signer)
    }

    override suspend fun signOffline(request: OfflineSignRequest): SignedOfflineTronTransaction {
        require(request is OfflineTronSignRequest) { "OfflineTronSignRequest is required" }
        val signer = signer ?: throw TronSignerNotInitializedException()
        val signed = tronKit.signedTransaction(request.createdTransaction, signer)
        return SignedOfflineTronTransaction(
            rawHex = signed.raw.toRawHexString(),
            txHash = signed.txId.canonicalTransactionHash(),
            expiration = signed.expiration,
        )
    }

    override suspend fun getNowBlock(): NowBlock = withContext(Dispatchers.IO) {
        tronKit.getNowBlock()
    }

    override fun buildOfflineTransaction(
        amount: BigDecimal,
        to: Address,
        feeLimit: Long?,
        block: NowBlock,
        timestamp: Long,
        expiration: Long,
    ): CreatedTransaction = tronKit.buildOfflineTransaction(
        contract = transferContract(amount, to),
        block = block,
        timestamp = timestamp,
        expiration = expiration,
        feeLimit = feeLimit,
    )

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val normalizedRawHex = rawTransactionHex.trim()
        require(RawTransactionHex.isValid(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val result = tronKit.broadcastRawTransaction(
            rawTransaction = normalizedRawHex.hexToByteArray(),
            retryMetadata = (metadata as? OfflineBroadcastMetadata.Tron)?.toTronRetryMetadata(),
        )
        return BroadcastRawTransactionResult(
            txHash = result.txId.canonicalTransactionHash(),
            status = result.status.toAppStatus(),
        )
    }

    override fun isOwnAddress(address: Address): Boolean {
        return address == tronKit.address
    }

    protected fun balanceInBigDecimal(balance: BigInteger?, decimal: Int): BigDecimal {
        balance?.toBigDecimal()?.let {
            return scaleDown(it, decimal)
        } ?: return BigDecimal.ZERO
    }

    protected fun scaleDown(amount: BigDecimal, decimals: Int = decimal): BigDecimal {
        return amount.movePointLeft(decimals).stripTrailingZeros()
    }

    protected fun scaleUp(amount: BigDecimal, decimals: Int = decimal): BigInteger {
        return amount.movePointRight(decimals).toBigInteger()
    }

    protected abstract fun transferContract(amount: BigDecimal, to: Address): Contract

    companion object {
        const val confirmationsThreshold: Int = TronTransactionRecord.CONFIRMATIONS_THRESHOLD
    }

}

internal class TronSignerNotInitializedException : IllegalStateException("Tron signer is not initialized")

/** A watch account funds nothing, so foreign control is not reported for it. */
internal fun AccountStatus.toReceiveAccountStatus(hasSigner: Boolean): ReceiveAccountStatus =
    when (this) {
        AccountStatus.NotActive -> ReceiveAccountStatus.NotActive
        AccountStatus.Active -> ReceiveAccountStatus.Active
        AccountStatus.ControlledByOthers ->
            if (hasSigner) ReceiveAccountStatus.ControlledByOthers else ReceiveAccountStatus.Active
    }

private fun OfflineBroadcastMetadata.Tron.toTronRetryMetadata() = RawTransactionRetryMetadata(
    expiration = expiration,
)

private fun RawTransactionBroadcastStatus.toAppStatus(): BroadcastRawTransactionStatus =
    when (this) {
        RawTransactionBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
        RawTransactionBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
        RawTransactionBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
    }
