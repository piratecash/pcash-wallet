package io.horizontalsystems.core.logger

import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AppLog {
    // Resolved per call so a log made before Koin starts fails inside the executor task, not at the call site.
    private val logsDao: LogsDao get() = getKoinInstance()

    // Stored levels keep the values of android.util.Log, which earlier rows were written with.
    internal const val LEVEL_INFO = 4
    internal const val LEVEL_WARN = 5

    private const val DAYS_TO_KEEP = 90L
    private const val MAX_ENTRIES = 20_000
    private const val EXPORT_PAGE_SIZE = 500
    private val executor = Executors.newSingleThreadExecutor()

    /** Serializes the export with every insert and trim, which all run on [executor]. */
    private val logDispatcher = executor.asCoroutineDispatcher()
    private val sdf = newDateFormat()

    fun info(actionId: String, message: String) = submitInsert(LEVEL_INFO, actionId, message)

    fun warning(actionId: String, message: String, e: Throwable) =
        submitInsert(LEVEL_WARN, actionId, message, e)

    fun warning(actionId: String, message: String) = submitInsert(LEVEL_WARN, actionId, message)

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

    /** Streams the whole log (or one tag) into [out] page by page, without materialising the table. */
    suspend fun writeLog(out: Appendable, tag: String? = null) = withContext(logDispatcher) {
        val dateFormat = newDateFormat()
        var currentActionId: String? = null
        var afterFirstId = 0L
        var afterId = 0

        do {
            val page = if (tag == null) {
                logsDao.getGroupedPage(afterFirstId, afterId, EXPORT_PAGE_SIZE)
            } else {
                logsDao.getGroupedPageByTag(tag, afterFirstId, afterId, EXPORT_PAGE_SIZE)
            }

            page.forEach { row ->
                if (row.actionId != currentActionId) {
                    out.appendLine("${row.actionId}:")
                    currentActionId = row.actionId
                }
                val date = dateFormat.format(Date(row.date))
                out.appendLine("  - ${row.id}: $date ${row.message}")
            }

            page.lastOrNull()?.let { last ->
                afterFirstId = last.firstId
                afterId = last.id
            }
        } while (page.size == EXPORT_PAGE_SIZE)
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
