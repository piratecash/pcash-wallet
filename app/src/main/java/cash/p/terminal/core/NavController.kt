package cash.p.terminal.core

import androidx.compose.runtime.Composable
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.settings.advancedsecurity.terms.DeleteContactsTermsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.rememberResultLauncher
import io.horizontalsystems.core.IPinComponent
import org.jetbrains.compose.resources.StringResource
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.confirm_pin_to_access_login_logging
import cash.p.terminal.resources.confirm_pin_to_disable_delete_all_contacts_passcode
import cash.p.terminal.resources.Unlock_EnterPasscode_Transactions_Hide

/** Runs [action] at once when no PIN guards [input], otherwise after the PIN is confirmed. */
@Composable
fun <A> HSNavigation.rememberAuthorizedAction(
    input: ConfirmPinPage.InputConfirm?,
    action: (A) -> Unit
): (A) -> Unit {
    val confirm = rememberPinConfirmation(action)
    return { arg -> confirm(input, arg) }
}

@Composable
fun HSNavigation.rememberAuthorizedAction(
    input: ConfirmPinPage.InputConfirm?,
    action: () -> Unit
): () -> Unit {
    val authorized = rememberAuthorizedAction<Unit>(input) { action() }
    return { authorized(Unit) }
}

/** Requires LOG_LOGGING PIN if set, otherwise REGULAR PIN if set. */
@Composable
fun HSNavigation.rememberAuthorizedLoggingAction(action: () -> Unit): () -> Unit {
    val confirm = rememberPinConfirmation<Unit> { action() }
    return { confirm(loggingConfirmInput(), Unit) }
}

@Composable
fun HSNavigation.rememberAuthorizedDeleteContactsPasscodeAction(action: () -> Unit): () -> Unit =
    rememberAuthorizedAction(
        input = ConfirmPinPage.InputConfirm(
            Res.string.confirm_pin_to_disable_delete_all_contacts_passcode,
            PinType.DELETE_CONTACTS
        ),
        action = action
    )

@Composable
fun HSNavigation.rememberAuthorizedShowAllTransactionsAction(action: () -> Unit): () -> Unit =
    rememberAuthorizedAction(
        input = ConfirmPinPage.InputConfirm(
            Res.string.Unlock_EnterPasscode_Transactions_Hide,
            PinType.TRANSACTIONS_HIDE
        ),
        action = action
    )

/** Runs [action] at once when a PIN of [pinType] is set, otherwise after it is set. */
@Composable
fun <A> HSNavigation.rememberEnsurePinSet(
    description: StringResource,
    pinType: PinType,
    action: (A) -> Unit
): (A) -> Unit {
    val launcher = rememberResultLauncher<SetPinPage.Result, A> { _, arg -> action(arg) }
    return { arg ->
        if (isPinSetAlready(pinType)) {
            action(arg)
        } else {
            launcher.slideFromRight(SetPinPage(SetPinPage.Input(description, pinType)), arg)
        }
    }
}

@Composable
fun HSNavigation.rememberEnsurePinSet(
    description: StringResource,
    pinType: PinType,
    action: () -> Unit
): () -> Unit {
    val ensured = rememberEnsurePinSet<Unit>(description, pinType) { action() }
    return { ensured(Unit) }
}

/** Enabling needs premium and a PIN of [pinType]; disabling passes through. */
@Composable
fun HSNavigation.rememberEnsurePinSetPremiumAction(
    description: StringResource,
    pinType: PinType,
    setter: (Boolean) -> Unit
): (Boolean) -> Unit {
    val enable = rememberPremiumAction(rememberEnsurePinSet(description, pinType) { setter(true) })
    return { enabled -> if (enabled) enable() else setter(false) }
}

fun HSNavigation.slideToDeleteContactsTerms() {
    slideFromRight(DeleteContactsTermsPage())
}

@Composable
private fun <A> HSNavigation.rememberPinConfirmation(
    action: (A) -> Unit
): (ConfirmPinPage.InputConfirm?, A) -> Unit {
    val launcher = rememberResultLauncher<ConfirmPinPage.Result, A> { result, arg ->
        if (result.success) action(arg)
    }
    return { input, arg ->
        if (needEnterPin(input?.pinType)) {
            launcher.slideFromBottom(ConfirmPinPage(input), arg)
        } else {
            action(arg)
        }
    }
}

private fun needEnterPin(pinType: PinType?): Boolean {
    val pinComponent = getKoinInstance<IPinComponent>()
    return when (pinType) {
        PinType.REGULAR, PinType.DURESS, PinType.HIDDEN_WALLET, PinType.SECURE_RESET, null -> pinComponent.isPinSet
        PinType.TRANSFER -> getKoinInstance<ILocalStorage>().transferPasscodeEnabled
        PinType.TRANSACTIONS_HIDE -> pinComponent.isPinSet ||
            getKoinInstance<TransactionHiddenManager>().transactionHiddenFlow.value.transactionAutoHidePinExists
        PinType.DELETE_CONTACTS -> pinComponent.isDeleteContactsPinSet() || pinComponent.isPinSet
        PinType.LOG_LOGGING -> pinComponent.isLogLoggingPinSet() || pinComponent.isPinSet
    }
}

private fun isPinSetAlready(pinType: PinType): Boolean {
    val pinComponent = getKoinInstance<IPinComponent>()
    return when (pinType) {
        PinType.REGULAR -> pinComponent.isPinSet
        PinType.DELETE_CONTACTS -> pinComponent.isDeleteContactsPinSet()
        PinType.LOG_LOGGING -> pinComponent.isLogLoggingPinSet()
        else -> throw IllegalStateException("Unsupported pin type for ensurePinSet: $pinType")
    }
}

private fun loggingConfirmInput(): ConfirmPinPage.InputConfirm? =
    if (getKoinInstance<IPinComponent>().isLogLoggingPinSet()) {
        ConfirmPinPage.InputConfirm(Res.string.confirm_pin_to_access_login_logging, PinType.LOG_LOGGING)
    } else {
        null
    }
