package cash.p.terminal.shared.desktop

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val AFTER_PID_PREFIX = "--after-pid="
private const val WIPE_FAILED = "--wipe-failed"

// Set by the jpackage launcher the packaged app starts from.
private const val APP_PATH_PROPERTY = "jpackage.app-path"
private const val PREVIOUS_INSTANCE_EXIT_TIMEOUT_SECONDS = 10L

private val logger = Logger.withTag("DesktopRelaunch")

/** What the previous instance passed when it started this one. */
data class DesktopLaunchArguments(val afterPid: Long?, val wipeFailed: Boolean) {

    /** The previous instance holds the single-instance lock and the kit files until it has exited. */
    fun awaitPreviousInstance() {
        val previous = afterPid?.let { ProcessHandle.of(it).orElse(null) } ?: return
        try {
            previous.onExit().get(PREVIOUS_INSTANCE_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.w(e) { "The previous instance is still running" }
        }
    }

    companion object {
        fun parse(args: Array<String>) = DesktopLaunchArguments(
            afterPid = args.firstOrNull { it.startsWith(AFTER_PID_PREFIX) }
                ?.removePrefix(AFTER_PID_PREFIX)
                ?.toLongOrNull(),
            wipeFailed = WIPE_FAILED in args,
        )
    }
}

interface DesktopAppRelauncher {
    /** Starts a new instance that waits for this one to exit; false when the user has to start it. */
    fun relaunch(wipeFailed: Boolean): Boolean
}

class ProcessAppRelauncher : DesktopAppRelauncher {
    override fun relaunch(wipeFailed: Boolean): Boolean {
        val current = ProcessHandle.current()
        val command = relaunchCommand(System.getProperty(APP_PATH_PROPERTY), current.info(), current.pid(), wipeFailed)
        if (command == null) {
            logger.w { "No command to start the app again" }
            return false
        }
        return try {
            ProcessBuilder(command).inheritIO().start()
            true
        } catch (e: IOException) {
            logger.w(e) { "Starting the app again failed" }
            false
        }
    }
}

internal fun relaunchCommand(
    appPath: String?,
    processInfo: ProcessHandle.Info,
    pid: Long,
    wipeFailed: Boolean,
): List<String>? {
    val launcher = appPath?.let(::listOf) ?: currentCommandLine(processInfo) ?: return null
    return launcher + "$AFTER_PID_PREFIX$pid" + listOfNotNull(WIPE_FAILED.takeIf { wipeFailed })
}

/** Without the arguments of an earlier restart, so they do not pile up. */
private fun currentCommandLine(processInfo: ProcessHandle.Info): List<String>? {
    val command = processInfo.command().orElse(null) ?: return null
    // A bare JVM command starts nothing, and some systems do not report the arguments.
    val arguments = processInfo.arguments().orElse(null) ?: return null
    return listOf(command) + arguments.filterNot { it == WIPE_FAILED || it.startsWith(AFTER_PID_PREFIX) }
}

/** The one way out of the app: the window closes the vault and exits, as when the user closes it. */
class DesktopAppExit {
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun request() {
        _requested.value = true
    }
}
