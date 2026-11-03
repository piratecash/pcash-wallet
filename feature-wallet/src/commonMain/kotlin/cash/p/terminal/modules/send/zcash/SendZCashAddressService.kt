package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.entities.Address
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Error_SendToSelf
import cash.p.terminal.resources.SwapSettings_Error_InvalidAddress

class SendZCashAddressService(
    private val adapter: ISendZcashAdapter
) {
    var address: Address? = null
        private set
    private var addressType: ZcashAdapter.ZCashAddressType? = null
    private var addressError: Throwable? = null

    private val _stateFlow = MutableStateFlow(
        State(
            address = address,
            addressType = addressType,
            addressError = addressError,
            canBeSend = address != null && addressError == null
        )
    )
    val stateFlow = _stateFlow.asStateFlow()


    suspend fun setAddress(address: Address?) {
        this.address = address

        validateAddress()

        emitState()
    }

    private suspend fun validateAddress() {
        addressType = null
        addressError = null
        val address = this.address ?: return

        try {
            addressType = adapter.validate(address.hex)
        } catch (e: Exception) {
            addressError = getError(e)
        }
    }

    private fun getError(error: Throwable): Throwable {
        val message = when (error) {
            is ZcashAdapter.ZcashError.SendToSelfNotAllowed -> Translator.getString(
                Res.string.Send_Error_SendToSelf, "ZEC"
            )

            is ZcashAdapter.ZcashError.InvalidAddress -> Translator.getString(
                Res.string.SwapSettings_Error_InvalidAddress
            )

            else -> error.message ?: error.javaClass.simpleName
        }

        return Throwable(message)
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                address = address,
                addressType = addressType,
                addressError = addressError,
                canBeSend = address != null && addressError == null
            )
        }
    }

    data class State(
        val address: Address?,
        val addressType: ZcashAdapter.ZCashAddressType?,
        val addressError: Throwable?,
        val canBeSend: Boolean
    )
}
