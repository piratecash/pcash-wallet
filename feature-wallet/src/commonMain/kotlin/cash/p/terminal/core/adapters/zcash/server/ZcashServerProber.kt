package cash.p.terminal.core.adapters.zcash.server

import cash.p.terminal.core.tryOrNull
import cash.p.zcash.ServerProbe
import cash.p.zcash.Transport
import cash.p.zcash.ZcashSdk
import cash.p.zcash.probeServer

interface ZcashServerProber {
    /** Needs [ZcashSdk.initialize] done; the scheduler only runs once a session has opened a wallet. */
    suspend fun probe(url: String, transport: Transport, rangeBlocks: Int): ServerProbe

    /** Process-wide count of compact-block stream breaks; 0 while the native side is unavailable. */
    fun streamBreaks(): Long
}

class ZcashServerProberImpl : ZcashServerProber {

    override suspend fun probe(url: String, transport: Transport, rangeBlocks: Int): ServerProbe =
        ZcashSdk.probeServer(url, transport, rangeBlocks, PROBE_TIMEOUT_MS)

    override fun streamBreaks(): Long = tryOrNull { ZcashSdk.networkStats().streamBreaks } ?: 0

    private companion object {
        const val PROBE_TIMEOUT_MS = 8_000L
    }
}
