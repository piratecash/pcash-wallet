package cash.p.terminal.core

class CrashlyticsNonFatalReporter : NonFatalReporter {
    override fun report(error: Throwable, message: String) = logError(error, message)
}
