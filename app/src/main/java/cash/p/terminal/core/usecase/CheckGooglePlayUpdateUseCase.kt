package cash.p.terminal.core.usecase

import cash.p.terminal.modules.softwareupdate.domain.GooglePlayUpdateAvailability
import cash.p.terminal.modules.softwareupdate.domain.GooglePlayUpdateAvailabilityProvider
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class CheckGooglePlayUpdateUseCase(
    private val appUpdateManager: AppUpdateManager,
) : GooglePlayUpdateAvailabilityProvider {

    override suspend fun getAvailability(): GooglePlayUpdateAvailability = try {
        appUpdateManager.requestAppUpdateInfo().toGooglePlayUpdateAvailability()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Google Play update check failed")
        GooglePlayUpdateAvailability.Error
    }
}

internal fun AppUpdateInfo.toGooglePlayUpdateAvailability(): GooglePlayUpdateAvailability =
    when (updateAvailability()) {
        UpdateAvailability.UPDATE_AVAILABLE ->
            GooglePlayUpdateAvailability.Available(availableVersionCode())

        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
            GooglePlayUpdateAvailability.DeveloperTriggeredUpdateInProgress(availableVersionCode())

        else -> GooglePlayUpdateAvailability.NotAvailable
    }
