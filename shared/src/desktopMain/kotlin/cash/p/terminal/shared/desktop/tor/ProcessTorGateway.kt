package cash.p.terminal.shared.desktop.tor

import cash.p.terminal.core.tor.TorControlClient
import cash.p.terminal.core.tor.TorGateway
import cash.p.terminal.core.tor.TorGateway.Event
import cash.p.terminal.shared.desktop.qrscanner.DesktopOs
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.freehaven.tor.control.TorControlConnection
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Runs the bundled tor executable as a child process; its control port is guarded by a cookie. */
class ProcessTorGateway(
    private val executable: File?,
    private val dataDirectory: File,
    private val os: DesktopOs,
) : TorGateway {

    private val logger = Logger.withTag("ProcessTorGateway")

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<Event> = _events.asSharedFlow()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var control: Control? = null

    // exitProcess skips the window's close path, so the hook is what stops Tor before a relaunched app starts its own
    private val shutdownHook by lazy {
        Thread(::unbind, "tor-shutdown").also(Runtime.getRuntime()::addShutdownHook)
    }

    private val controlPortFile get() = File(dataDirectory, CONTROL_PORT_FILE)
    private val cookieFile get() = File(dataDirectory, COOKIE_FILE)

    override val isTorRunning: Boolean
        get() = process?.isAlive == true

    override fun bind(): Boolean {
        val tor = executable?.takeIf(File::canExecute)
        if (tor == null) {
            logger.w { "Tor executable is missing: $executable" }
            return false
        }
        shutdownHook
        val started = try {
            prepareDataDirectory()
            startProcess(tor)
        } catch (e: IOException) {
            logger.w(e) { "Tor did not start" }
            return false
        }
        process = started
        // An exit nobody asked for; unbind clears the field first
        started.onExit().thenAccept { if (process === it) _events.tryEmit(Event.TorExited) }
        return started.isAlive
    }

    override fun unbind() {
        closeControl()
        val running = process ?: return
        process = null
        running.destroy()
        if (!running.waitFor(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) running.destroyForcibly()
    }

    override fun controlClient(): TorControlClient? = control?.client

    override fun openControlClient(): TorControlClient? {
        if (!isTorRunning) return null
        val port = controlPortFile.takeIf(File::isFile)?.readText()?.let(::parseControlPort) ?: return null
        val cookie = cookieFile.takeIf(File::isFile)?.readBytes() ?: return null
        closeControl()
        // Never through the SOCKS proxy the app traffic uses
        val socket = Socket(Proxy.NO_PROXY)
        return try {
            socket.connect(InetSocketAddress(TorControlClient.SOCKS_HOST, port), CONNECT_TIMEOUT_MILLIS)
            val connection = TorControlConnection(socket.getInputStream(), socket.getOutputStream())
            connection.launchThread(true)
            Control(socket, TorControlClient(connection, cookie)).also { control = it }.client
        } catch (e: IOException) {
            socket.close()
            null
        }
    }

    override fun deleteStaleFiles(torEnabled: Boolean) {
        if (!torEnabled) dataDirectory.deleteRecursively()
    }

    private fun prepareDataDirectory() {
        if (!dataDirectory.isDirectory && !dataDirectory.mkdirs()) throw IOException("Cannot create $dataDirectory")
        controlPortFile.delete()
        File(dataDirectory, TORRC_FILE).writeText(torrc(dataDirectory, ProcessHandle.current().pid()))
    }

    private fun startProcess(tor: File): Process {
        val builder = ProcessBuilder(tor.path, "-f", File(dataDirectory, TORRC_FILE).path).redirectErrorStream(true)
        // The Linux bundle ships its own libssl and libevent next to tor without an rpath
        if (os == DesktopOs.Linux) builder.environment()["LD_LIBRARY_PATH"] = tor.parent
        return builder.start().also(::drainOutput)
    }

    private fun drainOutput(process: Process) {
        Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { logger.d { it } }
            } catch (e: IOException) {
                // The stream closes with the process
            }
        }, "tor-output").apply { isDaemon = true }.start()
    }

    private fun closeControl() {
        control?.socket?.close()
        control = null
    }

    private class Control(val socket: Socket, val client: TorControlClient)

    private companion object {
        const val EVENT_BUFFER = 8
        const val STOP_WAIT_SECONDS = 5L
        const val CONNECT_TIMEOUT_MILLIS = 5_000
    }
}

private const val TORRC_FILE = "torrc"
private const val CONTROL_PORT_FILE = "control-port"
private const val COOKIE_FILE = "control_auth_cookie"
private val CONTROL_PORT = Regex("""PORT=127\.0\.0\.1:(\d+)""")

internal fun parseControlPort(text: String): Int? = CONTROL_PORT.find(text)?.groupValues?.get(1)?.toIntOrNull()

/** Tor exits on its own within seconds once [ownerPid] is gone, so a crashed app leaves no Tor behind. */
internal fun torrc(dataDirectory: File, ownerPid: Long): String {
    val dataPath = dataDirectory.absolutePath
    return (
        TorGateway.BASE_TORRC_OPTIONS + listOf(
            "DataDirectory ${quoted(dataPath)}",
            "ControlPort auto",
            "ControlPortWriteToFile ${quoted(File(dataPath, CONTROL_PORT_FILE).path)}",
            "CookieAuthentication 1",
            "CookieAuthFile ${quoted(File(dataPath, COOKIE_FILE).path)}",
            "ClientOnly 1",
            "__OwningControllerProcess $ownerPid",
        )
        ).joinToString("\n")
}

// Paths contain spaces (Application Support); torrc reads quoted values with C escapes
private fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
