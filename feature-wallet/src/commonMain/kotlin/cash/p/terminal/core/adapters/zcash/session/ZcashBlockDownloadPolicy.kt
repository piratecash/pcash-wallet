package cash.p.terminal.core.adapters.zcash.session

import cash.p.zcash.BlockDownload

/** How many parallel block streams and how much buffer this device can afford for a sync. */
interface ZcashBlockDownloadPolicy {
    val blockDownload: BlockDownload
}
