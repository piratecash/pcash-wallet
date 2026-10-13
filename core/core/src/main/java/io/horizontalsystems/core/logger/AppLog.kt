package io.horizontalsystems.core.logger

import android.util.Log
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AppLog {
    // Resolved per call so a log made before Koin starts fails inside the executor task, not at the call site.
    private val logsDao: LogsDao get() = get(LogsDao::class.java)

    private const val DAYS_TO_KEEP = 90L
    private val executor = Executors.newSingleThreadExecutor()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun info(actionId: String, message: String) {
        executor.submit {
            logsDao.insert(LogEntry(System.currentTimeMillis(), Log.INFO, actionId, message))
        }
    }

    fun warning(actionId: String, message: String, e: Throwable) {
        executor.submit {
            logsDao.insert(
                LogEntry(
                    System.currentTimeMillis(),
                    Log.WARN,
                    actionId,
                    message + ": " + getStackTraceString(e)
                )
            )
        }
    }

    fun warning(actionId: String, message: String) {
        executor.submit {
            logsDao.insert(LogEntry(System.currentTimeMillis(), Log.WARN, actionId, message))
        }
    }

    suspend fun getLog(): Map<String, Any> = withContext(Dispatchers.IO) {
        buildLogMap(logsDao.getRecent(500).reversed())
    }

    suspend fun getFullLog(): Map<String, Any> = withContext(Dispatchers.IO) {
        buildLogMap(logsDao.getAll())
    }

    fun getLog(tag: String): Map<String, Any> = buildLogMap(logsDao.getByTag(tag))

    fun getRecentLog(tag: String, limit: Int = 300): Map<String, Any> =
        buildLogMap(logsDao.getRecentByTag(tag, limit).reversed())

    private fun buildLogMap(entries: List<LogEntry>): Map<String, Any> {
        val res = mutableMapOf<String, MutableMap<String, String>>()

        entries.forEach { logEntry ->
            res.getOrPut(logEntry.actionId) { mutableMapOf() }[logEntry.id.toString()] =
                sdf.format(Date(logEntry.date)) + " " + logEntry.message
        }

        return res
    }

    fun cleanupOldLogs() {
        executor.submit {
            val threeMonthsAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DAYS_TO_KEEP)
            logsDao.deleteOlderThan(threeMonthsAgo)
        }
    }

    private fun getStackTraceString(error: Throwable): String {
        val sb = StringBuilder()

        sb.appendLine(error)

        error.stackTrace.forEachIndexed { index, stackTraceElement ->
            if (index < 5) {
                sb.appendLine(stackTraceElement)
            }
        }

        return sb.toString()
    }
}
