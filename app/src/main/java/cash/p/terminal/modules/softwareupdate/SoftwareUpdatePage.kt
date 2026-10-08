package cash.p.terminal.modules.softwareupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.softwareupdate.changelog.VersionChangelogPage
import cash.p.terminal.modules.softwareupdate.domain.ChangelogRequest
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.network.github.domain.entity.AppRelease
import org.koin.compose.viewmodel.koinViewModel

class SoftwareUpdatePage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: SoftwareUpdateViewModel = koinViewModel()
        val context = LocalContext.current
        val installSourceProvider = remember { getKoinInstance<InstallSourceProvider>() }

        SoftwareUpdateScreen(
            uiState = viewModel.uiState,
            onBack = navigation::navigateUpSafely,
            onIntervalChange = viewModel::onIntervalChange,
            onRetry = viewModel::retry,
            onHistoryClick = { navigation.slideFromRight(VersionHistoryPage()) },
            onDetailsClick = { request -> navigation.navigateToChangelog(request) },
            onUpdateNowClick = { release ->
                onUpdateNow(context, installSourceProvider, release)
            },
        )
    }
}

internal fun HSNavigation.navigateToChangelog(request: ChangelogRequest) {
    slideFromRight(
        VersionChangelogPage(
            minor = request.minor,
            isActiveBranch = request.isActiveBranch,
            tagName = request.tagName,
        )
    )
}

private fun onUpdateNow(
    context: Context,
    installSourceProvider: InstallSourceProvider,
    release: AppRelease?,
) {
    installSourceProvider.updateDestinationUrl(release)?.let { destinationUrl ->
        openUrl(context, destinationUrl)
    }
}

private fun openUrl(context: Context, url: String) {
    tryOrNull { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
