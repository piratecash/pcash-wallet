package cash.p.terminal.modules.send

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.send.bitcoin.SendBitcoinConfirmationScreen
import cash.p.terminal.modules.send.bitcoin.SendBitcoinViewModel
import cash.p.terminal.modules.send.evm.SendEvmConfirmationScreen
import cash.p.terminal.modules.send.evm.SendEvmViewModel
import cash.p.terminal.modules.send.monero.SendMoneroConfirmationScreen
import cash.p.terminal.modules.send.monero.SendMoneroViewModel
import cash.p.terminal.modules.send.solana.SendSolanaConfirmationScreen
import cash.p.terminal.modules.send.solana.SendSolanaViewModel
import cash.p.terminal.modules.send.stellar.SendStellarConfirmationScreen
import cash.p.terminal.modules.send.stellar.SendStellarViewModel
import cash.p.terminal.modules.send.ton.SendTonConfirmationScreen
import cash.p.terminal.modules.send.ton.SendTonViewModel
import cash.p.terminal.modules.send.tron.SendTronConfirmationScreen
import cash.p.terminal.modules.send.tron.SendTronViewModel
import cash.p.terminal.modules.send.zcash.SendZCashConfirmationScreen
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import kotlinx.parcelize.Parcelize
import kotlin.reflect.KClass

class SendConfirmationPage(
    val type: Type,
    val sendEntryPoint: KClass<out HSPage>? = null
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        when (type) {
            Type.Bitcoin -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendBitcoinViewModel::class)
            ) {
                SendBitcoinConfirmationScreen(navigation, it, sendEntryPoint)
            }

            Type.ZCash -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendZCashViewModel::class)
            ) {
                SendZCashConfirmationScreen(navigation, it, sendEntryPoint)
            }

            Type.Evm -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendEvmViewModel::class)
            ) {
                SendEvmConfirmationScreen(navigation, it, sendEntryPoint)
            }

            Type.Tron -> {
                val sendTronViewModel =
                    navigation.rememberExistingViewModel(SendPage::class, SendTronViewModel::class)
                val amountInputModeViewModel =
                    navigation.rememberExistingViewModel(SendPage::class, AmountInputModeViewModel::class)
                val viewModels = if (sendTronViewModel != null && amountInputModeViewModel != null) {
                    sendTronViewModel to amountInputModeViewModel
                } else {
                    null
                }
                ConfirmationOrRecover(navigation, viewModels) { (tron, amountMode) ->
                    SendTronConfirmationScreen(navigation, tron, amountMode, sendEntryPoint)
                }
            }

            Type.Solana -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendSolanaViewModel::class)
            ) {
                SendSolanaConfirmationScreen(navigation, it, sendEntryPoint)
            }

            Type.Ton -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendTonViewModel::class)
            ) {
                SendTonConfirmationScreen(navigation, it, sendEntryPoint)
            }

            Type.Monero -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendMoneroViewModel::class)
            ) {
                SendMoneroConfirmationScreen(
                    navigation = navigation,
                    sendViewModel = it,
                    sendEntryPoint = sendEntryPoint
                )
            }

            Type.Stellar -> ConfirmationOrRecover(
                navigation,
                navigation.rememberExistingViewModel(SendPage::class, SendStellarViewModel::class)
            ) {
                SendStellarConfirmationScreen(navigation, it, sendEntryPoint)
            }
        }
    }

    // Renders the confirmation screen when the flow ViewModel is still alive, or triggers
    // recovery when it isn't (e.g. process death left an empty ViewModelStore).
    @Composable
    private fun <T : Any> ConfirmationOrRecover(
        navigation: HSNavigation,
        viewModel: T?,
        content: @Composable (T) -> Unit
    ) {
        if (viewModel != null) {
            content(viewModel)
        } else {
            RecoverToSendInputEffect(navigation)
        }
    }

    // Recovery-only: the confirmation screen has no valid state after process death,
    // so send the user back to the send input step (SendPage),
    // dropping any security-check step in between.
    @Composable
    private fun RecoverToSendInputEffect(navigation: HSNavigation) {
        LaunchedEffect(Unit) {
            if (!navigation.removeLastUntil(SendPage::class, false)) {
                navigation.navigateUp()
            }
        }
    }

    @Parcelize
    enum class Type : Parcelable {
        Bitcoin, ZCash, Evm, Solana, Tron, Ton, Monero, Stellar
    }
}

// Resolved once: the page is still drawn during its exit animation after the owner page is popped.
@Composable
internal fun <T : ViewModel> HSNavigation.rememberExistingViewModel(
    ownerPage: KClass<out HSPage>,
    modelClass: KClass<out T>
): T? {
    val owner = viewModelStoreOwnerForPage(ownerPage)
    return remember { owner.existingViewModelOrNull(modelClass) }
}

private fun <T : ViewModel> ViewModelStoreOwner.existingViewModelOrNull(modelClass: KClass<out T>): T? =
    try {
        ViewModelProvider(this)[modelClass.java]
    } catch (_: RuntimeException) {
        // Default factory can't instantiate a parametrized ViewModel: the store is
        // empty (e.g. process death restored this screen), so the VM was never created.
        null
    }
