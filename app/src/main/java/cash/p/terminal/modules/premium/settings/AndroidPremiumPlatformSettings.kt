package cash.p.terminal.modules.premium.settings

import cash.p.terminal.core.authorizedLoggingAction
import cash.p.terminal.feature.logging.domain.usecase.LogLoginAttemptUseCase
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

class AndroidPremiumPlatformSettings(
    private val logLoginAttemptUseCase: LogLoginAttemptUseCase,
) : PremiumPlatformSettings {
    override suspend fun shouldShowLoggingAlert(): Boolean = logLoginAttemptUseCase.shouldShowLoggingAlert()

    override fun openLoginLogging(navigation: HSNavigation) =
        navigation.authorizedLoggingAction { navigation.slideFromRight(LoginLoggingPage()) }

    override fun openAuthorizationInfo(navigation: HSNavigation) =
        navigation.authorizedLoggingAction { navigation.slideFromRight(AuthorizationInfoPage()) }

    override fun pushNotificationsPage(): HSPage = PushNotificationsPage()
}
