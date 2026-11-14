package cash.p.terminal.modules.send.tron

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ServiceState
import cash.p.terminal.wallet.Token
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.transaction.Fee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import io.horizontalsystems.tronkit.models.Address as TronAddress

class SendTronFeeService(private val adapter: ISendTronAdapter, private val feeToken: Token) :
    ServiceState<SendTronFeeService.State>() {

    private var amount: BigDecimal? = null
    private var tronAddress: TronAddress? = null
    private var contract: Contract? = null

    private var feeLimit: Long? = null
    private var fee: BigDecimal? = null
    private var breakdown: TronFeeBreakdown? = null
    private var error: Throwable? = null
    private var estimateGeneration = 0

    suspend fun setAmount(amount: BigDecimal?) {
        this.amount = amount

        resetFees()
        refreshFees()
        emitState()
    }

    suspend fun setContract(contract: Contract) {
        this.contract = contract

        invalidateFees()
        refreshFees()
        emitState()
    }

    suspend fun setCreatedTransaction(createdTransaction: CreatedTransaction) {
        invalidateFees()
        // Keep the original fee_limit for sending — estimation only affects displayed fee
        this.feeLimit = createdTransaction.raw_data.fee_limit
        refreshFees(createdTransaction)
        // Fall back to fee_limit if estimation failed, so fee balance check still works
        if (fee == null && feeLimit != null) {
            fee = feeLimit?.toBigDecimal()?.movePointLeft(feeToken.decimals)
        }
        emitState()
    }

    suspend fun setTronAddress(address: Address?) {
        this.tronAddress = address

        invalidateFees()
        refreshFees()
        emitState()
    }

    private suspend fun refreshFees(createdTransaction: CreatedTransaction? = null) {
        val generation = estimateGeneration
        try {
            val fees = estimate(createdTransaction) ?: return
            // Inputs changed while estimating: a newer estimate owns the state.
            if (generation != estimateGeneration) return
            val breakdown = fees.toTronFeeBreakdown(feeToken.decimals)
            if (createdTransaction == null) {
                feeLimit = breakdown.feeLimit
            }
            this.breakdown = breakdown
            fee = breakdown.totalFee
        } catch (error: Throwable) {
            if (generation == estimateGeneration) this.error = error
        }
    }

    private suspend fun estimate(createdTransaction: CreatedTransaction?): List<Fee>? {
        val currentContract = contract
        val amount = amount
        val tronAddress = tronAddress
        return withContext(Dispatchers.Default) {
            when {
                createdTransaction != null -> adapter.estimateFee(createdTransaction)
                currentContract != null -> adapter.estimateFee(currentContract)
                amount != null && tronAddress != null -> adapter.estimateFee(amount, tronAddress)
                else -> null
            }
        }
    }

    // A new recipient changes the fee; never let the previous one stay published while estimating.
    private fun invalidateFees() {
        resetFees()
        emitState()
    }

    private fun resetFees() {
        estimateGeneration++
        feeLimit = null
        fee = null
        breakdown = null
        error = null
    }

    override fun createState() = State(
        feeLimit = feeLimit,
        fee = fee,
        breakdown = breakdown,
        error = error,
        canBeSend = feeLimit != null && error == null
    )

    data class State(
        val feeLimit: Long?,
        val fee: BigDecimal?,
        val breakdown: TronFeeBreakdown?,
        val error: Throwable?,
        val canBeSend: Boolean
    ) {
        val estimating: Boolean
            get() = fee == null && error == null
    }
}
