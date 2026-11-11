package cash.p.terminal.core.managers

import java.util.concurrent.atomic.AtomicLong

/** Numbers native wallet sessions, so work and callbacks of a closed session cannot touch the current one. */
internal class MoneroSpentStatusReconciler {
    private val nextSession = AtomicLong()

    @Volatile
    private var session: Long? = null

    fun activate(): Long = nextSession.incrementAndGet().also { session = it }

    fun deactivate() {
        session = null
    }

    fun activeSession(): Long? = session

    fun isActive(session: Long): Boolean = this.session == session
}
