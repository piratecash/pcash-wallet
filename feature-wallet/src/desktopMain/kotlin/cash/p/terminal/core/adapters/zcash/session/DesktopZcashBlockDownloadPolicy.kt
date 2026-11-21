package cash.p.terminal.core.adapters.zcash.session

import cash.p.zcash.BlockDownload

/** Past 8 streams the desktop scanner is the bottleneck again. */
class DesktopZcashBlockDownloadPolicy : ZcashBlockDownloadPolicy {
    override val blockDownload = BlockDownload(maxStreams = 8, bufferMegabytes = 64)
}
