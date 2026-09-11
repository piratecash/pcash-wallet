package cash.p.terminal.core.managers

/**
 * Birthday for a ZEC wallet the user has just created. It is a shipped constant so that creating
 * a wallet works offline; the chain tip refines it when the network happens to be up.
 */
class ZcashBirthdayProvider {

    fun getLatestCheckpointBlockHeight(): Long = LATEST_MAINNET_CHECKPOINT

    private companion object {
        /** Mainnet height at the time of the release. Raise it when cutting a new one. */
        const val LATEST_MAINNET_CHECKPOINT = 3_424_810L
    }
}
