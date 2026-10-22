package cash.p.terminal.ledger

import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.transport.SpeculosTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.milliseconds

/**
 * A speculos emulator started with its default seed: `SPECULOS_APDU_PORT` for APDUs, `SPECULOS_API_URL`
 * (default `http://127.0.0.1:5000`) for its REST API. Tests skip unless the port is set and the app runs.
 */
object Speculos {
    const val SEED = "glory promote mansion idle axis finger extra february uncover one trip resource " +
        "lawn turtle enact monster seven myth punch hobby comfort wild raise skin"

    private val port = System.getenv("SPECULOS_APDU_PORT")?.toIntOrNull()
    private val api = System.getenv("SPECULOS_API_URL") ?: "http://127.0.0.1:5000"
    private val http = HttpClient.newHttpClient()

    /** Runs [block] on a session to the emulator, or skips the test when it does not run [app]. */
    suspend fun <T> withSession(app: String, block: suspend (LedgerSession) -> T): T {
        assumeTrue("SPECULOS_APDU_PORT is not set", port != null)
        val apdu = requireNotNull(port)
        val running = runningApp(apdu)
        assumeTrue("speculos runs '$running', not '$app'", running == app)
        val session = LedgerSession { SpeculosTransport.connect(HOST, apdu) }
        return try {
            block(session)
        } finally {
            session.close()
        }
    }

    /** Runs [block] while pressing through the review screens and approving on the first approval screen. */
    suspend fun <T> approving(block: suspend () -> T): T = coroutineScope {
        val walker = launch(Dispatchers.IO) {
            while (true) {
                delay(STEP)
                val screen = currentScreen()
                when {
                    isApproval(screen) -> press("both")
                    isHome(screen) -> Unit
                    // A screen read mid-redraw can skip a warning onto its reject branch; step back to it.
                    "Reject" in screen -> press("left")
                    else -> press("right")
                }
            }
        }
        val result = try {
            block()
        } finally {
            walker.cancel()
        }
        // The Stellar app drops a request that arrives while its "verified"/"signed" screen is still up.
        pressUntilHome()
        result
    }

    /** Flips [setting] in the app's settings menu; every press waits for its screen. */
    suspend fun toggleSetting(setting: String) {
        // A blind "both" on "Quit app" would close the emulator.
        pressUntilHome()
        pressUntil("App settings", "right")
        press("both")
        pressUntil(setting, "right")
        press("both")
        pressUntil("Back", "right")
        press("both")
        pressUntilHome()
    }

    /**
     * Picks entry [index] of the first setting's choice list (TON: "Allow blind sign", No / Yes). The list opens on
     * the current value and shows no marker, so entries are reached by position.
     */
    suspend fun chooseInFirstSetting(index: Int) {
        pressUntilHome()
        press("right")
        press("both")
        press("both")
        repeat(CHOICES) { press("left") }
        repeat(index) { press("right") }
        press("both")
        repeat(SETTINGS_ENTRIES - 1) { press("right") }
        press("both")
        pressUntilHome()
    }

    /** Closes an error [screen] that the app holds until a button is pressed. */
    suspend fun dismiss(screen: String) {
        pressUntil(screen, button = null)
        press("both")
    }

    private suspend fun pressUntil(screen: String, button: String?) = pressUntil(button) { screen in it }

    private suspend fun pressUntilHome() = pressUntil("left", ::isHome)

    // Zondax apps (THORChain) show their name over a "Ready" line instead of "<app> is ready".
    private fun isHome(screen: String): Boolean = READY_SCREEN in screen || ZONDAX_READY_TEXT in screen

    private suspend fun pressUntil(button: String?, reached: (String) -> Boolean) {
        repeat(MAX_PRESSES) {
            delay(STEP)
            val screen = currentScreen()
            if (reached(screen)) return
            button?.let(::press)
        }
        error("The expected screen did not come up: ${currentScreen()}")
    }

    private suspend fun runningApp(port: Int): String {
        val transport = SpeculosTransport.connect(HOST, port)
        return try {
            val answer = transport.exchange(GET_APP_AND_VERSION)
            answer.decodeToString(2, 2 + answer[1].toInt())
        } finally {
            transport.close()
        }
    }

    // A bare "Confirm" approves an address; "Confirm transaction" only opens the legacy fee review.
    private fun isApproval(screen: String): Boolean =
        TEXT.findAll(screen).map { it.groupValues[1] }.toList() == listOf("Confirm") ||
            APPROVE_SCREENS.any { screen.contains(it, ignoreCase = true) } && "Reject" !in screen

    fun currentScreen(): String {
        val request = HttpRequest.newBuilder(URI("$api/events?currentscreenonly=true")).GET().build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    fun press(button: String) {
        val request = HttpRequest.newBuilder(URI("$api/button/$button"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"action":"press-and-release"}"""))
            .header("Content-Type", "application/json")
            .build()
        http.send(request, HttpResponse.BodyHandlers.discarding())
    }

    private const val HOST = "127.0.0.1"
    private val GET_APP_AND_VERSION = byteArrayOf(0xB0.toByte(), 0x01, 0, 0, 0)
    private val STEP = 300.milliseconds
    private const val READY_SCREEN = "is ready"
    private const val ZONDAX_READY_TEXT = "\"text\": \"Ready\""
    private const val CHOICES = 2
    private const val SETTINGS_ENTRIES = 3
    private const val MAX_PRESSES = 10
    private val APPROVE_SCREENS = listOf("Approve", "Accept", "accept risk", "Sign transaction", "Sign message")
    private val TEXT = Regex("\"text\": \"([^\"]*)\"")
}
