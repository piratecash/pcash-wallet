package cash.p.terminal.modules.signmessage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.adapters.BitcoinAdapter
import cash.p.terminal.core.derivation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.sign_message_failed
import cash.p.terminal.resources.sign_message_not_ready
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.UsedAddress
import co.touchlab.kermit.Logger
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

class SignMessageViewModel(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager,
) : ViewModel() {

    private val logger = Logger.withTag("SignMessageViewModel")
    private val formats = wallet.token.type.derivation
        ?.let { SignMessageFormats.available(wallet.account.type, it) }
        .orEmpty()

    var uiState by mutableStateOf(
        SignMessageUiState(
            address = adapter()?.receiveAddress.orEmpty(),
            formats = formats,
            selectedFormat = formats.firstOrNull(),
        )
    )
        private set

    fun onMessageChange(message: String) {
        uiState = uiState.copy(message = message)
    }

    fun onFormatSelect(format: MessageSignatureFormat) {
        uiState = uiState.copy(selectedFormat = format)
    }

    fun onAddressSelect(address: String) {
        uiState = uiState.copy(address = address)
    }

    /** Null while the adapter is not ready; the error is shown instead. */
    fun addressSelectInput(): SignAddressSelectInput? {
        val adapter = adapter() ?: return null.also { showError(Res.string.sign_message_not_ready) }
        return SignAddressSelectInput(
            currentAddress = adapter.receiveAddress,
            receiveAddresses = adapter.usedAddresses(change = false),
            changeAddresses = adapter.usedAddresses(change = true),
            selectedAddress = uiState.address,
        )
    }

    fun sign() {
        val format = uiState.selectedFormat ?: return
        if (uiState.signing) return
        val adapter = adapter() ?: return showError(Res.string.sign_message_not_ready)
        val address = uiState.address.ifEmpty { adapter.receiveAddress }
        val message = uiState.message
        uiState = uiState.copy(address = address, signing = true)
        viewModelScope.launch {
            uiState = try {
                val signature = adapter.signMessage(address, message, format)
                uiState.copy(signing = false, result = SignedMessage(signature, address, format, message))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Message signing failed" }
                uiState.copy(signing = false, error = Translator.getString(Res.string.sign_message_failed))
            }
        }
    }

    fun onResultShown() {
        uiState = uiState.copy(result = null)
    }

    fun onErrorShown() {
        uiState = uiState.copy(error = null)
    }

    private fun showError(text: StringResource) {
        uiState = uiState.copy(error = Translator.getString(text))
    }

    private fun adapter(): BitcoinAdapter? = adapterManager.getAdapterForWallet(wallet)
}

data class SignMessageUiState(
    val address: String,
    val formats: List<MessageSignatureFormat>,
    val selectedFormat: MessageSignatureFormat?,
    val message: String = "",
    val signing: Boolean = false,
    val error: String? = null,
    val result: SignedMessage? = null,
) {
    val signEnabled: Boolean
        get() = message.isNotEmpty() && selectedFormat != null && !signing
}

data class SignedMessage(
    val signature: String,
    val address: String,
    val format: MessageSignatureFormat,
    val message: String,
)

data class SignAddressSelectInput(
    val currentAddress: String,
    val receiveAddresses: List<UsedAddress>,
    val changeAddresses: List<UsedAddress>,
    val selectedAddress: String,
)
