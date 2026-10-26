package cash.p.terminal.core

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.settings.terms.TermsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberResultLauncher

/** [A] is the click-time argument handed back to [action]; remember at the screen root. */
@Composable
fun <A> HSNavigation.rememberTermsAcceptedAction(action: (A) -> Unit): (A) -> Unit {
    val launcher = rememberResultLauncher<TermsPage.Result, A> { result, arg ->
        if (result.termsAccepted) action(arg)
    }
    return { arg ->
        if (allTermsAccepted()) action(arg) else launcher.slideFromBottom(TermsPage(), arg)
    }
}

@Composable
fun HSNavigation.rememberTermsAcceptedAction(action: () -> Unit): () -> Unit {
    val accepted = rememberTermsAcceptedAction<Unit> { action() }
    return { accepted(Unit) }
}

private fun allTermsAccepted(): Boolean = getKoinInstance<ITermsManager>().allTermsAccepted
