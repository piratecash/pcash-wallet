package cash.p.terminal.modules.send.memo

import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.ServiceState
import cash.p.terminal.entities.Address
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

class SendMemoMinimumAmountService(
    private val adapter: ISendMemoAdapter
) : ServiceState<SendMemoMinimumAmountService.State>() {

    private var minimumAmount: BigDecimal? = null
    private var error: Throwable? = null
    private var lookupPending = false

    override fun createState() = State(
        minimumAmount = minimumAmount,
        error = error,
        canBeSend = error == null && !lookupPending
    )

    suspend fun setValidAddress(address: Address?) {
        error = null
        lookupPending = address != null
        if (lookupPending) emitState()

        try {
            minimumAmount = address?.let {
                adapter.getMinimumSendAmount(it.hex)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            minimumAmount = null
            error = e
        }

        lookupPending = false
        emitState()
    }

    data class State(
        val minimumAmount: BigDecimal?,
        val error: Throwable?,
        val canBeSend: Boolean,
    )

}
