package cash.p.terminal.core.adapters.zcash.session

import cash.p.zcash.BlockDownload

/** Phones are scan-bound: more streams or buffer only cost memory. */
class AndroidZcashBlockDownloadPolicy : ZcashBlockDownloadPolicy {
    override val blockDownload = BlockDownload(maxStreams = 4, bufferMegabytes = 16)
}
