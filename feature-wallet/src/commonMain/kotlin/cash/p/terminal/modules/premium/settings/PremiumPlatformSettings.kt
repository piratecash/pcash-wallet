package cash.p.terminal.modules.premium.settings

import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

/** Login logging and push notifications live in Android-only modules; where unbound, their rows are hidden. */
interface PremiumPlatformSettings {
    suspend fun shouldShowLoggingAlert(): Boolean
    fun openLoginLogging(navigation: HSNavigation)
    fun openAuthorizationInfo(navigation: HSNavigation)
    fun pushNotificationsPage(): HSPage
}
