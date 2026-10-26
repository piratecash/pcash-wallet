package cash.p.terminal.shared.desktop

import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DesktopRelaunchTest {

    @Test
    fun relaunchCommand_packagedApp_startsTheLauncherAfterThisProcess() {
        val command = relaunchCommand(APP_PATH, processInfo(JAVA, listOf("-jar", "app.jar")), PID, wipeFailed = false)

        assertEquals(listOf(APP_PATH, "--after-pid=$PID"), command)
    }

    @Test
    fun relaunchCommand_noLauncher_repeatsTheCommandLineWithoutEarlierRestartArguments() {
        val info = processInfo(JAVA, listOf("-jar", "app.jar", "--after-pid=7", "--wipe-failed"))

        val command = relaunchCommand(appPath = null, processInfo = info, pid = PID, wipeFailed = false)

        assertEquals(listOf(JAVA, "-jar", "app.jar", "--after-pid=$PID"), command)
    }

    @Test
    fun relaunchCommand_wipeFailed_tellsTheNewProcess() {
        val command = relaunchCommand(APP_PATH, processInfo(JAVA, emptyList()), PID, wipeFailed = true)

        assertEquals(listOf(APP_PATH, "--after-pid=$PID", "--wipe-failed"), command)
    }

    @Test
    fun relaunchCommand_noCommandOrNoArguments_hasNothingToStart() {
        assertNull(relaunchCommand(appPath = null, processInfo(null, listOf("-jar")), PID, wipeFailed = false))
        assertNull(relaunchCommand(appPath = null, processInfo(JAVA, null), PID, wipeFailed = false))
    }

    @Test
    fun parse_restartArguments_readsThePidAndTheWipeOutcome() {
        assertEquals(
            DesktopLaunchArguments(afterPid = PID, wipeFailed = true),
            DesktopLaunchArguments.parse(arrayOf("--after-pid=$PID", "--wipe-failed")),
        )
        assertEquals(DesktopLaunchArguments(afterPid = null, wipeFailed = false), DesktopLaunchArguments.parse(emptyArray()))
    }

    @Test
    fun awaitPreviousInstance_runningProcess_returnsOnceItHasExited() {
        val previous = ProcessBuilder("sleep", "1").start()

        DesktopLaunchArguments.parse(arrayOf("--after-pid=${previous.pid()}")).awaitPreviousInstance()

        assertFalse(previous.isAlive)
    }

    private fun processInfo(command: String?, arguments: List<String>?): ProcessHandle.Info = mockk {
        every { command() } returns Optional.ofNullable(command)
        every { arguments() } returns Optional.ofNullable(arguments?.toTypedArray())
    }

    private companion object {
        const val APP_PATH = "/Applications/P.CASH.app/Contents/MacOS/P.CASH"
        const val JAVA = "/usr/bin/java"
        const val PID = 4242L
    }
}
