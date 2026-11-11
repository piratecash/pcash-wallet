package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.UserLevelLogScope
import io.horizontalsystems.core.logger.LogScope
import io.horizontalsystems.core.storage.LogEntry
import io.horizontalsystems.core.storage.LogsDao
import java.util.concurrent.atomic.AtomicInteger

/** Closed while the wallet of a level above is still stopping: its log rows would land in the safe below. */
class DesktopLogGate {
    private val releases = AtomicInteger(0)

    val isOpen: Boolean get() = releases.get() == 0

    fun releaseStarted() {
        releases.incrementAndGet()
    }

    fun releaseEnded() {
        releases.updateAndGet { maxOf(it - 1, 0) }
    }

    fun reset() = releases.set(0)
}

class GatedLogsDao(private val dao: LogsDao, private val gate: DesktopLogGate) : LogsDao by dao {
    /** Made per log call: a line queued during a release may run after it ended. */
    private val openAtCall = gate.isOpen

    override fun insert(logEntry: LogEntry) {
        if (openAtCall && gate.isOpen) dao.insert(logEntry)
    }
}

/** Every safe has its own journal, and a safe's level number depends on how it was entered. */
class DesktopLogScope(private val scope: UserLevelLogScope) : LogScope by scope {
    override val userLevel: Int = 0

    override fun stampFor(accountId: String?): Int? = userLevel.takeIf { scope.stampFor(accountId) != null }
}
