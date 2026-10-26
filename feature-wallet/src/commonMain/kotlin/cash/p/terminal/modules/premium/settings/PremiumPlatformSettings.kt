package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

/** Login logging and push notifications live in Android-only modules; where unbound, their rows are hidden. */
interface PremiumPlatformSettings {
    suspend fun shouldShowLoggingAlert(): Boolean

    @Composable
    fun rememberLoginLoggingAction(navigation: HSNavigation): () -> Unit

    @Composable
    fun rememberAuthorizationInfoAction(navigation: HSNavigation): () -> Unit

    fun pushNotificationsPage(): HSPage
}
