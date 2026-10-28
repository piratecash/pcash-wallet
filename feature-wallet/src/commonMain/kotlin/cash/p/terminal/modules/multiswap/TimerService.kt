package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ServiceState
import cash.p.terminal.modules.pin.core.UptimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

class TimerService(
    private val uptimeProvider: UptimeProvider,
) : ServiceState<TimerService.State>() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var expirationTimer: Job? = null

    private var secondsRemaining: Long? = null
    private var timeout = false

    override fun createState() = State(
        remaining = secondsRemaining,
        timeout = timeout
    )

    fun start(seconds: Long) {
        secondsRemaining = seconds
        timeout = false

        emitState()

        runExpirationTimer(
            millisInFuture = seconds * 1000,
            onTick = { millisUntilFinished ->
                secondsRemaining = ceil(millisUntilFinished / 1000.0).toLong()
                emitState()
            },
            onFinish = {
                timeout = true
                emitState()
            }
        )
    }

    fun stop() {
        expirationTimer?.cancel()
    }

    private fun runExpirationTimer(millisInFuture: Long, onTick: (Long) -> Unit, onFinish: () -> Unit) {
        expirationTimer?.cancel()
        // A deadline, not a tick count: a resumption delayed by sleep or a busy dispatcher must not extend the quote.
        val deadline = uptimeProvider.uptime + millisInFuture
        expirationTimer = scope.launch {
            var previousMillis = millisInFuture
            while (true) {
                val millisUntilFinished = deadline - uptimeProvider.uptime
                // Growing remaining time means the desktop wall clock moved back: expire rather than extend.
                if (millisUntilFinished <= 0 || millisUntilFinished > previousMillis) break
                previousMillis = millisUntilFinished
                onTick(millisUntilFinished)
                delay(minOf(TICK_MILLIS, millisUntilFinished))
            }
            onFinish()
        }
    }

    fun reset() {
        expirationTimer?.cancel()
        secondsRemaining = null
        timeout = false

        emitState()
    }

    data class State(val remaining: Long?, val timeout: Boolean)

    private companion object {
        const val TICK_MILLIS = 1000L
    }
}
