package cash.p.terminal.feature.wallet

import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.navigation.HSNavigation

/** Confirms the user before a secret is shown or exported; [action] runs only once confirmed. */
interface SecretActionAuthorizer {
    fun authorize(navigation: HSNavigation, action: () -> Unit)
}

fun HSNavigation.authorizeSecretAction(action: () -> Unit) =
    getKoinInstance<SecretActionAuthorizer>().authorize(this, action)
