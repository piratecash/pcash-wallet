package cash.p.terminal.core.notifications

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.ILocalStorage
import io.horizontalsystems.core.IBackgroundManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TransactionNotificationServiceTest {

    private val transactionMonitor = mockk<TransactionMonitor>(relaxed = true)
    private val notificationManager = mockk<TransactionNotificationManager>()
    private val backgroundManager = mockk<IBackgroundManager>()
    private var appInForeground = false

    private lateinit var service: TransactionNotificationService

    @Before
    fun setUp() {
        every { backgroundManager.inForeground } answers { appInForeground }
        every { notificationManager.buildServiceNotification() } answers {
            NotificationCompat.Builder(ApplicationProvider.getApplicationContext(), "test").build()
        }
        startKoin {
            modules(
                module {
                    single { transactionMonitor }
                    single { notificationManager }
                    single<ILocalStorage> { mockk(relaxed = true) }
                    single { backgroundManager }
                }
            )
        }
        service = Robolectric.buildService(TransactionNotificationService::class.java).create().get()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun onStartCommand_stopThenStartInBackground_entersForegroundAndStartsMonitor() {
        service.onStartCommand(stopIntent(), 0, 1)
        service.onStartCommand(startIntent(), 0, 2)

        val shadow = shadowOf(service)
        assertNotNull(shadow.lastForegroundNotification)
        assertFalse(shadow.isForegroundStopped)
        verify(exactly = 1) { transactionMonitor.start(any()) }
    }

    @Test
    fun onStartCommand_startWhileAppInForeground_entersThenLeavesForegroundWithoutMonitoring() {
        appInForeground = true

        service.onStartCommand(startIntent(), 0, 1)

        assertLeftForegroundWithoutMonitoring()
    }

    @Test
    fun onStartCommand_stopThenStartWhileAppInForeground_removesForegroundNotification() {
        service.onStartCommand(stopIntent(), 0, 1)
        appInForeground = true

        service.onStartCommand(startIntent(), 0, 2)

        assertLeftForegroundWithoutMonitoring()
    }

    @Test
    fun onStartCommand_stop_stopsSelfWithItsOwnStartId() {
        service.onStartCommand(stopIntent(), 0, 7)

        assertEquals(7, shadowOf(service).stopSelfId)
    }

    @Test
    fun onPremiumExpired_fromStoppedSession_doesNotStopRestartedMonitor() {
        val expiries = mutableListOf<(() -> Unit)?>()
        every { transactionMonitor.onPremiumExpired = captureNullable(expiries) } just Runs
        service.onStartCommand(startIntent(), 0, 1)
        val firstSessionExpiry = expiries.filterNotNull().single()
        service.onStartCommand(stopIntent(), 0, 2)
        service.onStartCommand(startIntent(), 0, 3)

        firstSessionExpiry()
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { transactionMonitor.stop() }
    }

    private fun assertLeftForegroundWithoutMonitoring() {
        val shadow = shadowOf(service)
        verify(exactly = 1) { notificationManager.buildServiceNotification() }
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
        verify(exactly = 0) { transactionMonitor.start(any()) }
    }

    private fun startIntent() = Intent(service, TransactionNotificationService::class.java)

    private fun stopIntent() = startIntent().setAction(TransactionNotificationService.ACTION_STOP)
}
