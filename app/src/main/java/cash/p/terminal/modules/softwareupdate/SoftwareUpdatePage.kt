package cash.p.terminal.modules.softwareupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.usecase.toGooglePlayUpdateAvailability
import cash.p.terminal.modules.softwareupdate.changelog.VersionChangelogPage
import cash.p.terminal.modules.softwareupdate.domain.ChangelogRequest
import cash.p.terminal.modules.softwareupdate.domain.GooglePlayUpdateAvailability
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.network.github.domain.entity.AppRelease
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import org.koin.compose.viewmodel.koinViewModel

class SoftwareUpdatePage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: SoftwareUpdateViewModel = koinViewModel()
        val context = LocalContext.current
        val appUpdateManager = remember { getKoinInstance<AppUpdateManager>() }
        val installSourceProvider = remember { getKoinInstance<InstallSourceProvider>() }
        val updateFlowLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { }

        PageResumeEffect(
            onResume = {
                if (installSourceProvider.installSource == InstallSource.GOOGLE_PLAY) {
                    requestGooglePlayUpdate(appUpdateManager) { info, availability ->
                        if (availability is GooglePlayUpdateAvailability.DeveloperTriggeredUpdateInProgress) {
                            startImmediateUpdate(appUpdateManager, updateFlowLauncher, info)
                        }
                    }
                }
            },
            onPause = {}
        )

        SoftwareUpdateScreen(
            uiState = viewModel.uiState,
            onBack = navigation::navigateUpSafely,
            onIntervalChange = viewModel::onIntervalChange,
            onRetry = viewModel::retry,
            onHistoryClick = { navigation.slideFromRight(VersionHistoryPage()) },
            onDetailsClick = { request -> navigation.navigateToChangelog(request) },
            onUpdateNowClick = { release ->
                onUpdateNow(context, appUpdateManager, installSourceProvider, updateFlowLauncher, release)
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
    appUpdateManager: AppUpdateManager,
    installSourceProvider: InstallSourceProvider,
    updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>,
    release: AppRelease?,
) {
    if (installSourceProvider.installSource == InstallSource.GOOGLE_PLAY) {
        requestGooglePlayUpdate(appUpdateManager) { info, availability ->
            handleGooglePlayUpdate(
                context, appUpdateManager, installSourceProvider, updateFlowLauncher, info, availability
            )
        }
        return
    }
    installSourceProvider.updateDestinationUrl(release)?.let { destinationUrl ->
        openUrl(context, destinationUrl)
    }
}

private fun requestGooglePlayUpdate(
    appUpdateManager: AppUpdateManager,
    onResult: (AppUpdateInfo, GooglePlayUpdateAvailability) -> Unit,
) {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        onResult(info, info.toGooglePlayUpdateAvailability())
    }
}

private fun handleGooglePlayUpdate(
    context: Context,
    appUpdateManager: AppUpdateManager,
    installSourceProvider: InstallSourceProvider,
    updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>,
    info: AppUpdateInfo,
    availability: GooglePlayUpdateAvailability,
) {
    when (availability) {
        is GooglePlayUpdateAvailability.Available -> {
            if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ||
                !startImmediateUpdate(appUpdateManager, updateFlowLauncher, info)
            ) {
                openGooglePlayPage(context, installSourceProvider)
            }
        }

        is GooglePlayUpdateAvailability.DeveloperTriggeredUpdateInProgress ->
            startImmediateUpdate(appUpdateManager, updateFlowLauncher, info)

        GooglePlayUpdateAvailability.NotAvailable,
        GooglePlayUpdateAvailability.Error,
        -> Unit
    }
}

private fun startImmediateUpdate(
    appUpdateManager: AppUpdateManager,
    updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest>,
    info: AppUpdateInfo,
): Boolean = tryOrNull {
    appUpdateManager.startUpdateFlowForResult(
        info,
        updateFlowLauncher,
        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
    )
} == true

private fun openGooglePlayPage(context: Context, installSourceProvider: InstallSourceProvider) {
    val destinationUrl = installSourceProvider.updateDestinationUrl(release = null) ?: return
    openUrl(context, destinationUrl)
}

private fun openUrl(context: Context, url: String) {
    tryOrNull { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
