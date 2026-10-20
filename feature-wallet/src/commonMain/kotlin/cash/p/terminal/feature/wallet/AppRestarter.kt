package cash.p.terminal.feature.wallet

/** Restarts the app UI from its launch screen, e.g. after a full backup was restored. */
interface AppRestarter {
    fun restart()
}
