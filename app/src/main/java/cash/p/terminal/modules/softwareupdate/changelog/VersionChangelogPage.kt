package cash.p.terminal.modules.softwareupdate.changelog

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.modules.releasenotes.ReleaseNotesScreen
import cash.p.terminal.modules.softwareupdate.domain.ChangelogRequest
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui.helpers.LinkHelper
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class VersionChangelogPage(
    val minor: String,
    val isActiveBranch: Boolean,
    val tagName: String?,
) : HSPage(showConnectionPanel = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val context = LocalContext.current
        val request = if (isActiveBranch) {
            ChangelogRequest.active(minor, tagName)
        } else {
            ChangelogRequest.archived(minor)
        }
        val viewModel: VersionChangelogViewModel = koinViewModel(parameters = { parametersOf(request) })

        ReleaseNotesScreen(
            closeablePopup = false,
            uiState = viewModel.uiState,
            onCloseClick = navigation::navigateUpSafely,
            onRetryClick = viewModel::retry,
            onWhatsNewShown = {},
            onShowChangelogToggle = viewModel::onShowChangelogToggle,
            onUrlClick = { url -> LinkHelper.openLinkInAppBrowser(context, url) },
        )
    }
}
