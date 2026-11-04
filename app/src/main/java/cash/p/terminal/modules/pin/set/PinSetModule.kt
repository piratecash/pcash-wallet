package cash.p.terminal.modules.pin.set

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.IPinComponent
import org.koin.core.parameter.parametersOf

object PinSetModule {

    class Factory(private val pinType: PinType) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val pinComponent = getKoinInstance<IPinComponent>()
            return PinSetViewModel(
                pinComponent = pinComponent,
                pinType = pinType,
                transactionHiddenManager = getKoinInstance<TransactionHiddenManager>(),
                hiddenWalletPinPolicy = getKoinInstance<HiddenWalletPinPolicy> {
                    parametersOf(pinComponent)
                },
                userManager = getKoinInstance<UserManager>()
            ) as T
        }
    }

    enum class SetStage {
        Enter,
        Confirm
    }

    data class PinSetViewState(
        val stage: SetStage,
        val enteredCount: Int,
        val finished: Boolean,
        val reverseSlideAnimation: Boolean,
        val error: String?,
    )

}
