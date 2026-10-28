package cash.p.terminal.modules.multiswap.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.modules.multiswap.SwapConfirmViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import kotlin.reflect.KClass

@Composable
fun SwapTransactionSettingsScreen(navigation: HSNavigation, confirmPage: KClass<out HSPage>) {
    if (navigation.backStack.none { it::class == confirmPage }) return
    val viewModel = viewModel<SwapConfirmViewModel>(
        viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(confirmPage)
    )

    val sendTransactionService = viewModel.sendTransactionService

    sendTransactionService.GetSettingsContent(navigation)
}
