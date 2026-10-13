package io.horizontalsystems.core.logger

import android.util.Log
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
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
    private const val MAX_ENTRIES = 20_000
    private val executor = Executors.newSingleThreadExecutor()

    /** Serializes the export with every insert and trim, which all run on [executor]. */
    private val logDispatcher = executor.asCoroutineDispatcher()
    private val sdf = newDateFormat()

    fun info(actionId: String, message: String) = submitInsert(Log.INFO, actionId, message)

    fun warning(actionId: String, message: String, e: Throwable) =
        submitInsert(Log.WARN, actionId, message, e)

    fun warning(actionId: String, message: String) = submitInsert(Log.WARN, actionId, message)

    private fun submitInsert(
        level: Int,
        actionId: String,
        message: String,
        error: Throwable? = null,
    ) {
        executor.submit {
            val text = if (error == null) message else message + ": " + getStackTraceString(error)
            logsDao.insert(LogEntry(System.currentTimeMillis(), level, actionId, text))
            logsDao.trimToNewest(MAX_ENTRIES)
        }
    }

    /** Streams the whole log (or one tag) into [out] row by row, without materialising the table. */
    suspend fun writeLog(out: Appendable, tag: String? = null) = withContext(logDispatcher) {
        val dateFormat = newDateFormat()
        val cursor = if (tag == null) logsDao.streamGrouped() else logsDao.streamGroupedByTag(tag)
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val dateIndex = it.getColumnIndexOrThrow("date")
            val actionIdIndex = it.getColumnIndexOrThrow("actionId")
            val messageIndex = it.getColumnIndexOrThrow("message")
            var currentActionId: String? = null

            while (it.moveToNext()) {
                val actionId = it.getString(actionIdIndex)
                if (actionId != currentActionId) {
                    out.appendLine("$actionId:")
                    currentActionId = actionId
                }
                val date = dateFormat.format(Date(it.getLong(dateIndex)))
                out.appendLine("  - ${it.getInt(idIndex)}: $date ${it.getString(messageIndex)}")
            }
        }
    }

    suspend fun getLog(): Map<String, Any> = withContext(Dispatchers.IO) {
        buildLogMap(logsDao.getRecent(500).reversed())
    }

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
            logsDao.trimToNewest(MAX_ENTRIES)
        }
    }

    /** A shared instance would be read from both the export thread and the screen threads. */
    private fun newDateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
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
