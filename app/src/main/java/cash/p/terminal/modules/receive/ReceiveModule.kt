package cash.p.terminal.modules.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.beam.BeamAddressType
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.modules.receive.viewmodels.SessionBeamReceiveAddressProvider
import cash.p.terminal.wallet.entities.UsedAddress
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.receive.viewmodels.ReceiveAddressViewModel
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

object ReceiveModule {

    class Factory(private val wallet: Wallet) : ViewModelProvider.Factory {
        private val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)
        private val beamSessionOwner: BeamSessionOwner by inject(BeamSessionOwner::class.java)
        private val accountManager: IAccountManager by inject(IAccountManager::class.java)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val beamAddressProvider = if (wallet.token.blockchainType == BlockchainType.Beam) {
                SessionBeamReceiveAddressProvider(
                    beamSessionOwner, accountManager, App.adapterManager, dispatcherProvider
                )
            } else {
                null
            }
            return ReceiveAddressViewModel(
                wallet,
                App.adapterManager,
                dispatcherProvider,
                beamAddressProvider,
            ) as T
        }
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
        val beamAddressType: BeamAddressType?,
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
