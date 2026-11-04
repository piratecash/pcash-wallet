package cash.p.terminal.core

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase

/** Runs [block] at once with premium, otherwise after the premium page reports it; [A] is the click-time argument. */
@Composable
fun <A> HSNavigation.rememberPremiumAction(block: (A) -> Unit): (A) -> Unit {
    val launcher = rememberResultLauncher<AboutPremiumPage.Result, A> { _, arg -> block(arg) }
    return { arg ->
        if (isPremium()) {
            block(arg)
        } else {
            launcher.slideFromBottom(AboutPremiumPage(AboutPremiumPage.CloseOnPremiumInput()), arg)
        }
    }
}

@Composable
fun HSNavigation.rememberPremiumAction(block: () -> Unit): () -> Unit {
    val action = rememberPremiumAction<Unit> { block() }
    return { action(Unit) }
}

/** Enabling needs premium; disabling passes through. */
@Composable
fun HSNavigation.rememberPremiumToggle(setter: (Boolean) -> Unit): (Boolean) -> Unit {
    val enable = rememberPremiumAction { setter(true) }
    return { enabled -> if (enabled) enable() else setter(false) }
}

private fun isPremium(): Boolean = getKoinInstance<CheckPremiumUseCase>().getPremiumType().isPremium()
