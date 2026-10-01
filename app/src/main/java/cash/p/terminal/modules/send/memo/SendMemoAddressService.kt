package cash.p.terminal.modules.send.memo

import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.entities.Address
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SendMemoAddressService(
    private val adapter: ISendMemoAdapter,
    private val chain: SendMemoChain,
) {
    private var address: Address? = null
    private var addressError: Throwable? = null

    private val _stateFlow = MutableStateFlow(
        State(
            address = address,
            addressError = addressError,
            canBeSend = addressError == null,
        )
    )
    val stateFlow = _stateFlow.asStateFlow()

    fun setAddress(address: Address?) {
        this.address = address

        validateAddress()

        emitState()
    }

    private fun validateAddress() {
        addressError = null
        val address = this.address ?: return

        try {
            chain.validateAddress(address.hex, adapter)
        } catch (e: Exception) {
            addressError =
                Throwable(Translator.getString(R.string.SwapSettings_Error_InvalidAddress))
        }
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                address = address,
                addressError = addressError,
                canBeSend = addressError == null
            )
        }
    }

    data class State(
        val address: Address?,
        val addressError: Throwable?,
        val canBeSend: Boolean
    ) {
        val validAddress: Address?
            get() = if (addressError == null) address else null
    }
}
