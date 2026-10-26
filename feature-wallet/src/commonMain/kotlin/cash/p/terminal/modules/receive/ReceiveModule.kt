package cash.p.terminal.modules.receive

import org.koin.core.scope.Scope
import cash.p.beam.BeamAddressType
import cash.p.terminal.modules.receive.viewmodels.SessionBeamReceiveAddressProvider
import cash.p.terminal.wallet.entities.UsedAddress
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.receive.viewmodels.ReceiveAddressViewModel
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

object ReceiveModule {

    enum class BeamReceiveType(val addressType: BeamAddressType) {
        Regular(BeamAddressType.Regular),
        Sbbs(BeamAddressType.Regular),
        PublicOffline(BeamAddressType.PublicOffline),
        Offline(BeamAddressType.Offline),
        MaxPrivacy(BeamAddressType.MaxPrivacy),
    }

    sealed class AdditionalData {
        class Amount(val value: String) : AdditionalData()
        class Memo(val value: String) : AdditionalData()
        object AccountNotActive : AdditionalData()
    }

    abstract class AbstractUiState {
        abstract val viewState: ViewState
        abstract val alertText: AlertText?
        abstract val uri: String
        abstract val address: String
        abstract val mainNet: Boolean
        abstract val blockchainName: String?
        abstract val addressFormat: String?
        abstract val additionalItems: List<AdditionalData>
        abstract val watchAccount: Boolean
        abstract val amount: BigDecimal?
    }

    data class UiState(
        override val viewState: ViewState,
        override val address: String,
        override val mainNet: Boolean,
        val usedAddresses: List<UsedAddress>,
        val usedChangeAddresses: List<UsedAddress>,
        val isAddressHistorySupported: Boolean,
        val showTronAlert: Boolean,
        val beamReceiveType: BeamReceiveType?,
        override val uri: String,
        override val blockchainName: String?,
        override val addressFormat: String?,
        override val watchAccount: Boolean,
        override val additionalItems: List<AdditionalData>,
        override val amount: BigDecimal?,
        override val alertText: AlertText?,
    ) : AbstractUiState()

    sealed class AlertText {
        class Normal(val content: String) : AlertText()
        class Critical(val content: String) : AlertText()
    }

}

fun Scope.receiveAddressViewModel(wallet: Wallet): ReceiveAddressViewModel {
    val beamAddressProvider = if (wallet.token.blockchainType == BlockchainType.Beam) {
        SessionBeamReceiveAddressProvider(get(), get(), get(), get())
    } else {
        null
    }
    return ReceiveAddressViewModel(wallet, get(), get(), beamAddressProvider)
}
