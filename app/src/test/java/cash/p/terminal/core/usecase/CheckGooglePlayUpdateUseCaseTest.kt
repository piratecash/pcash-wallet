package cash.p.terminal.core.usecase

import cash.p.terminal.modules.softwareupdate.domain.GooglePlayUpdateAvailability
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CheckGooglePlayUpdateUseCaseTest {

    private val appUpdateManager = mockk<AppUpdateManager>()
    private val useCase = CheckGooglePlayUpdateUseCase(appUpdateManager)

    @Test
    fun getAvailability_updateAvailable_returnsVersionCodeWithoutRequiringAllowedType() = runTest {
        val info = updateInfo(UpdateAvailability.UPDATE_AVAILABLE, versionCode = 257)
        every { appUpdateManager.appUpdateInfo } returns Tasks.forResult(info)

        assertEquals(GooglePlayUpdateAvailability.Available(versionCode = 257), useCase.getAvailability())
        verify(exactly = 0) { info.isUpdateTypeAllowed(any<Int>()) }
    }

    @Test
    fun getAvailability_updateInProgress_returnsVersionCode() = runTest {
        val info = updateInfo(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS, versionCode = 257)
        every { appUpdateManager.appUpdateInfo } returns Tasks.forResult(info)

        assertEquals(
            GooglePlayUpdateAvailability.DeveloperTriggeredUpdateInProgress(versionCode = 257),
            useCase.getAvailability(),
        )
    }

    @Test
    fun getAvailability_notAvailable_doesNotReadVersionCode() = runTest {
        val info = updateInfo(UpdateAvailability.UPDATE_NOT_AVAILABLE)
        every { appUpdateManager.appUpdateInfo } returns Tasks.forResult(info)

        assertEquals(GooglePlayUpdateAvailability.NotAvailable, useCase.getAvailability())
        verify(exactly = 0) { info.availableVersionCode() }
    }

    @Test
    fun getAvailability_unknown_returnsErrorWithoutReadingVersionCode() = runTest {
        val info = updateInfo(UpdateAvailability.UNKNOWN)
        every { appUpdateManager.appUpdateInfo } returns Tasks.forResult(info)

        assertEquals(GooglePlayUpdateAvailability.Error, useCase.getAvailability())
        verify(exactly = 0) { info.availableVersionCode() }
    }

    @Test
    fun getAvailability_apiFailure_returnsError() = runTest {
        every { appUpdateManager.appUpdateInfo } returns Tasks.forException(RuntimeException("Play API"))

        assertEquals(GooglePlayUpdateAvailability.Error, useCase.getAvailability())
    }

    @Test
    fun getAvailability_cancelledTask_propagatesCancellation() {
        every { appUpdateManager.appUpdateInfo } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runTest { useCase.getAvailability() }
        }
    }

    private fun updateInfo(availability: Int, versionCode: Int? = null): AppUpdateInfo =
        mockk<AppUpdateInfo>().also { info ->
            every { info.updateAvailability() } returns availability
            versionCode?.let { every { info.availableVersionCode() } returns it }
        }
}
