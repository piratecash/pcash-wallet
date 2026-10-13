package cash.p.terminal.core

interface NonFatalReporter {
    fun report(error: Throwable, message: String)
}

class CrashlyticsNonFatalReporter : NonFatalReporter {
    override fun report(error: Throwable, message: String) = logError(error, message)
}
