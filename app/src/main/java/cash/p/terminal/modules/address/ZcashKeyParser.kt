package cash.p.terminal.modules.address

import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.isValidKey

/** Recognizes a standalone Zcash key: the HRP picks the branch, the SDK gives the final answer. */
object ZcashKeyParser {

    fun isUfvk(key: String): Boolean = key.startsWith(UFVK_HRP_PREFIX)

    fun isSaplingViewingKeyFormat(key: String): Boolean = key.startsWith(SAPLING_VIEWING_HRP_PREFIX)

    fun isSaplingViewingKey(key: String): Boolean = isSaplingViewingKeyFormat(key) && key.isValid()

    fun isSaplingSpendingKey(key: String): Boolean =
        key.startsWith(SAPLING_SPENDING_HRP_PREFIX) && key.isValid()

    private fun String.isValid() = ZcashSdk.isValidKey(this, ZcashNetwork.MAIN)

    private const val UFVK_HRP_PREFIX = "uview"
    private const val SAPLING_VIEWING_HRP_PREFIX = "zxviews"
    private const val SAPLING_SPENDING_HRP_PREFIX = "secret-extended-key"
}
