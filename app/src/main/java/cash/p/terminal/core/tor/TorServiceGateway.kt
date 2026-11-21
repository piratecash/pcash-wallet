package cash.p.terminal.core.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.IBinder
import androidx.core.content.ContextCompat
import cash.p.terminal.core.tor.TorGateway.Event
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService
import java.io.File
import java.io.IOException

/** Binds tor-android's in-process [TorService] and reports when its Tor is gone. */
class TorServiceGateway(private val context: Context) : TorGateway {

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<Event> = _events.asSharedFlow()

    @Volatile
    private var bound = false

    // TorService keeps its control connection after Tor exits, so it cannot tell whether Tor runs
    @Volatile
    private var torExited = false

    private var receiverRegistered = false

    @Volatile
    private var control: Control? = null

    override val isTorRunning: Boolean
        get() = bound && !torExited

    // Holds the TorService reference itself: tor-android ships Java 24 bytecode that JVM unit tests
    // cannot load, so the gateway's own fields and signatures must not name TorService
    private val serviceConnection = object : ServiceConnection {
        @Volatile
        var service: TorService? = null

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as TorService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            torExited = true
            _events.tryEmit(Event.ServiceLost)
        }
    }

    // Tor exiting on its own is only reported this way
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(TorService.EXTRA_STATUS) == TorService.STATUS_STOPPING) {
                torExited = true
                _events.tryEmit(Event.TorExited)
            }
        }
    }

    override fun bind(): Boolean {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                statusReceiver,
                IntentFilter(TorService.ACTION_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        writeTorrc()
        torExited = false
        val intent = Intent(context, TorService::class.java)
        bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        return bound
    }

    // Unbinding destroys the service, so the next bind starts a fresh Tor
    override fun unbind() {
        closeControl()
        if (bound) {
            context.unbindService(serviceConnection)
            bound = false
        }
        serviceConnection.service = null
    }

    override fun controlClient(): TorControlClient? = control?.client

    // TorService's connection is not used: it is published before TorService finishes setting it up
    override fun openControlClient(): TorControlClient? {
        if (serviceConnection.service == null) return null
        closeControl()
        val socket = LocalSocket()
        return try {
            socket.connect(LocalSocketAddress(controlSocketPath(), LocalSocketAddress.Namespace.FILESYSTEM))
            val connection = TorControlConnection(socket.inputStream, socket.outputStream)
            connection.launchThread(true)
            Control(socket, TorControlClient(connection, ByteArray(0))).also { control = it }.client
        } catch (e: IOException) {
            socket.close()
            null
        }
    }

    private fun closeControl() {
        control?.socket?.close()
        control = null
    }

    private fun controlSocketPath() =
        File(context.getDir(TOR_SERVICE_DIR, Context.MODE_PRIVATE), "data/ControlSocket").path

    // Tor used to run as a separate executable with its own files; they are useless now
    override fun deleteStaleFiles(torEnabled: Boolean) {
        listOf("torrc", "torrc.custom", "control.txt", "geoip", "geoip6").forEach {
            File(context.filesDir, it).delete()
        }
        context.getDir(LEGACY_DATA_DIR, Context.MODE_PRIVATE).deleteRecursively()
    }

    // TorService passes this file with -f, so its options override the defaults it writes
    private fun writeTorrc() {
        TorService.getTorrc(context).writeText(TorGateway.BASE_TORRC_OPTIONS.joinToString("\n"))
    }

    private class Control(val socket: LocalSocket, val client: TorControlClient)

    companion object {
        const val TOR_SERVICE_DIR = "TorService"
        const val LEGACY_DATA_DIR = "data"

        private const val EVENT_BUFFER = 8
    }
}
