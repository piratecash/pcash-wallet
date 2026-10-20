package cash.p.terminal.core

import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase

fun HSNavigation.premiumAction(block: () -> Unit) {
    if (getKoinInstance<CheckPremiumUseCase>().getPremiumType().isPremium()) {
        block.invoke()
    } else {
        slideFromBottomForResult<AboutPremiumPage.Result>(
            AboutPremiumPage(AboutPremiumPage.CloseOnPremiumInput())
        ) {
            block.invoke()
        }
    }
}
