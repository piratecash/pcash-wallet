package cash.p.terminal.modules.softwareupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cash.p.terminal.core.composablePage
import cash.p.terminal.core.usecase.toGooglePlayUpdateAvailability
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.releasenotes.ReleaseNotesScreen
import cash.p.terminal.modules.softwareupdate.changelog.VersionChangelogViewModel
import cash.p.terminal.modules.softwareupdate.domain.ChangelogRequest
import cash.p.terminal.modules.softwareupdate.domain.GooglePlayUpdateAvailability
import cash.p.terminal.modules.softwareupdate.domain.InstallSource
import cash.p.terminal.modules.softwareupdate.domain.InstallSourceProvider
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryScreen
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.network.github.domain.entity.AppRelease
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.ScreenWithoutConnectionPanel
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SoftwareUpdateFragment : BaseComposeFragment() {

    private val appUpdateManager: AppUpdateManager by inject()
    private val installSourceProvider: InstallSourceProvider by inject()

    private val updateFlowLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    @Composable
    override fun GetContent(navController: NavController) {
        SoftwareUpdateNavHost(navController, onUpdateNow = ::onUpdateNow)
    }

    override fun onResume() {
        super.onResume()
        if (installSourceProvider.installSource == InstallSource.GOOGLE_PLAY) {
            requestGooglePlayUpdate { info, availability ->
                if (availability is GooglePlayUpdateAvailability.DeveloperTriggeredUpdateInProgress) {
                    startImmediateUpdate(info)
                }
            }
        }
    }

    private fun onUpdateNow(release: AppRelease?) {
        if (installSourceProvider.installSource == InstallSource.GOOGLE_PLAY) {
            requestGooglePlayUpdate(::handleGooglePlayUpdate)
            return
        }
        installSourceProvider.updateDestinationUrl(release)?.let { destinationUrl ->
            openUrl(requireContext(), destinationUrl)
        }
    }

    private fun requestGooglePlayUpdate(
        onResult: (AppUpdateInfo, GooglePlayUpdateAvailability) -> Unit,
    ) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                onResult(info, info.toGooglePlayUpdateAvailability())
            }
    }

    private fun handleGooglePlayUpdate(
        info: AppUpdateInfo,
        availability: GooglePlayUpdateAvailability,
    ) {
        when (availability) {
            is GooglePlayUpdateAvailability.Available -> {
                if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) || !startImmediateUpdate(info)) {
                    openGooglePlayPage()
                }
            }

            is GooglePlayUpdateAvailability.DeveloperTriggeredUpdateInProgress ->
                startImmediateUpdate(info)

            GooglePlayUpdateAvailability.NotAvailable,
            GooglePlayUpdateAvailability.Error,
            -> Unit
        }
    }

    private fun startImmediateUpdate(info: AppUpdateInfo): Boolean {
        if (!isAdded) return false
        return tryOrNull {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateFlowLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        } == true
    }

    private fun openGooglePlayPage() {
        val context = context ?: return
        val destinationUrl = installSourceProvider.updateDestinationUrl(release = null) ?: return
        openUrl(context, destinationUrl)
    }
}

private sealed class SoftwareUpdateRoute {
    @Serializable
    data object Update : SoftwareUpdateRoute()

    @Serializable
    data object History : SoftwareUpdateRoute()

    @Serializable
    data class Changelog(
        val minor: String,
        val isActiveBranch: Boolean,
        val tagName: String?,
    ) : SoftwareUpdateRoute()
}

@Composable
private fun SoftwareUpdateNavHost(
    fragmentNavController: NavController,
    onUpdateNow: (AppRelease?) -> Unit,
) {
    val navController = rememberNavController()
    val openChangelog = { request: ChangelogRequest -> navController.navigateToChangelog(request) }

    NavHost(
        navController = navController,
        startDestination = SoftwareUpdateRoute.Update,
    ) {
        composable<SoftwareUpdateRoute.Update> {
            val viewModel: SoftwareUpdateViewModel = koinViewModel()
            SoftwareUpdateScreen(
                uiState = viewModel.uiState,
                onBack = fragmentNavController::popBackStackSafely,
                onIntervalChange = viewModel::onIntervalChange,
                onRetry = viewModel::retry,
                onHistoryClick = { navController.navigate(SoftwareUpdateRoute.History) },
                onDetailsClick = openChangelog,
                onUpdateNowClick = onUpdateNow,
            )
        }
        composablePage<SoftwareUpdateRoute.History> {
            val viewModel: VersionHistoryViewModel = koinViewModel()
            VersionHistoryScreen(
                uiState = viewModel.uiState,
                onBack = navController::popBackStackSafely,
                onRetry = viewModel::retry,
                onVersionClick = openChangelog,
            )
        }
        composablePage<SoftwareUpdateRoute.Changelog> { backStackEntry ->
            val context = LocalContext.current
            val route = backStackEntry.toRoute<SoftwareUpdateRoute.Changelog>()
            val request = if (route.isActiveBranch) {
                ChangelogRequest.active(route.minor, route.tagName)
            } else {
                ChangelogRequest.archived(route.minor)
            }
            val viewModel: VersionChangelogViewModel =
                koinViewModel(parameters = { parametersOf(request) })
            ScreenWithoutConnectionPanel {
                ReleaseNotesScreen(
                    closeablePopup = false,
                    uiState = viewModel.uiState,
                    onCloseClick = navController::popBackStackSafely,
                    onRetryClick = viewModel::retry,
                    onWhatsNewShown = {},
                    onShowChangelogToggle = viewModel::onShowChangelogToggle,
                    onUrlClick = { url -> LinkHelper.openLinkInAppBrowser(context, url) },
                )
            }
        }
    }
}

private fun NavController.navigateToChangelog(request: ChangelogRequest) {
    navigate(
        SoftwareUpdateRoute.Changelog(
            minor = request.minor,
            isActiveBranch = request.isActiveBranch,
            tagName = request.tagName,
        )
    )
}

private fun openUrl(context: Context, url: String) {
    tryOrNull { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
