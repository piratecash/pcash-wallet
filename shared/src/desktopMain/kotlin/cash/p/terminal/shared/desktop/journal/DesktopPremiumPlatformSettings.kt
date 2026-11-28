package cash.p.terminal.shared.desktop.journal

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.premium.settings.PremiumPlatformSettings
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoggingSettings
import kotlinx.coroutines.withContext

/** Desktop has the login journal but no push notifications. */
class DesktopPremiumPlatformSettings(
    private val vault: DesktopVault,
    private val loggingSettings: ILoggingSettings,
    private val userManager: UserManager,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : PremiumPlatformSettings {

    /** A selfie that cannot be taken, or journal switches left on after Premium ended. */
    override suspend fun shouldShowLoggingAlert(): Boolean = withContext(dispatcherProvider.io) {
        val level = userManager.getUserLevel()
        if (checkPremiumUseCase.getPremiumType().isPremium()) {
            loggingSettings.anySelfieEnabled(level) && vault.selfieProblem()
        } else {
            loggingSettings.hasEnabledAtLeastOneSettingsEnabled(level)
        }
    }

    @Composable
    override fun rememberLoginLoggingAction(navigation: HSNavigation) =
        navigation.rememberJournalAccessAction { navigation.slideFromRight(DesktopLoginLoggingPage()) }

    @Composable
    override fun rememberAuthorizationInfoAction(navigation: HSNavigation) =
        navigation.rememberJournalAccessAction { navigation.slideFromRight(DesktopAuthorizationInfoPage()) }

    override fun pushNotificationsPage(): HSPage? = null
}
