package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.pin.core.UptimeProvider
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Follows the scheduler's virtual clock; [sleptMillis] is time that passed while no coroutine could resume. */
class VirtualUptimeProvider(private val scheduler: TestCoroutineScheduler) : UptimeProvider {
    var sleptMillis = 0L

    override val uptime: Long
        get() = scheduler.currentTime + sleptMillis
}
