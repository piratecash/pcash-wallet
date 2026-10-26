package cash.p.terminal.feature.wallet

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import org.koin.compose.koinInject

/** Confirms the user before a secret is shown or exported; [action] runs only once confirmed. */
interface SecretActionAuthorizer {
    /** [A] is the click-time argument handed back to [action]; remember at the screen root. */
    @Composable
    fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit

    /** Confirms before transactions hidden by the user are shown; remember at the screen root. */
    @Composable
    fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit): () -> Unit
}

@Composable
fun <A> HSNavigation.rememberSecretAction(action: (A) -> Unit): (A) -> Unit =
    koinInject<SecretActionAuthorizer>().rememberAuthorization(this, action)

@Composable
fun HSNavigation.rememberSecretAction(action: () -> Unit): () -> Unit {
    val authorized = rememberSecretAction<Unit> { action() }
    return { authorized(Unit) }
}
