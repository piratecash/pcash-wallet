package cash.p.terminal.core.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.notifications.polling.TransactionPollingWorker
import io.horizontalsystems.core.IBackgroundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.koin.android.ext.android.inject
import timber.log.Timber

class TransactionNotificationService : Service() {

    private val notificationManager: TransactionNotificationManager by inject()
    private val transactionMonitor: TransactionMonitor by inject()
    private val localStorage: ILocalStorage by inject()
    private val backgroundManager: IBackgroundManager by inject()

    private var serviceScope = newServiceScope()
    private var foregroundStarted = false
    private var stopped = false
    private var lastStartId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        // Every other start came from startForegroundService(): reach startForeground() before any early return,
        // even for a start that is no longer wanted, or the system kills the process.
        try {
            startForeground(
                TransactionNotificationManager.SERVICE_NOTIFICATION_ID,
                notificationManager.buildServiceNotification(),
            )
            foregroundStarted = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to enter foreground for transaction notifications")
            // startForeground failed before monitor.start, so resetting the polling baseline is safe.
            activateFallback(resetBaseline = true)
            stopMonitoring()
            return START_NOT_STICKY
        }

        // The app came back before this start was delivered.
        if (backgroundManager.inForeground) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        if (stopped) {
            stopped = false
            serviceScope = newServiceScope()
        }
        // Expiry fires on the monitor's IO coroutine: hop to main, and ignore it if its session already ended.
        val session = serviceScope
        transactionMonitor.onPremiumExpired = {
            ContextCompat.getMainExecutor(this).execute { if (session.isActive) stopMonitoring() }
        }
        transactionMonitor.start(serviceScope)

        return START_STICKY
    }

    // Android 15+ enforces a 6h/24h cumulative cap on dataSync foreground services
    // and invokes onTimeout shortly before throwing ForegroundServiceDidNotStopInTimeException.
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (stopped) return
        Timber.w("Foreground service timeout (type=%d), switching to polling fallback", fgsType)
        if (!activateFallback(resetBaseline = false)) {
            Timber.e("Polling fallback did not start after foreground service timeout")
        }
        stopMonitoring()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopMonitoring() {
        if (!stopped) {
            stopped = true
            transactionMonitor.onPremiumExpired = null
            transactionMonitor.stop()
            serviceScope.cancel()
        }
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        // With the id, a newer start that is still pending keeps the service alive.
        stopSelf(lastStartId)
    }

    private fun newServiceScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun activateFallback(resetBaseline: Boolean): Boolean {
        val started = TransactionPollingWorker.startFallback(applicationContext, localStorage)
        if (started && resetBaseline) {
            transactionMonitor.resetPollingBaseline()
        }
        return started
    }

    companion object {
        internal const val ACTION_STOP = "cash.p.terminal.STOP_MONITORING"

        fun start(context: Context): Boolean {
            try {
                val intent = Intent(context, TransactionNotificationService::class.java)
                context.startForegroundService(intent)
                return true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start TransactionNotificationService")
                return false
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, TransactionNotificationService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop TransactionNotificationService")
            }
        }
    }
}
