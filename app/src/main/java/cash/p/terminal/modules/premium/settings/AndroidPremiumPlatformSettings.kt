package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.core.rememberAuthorizedLoggingAction
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

class AndroidPremiumPlatformSettings(
    private val logLoginAttemptUseCase: LogLoginAttemptUseCase,
) : PremiumPlatformSettings {
    override suspend fun shouldShowLoggingAlert(): Boolean = logLoginAttemptUseCase.shouldShowLoggingAlert()

    @Composable
    override fun rememberLoginLoggingAction(navigation: HSNavigation) =
        navigation.rememberAuthorizedLoggingAction { navigation.slideFromRight(LoginLoggingPage()) }

    @Composable
    override fun rememberAuthorizationInfoAction(navigation: HSNavigation) =
        navigation.rememberAuthorizedLoggingAction { navigation.slideFromRight(AuthorizationInfoPage()) }

    override fun pushNotificationsPage(): HSPage = PushNotificationsPage()
}
