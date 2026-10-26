package cash.p.terminal.ui_compose

import kotlin.concurrent.Volatile

object ScreenSecurityState {
    @Volatile
    var isAppLocked: Boolean = false
}
