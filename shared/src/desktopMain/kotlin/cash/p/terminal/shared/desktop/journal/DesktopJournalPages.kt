package cash.p.terminal.shared.desktop.journal

import androidx.compose.runtime.Composable
import androidx.paging.compose.collectAsLazyPagingItems
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.core.rememberPremiumToggle
import cash.p.terminal.feature.logging.detail.LoggingDetailScreen
import cash.p.terminal.feature.logging.detail.LoggingDetailViewModel
import cash.p.terminal.feature.logging.history.LoggingListScreen
import cash.p.terminal.feature.logging.history.LoggingListViewModel
import cash.p.terminal.feature.logging.settings.LoggingSettingsScreen
import cash.p.terminal.feature.logging.settings.LoggingSettingsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_delete_contacts_password_disable_description
import cash.p.terminal.resources.desktop_journal_password_disable_description
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class DesktopLoginLoggingPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: LoggingSettingsViewModel = koinViewModel()
        val enableJournalPassword = navigation.rememberPremiumAction {
            navigation.slideFromRight(SetJournalSecretPage(JournalSecretKind.JOURNAL_PASSWORD))
        }
        val disableJournalPassword = navigation.rememberJournalAccessAction(
            Res.string.desktop_journal_password_disable_description,
            viewModel::disablePasscodeLoggingPin,
        )
        val openDeleteContactsTerms = navigation.rememberPremiumAction {
            navigation.slideFromRight(DesktopDeleteContactsTermsPage())
        }
        val disableDeleteContacts = {
            navigation.slideFromRight(
                ConfirmJournalSecretPage(
                    JournalSecretKind.DELETE_CONTACTS,
                    Res.string.desktop_delete_contacts_password_disable_description,
                )
            )
        }

        LoggingSettingsScreen(
            uiState = viewModel.uiState,
            cameraAccess = koinInject(),
            onLogSuccessfulLoginsToggle = navigation.rememberPremiumToggle(viewModel::setLogSuccessfulLoginsEnabled),
            onSelfieOnSuccessfulLoginToggle =
                navigation.rememberPremiumToggle(viewModel::setSelfieOnSuccessfulLoginEnabled),
            onLogUnsuccessfulLoginsToggle =
                navigation.rememberPremiumToggle(viewModel::setLogUnsuccessfulLoginsEnabled),
            onSelfieOnUnsuccessfulLoginToggle =
                navigation.rememberPremiumToggle(viewModel::setSelfieOnUnsuccessfulLoginEnabled),
            onLogIntoDuressModeToggle = navigation.rememberPremiumToggle(viewModel::setLogIntoDuressModeEnabled),
            onSelfieOnDuressLoginToggle = navigation.rememberPremiumToggle(viewModel::onSelfieOnDuressLoginEnabled),
            onPasscodeToggle = { enabled -> if (enabled) enableJournalPassword() else disableJournalPassword() },
            onDeleteAllContactsPasscodeToggle = { enabled ->
                if (enabled) openDeleteContactsTerms() else disableDeleteContacts()
            },
            onSendLoginNotificationClick = null,
            onDeleteAllAuthDataOnDuressToggle =
                navigation.rememberPremiumToggle(viewModel::setDeleteAllAuthDataOnDuressEnabled),
            onAutoDeletePeriodChanged = viewModel::setAutoDeletePeriod,
            onDeleteAllLogs = viewModel::deleteAllLogs,
            onClose = navigation::navigateUpSafely,
            unsuccessfulSelfieIsShared = true,
        )
    }
}

class DesktopAuthorizationInfoPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: LoggingListViewModel = koinViewModel()

        LoggingListScreen(
            loginRecords = viewModel.loginRecordsFlow.collectAsLazyPagingItems(),
            onDeleteAllClick = viewModel::deleteAllLogs,
            onDeleteClick = viewModel::deleteLog,
            onItemClick = { recordId -> navigation.slideFromRight(DesktopAuthorizationDetailPage(recordId)) },
            onClose = navigation::navigateUpSafely,
        )
    }
}

class DesktopAuthorizationDetailPage(private val initialRecordId: Long) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: LoggingDetailViewModel = koinViewModel { parametersOf(initialRecordId) }

        LoggingDetailScreen(viewModel = viewModel, onClose = navigation::navigateUpSafely)
    }
}
