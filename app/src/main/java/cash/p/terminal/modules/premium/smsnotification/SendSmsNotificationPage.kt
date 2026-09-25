package cash.p.terminal.modules.premium.smsnotification

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class SendSmsNotificationPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val smsViewModel: SendSmsNotificationViewModel = koinViewModel()
        val appPages: AppPages = koinInject()

        SendSmsNotificationScreen(
            navigation = navigation,
            appPages = appPages,
            uiState = smsViewModel.uiState,
            onAccountSelected = smsViewModel::onAccountSelected,
            onAddressChanged = smsViewModel::onAddressChanged,
            onMemoChanged = smsViewModel::onMemoChanged,
            onSaveClick = smsViewModel::onSaveClick,
            onTestSmsClick = smsViewModel::onTestSmsClick,
            onSaveSuccessShown = smsViewModel::onSaveSuccessShown,
            onTestResultShown = smsViewModel::onTestResultShown,
            onCancelTest = smsViewModel::cancelTestTransaction,
            onClose = { navigation.navigateUpSafely() }
        )
    }
}
