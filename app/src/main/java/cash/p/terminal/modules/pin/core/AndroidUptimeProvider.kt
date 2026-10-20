package cash.p.terminal.modules.pin.core

import android.os.SystemClock

class AndroidUptimeProvider : UptimeProvider {

    //elapsed time since phone boot
    override val uptime: Long
        get() = SystemClock.elapsedRealtime()

}
