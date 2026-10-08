package cash.p.terminal.core

import cash.p.terminal.R
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.settings.advancedsecurity.terms.DeleteContactsTermsPage
import cash.p.terminal.modules.settings.terms.TermsPage
import cash.p.terminal.navigation.HSNavigation

fun HSNavigation.authorizedAction(
    input: ConfirmPinPage.InputConfirm? = null,
    action: () -> Unit
) {
    val needEnterPin = when (input?.pinType) {
        PinType.REGULAR, PinType.DURESS, PinType.HIDDEN_WALLET, PinType.SECURE_RESET, null -> App.pinComponent.isPinSet
        PinType.TRANSFER -> getKoinInstance<ILocalStorage>().transferPasscodeEnabled
        PinType.TRANSACTIONS_HIDE -> App.pinComponent.isPinSet || getKoinInstance<TransactionHiddenManager>().transactionHiddenFlow.value.transactionAutoHidePinExists
        PinType.DELETE_CONTACTS -> App.pinComponent.isDeleteContactsPinSet() || App.pinComponent.isPinSet
        PinType.LOG_LOGGING -> App.pinComponent.isLogLoggingPinSet() || App.pinComponent.isPinSet
    }
    if (needEnterPin) {
        slideFromBottomForResult<ConfirmPinPage.Result>(ConfirmPinPage(input)) {
            if (it.success) {
                action.invoke()
            }
        }
    } else {
        action.invoke()
    }
}

fun HSNavigation.navigateWithTermsAccepted(action: () -> Unit) {
    if (!App.termsManager.allTermsAccepted) {
        slideFromBottomForResult<TermsPage.Result>(TermsPage()) { result ->
            if (result.termsAccepted) {
                action.invoke()
            }
        }
    } else {
        action.invoke()
    }
}

fun HSNavigation.ensurePinSet(
    descriptionResId: Int,
    pinType: PinType = PinType.REGULAR,
    action: () -> Unit
) {
    val pinSetAlready = when (pinType) {
        PinType.REGULAR -> App.pinComponent.isPinSet
        PinType.DELETE_CONTACTS -> App.pinComponent.isDeleteContactsPinSet()
        PinType.LOG_LOGGING -> App.pinComponent.isLogLoggingPinSet()
        else -> throw IllegalStateException("Unsupported pin type for ensurePinSet: $pinType")
    }
    if (pinSetAlready) {
        action.invoke()
    } else {
        slideFromRightForResult<SetPinPage.Result>(
            SetPinPage(SetPinPage.Input(descriptionResId, pinType))
        ) {
            action.invoke()
        }
    }
}

fun HSNavigation.ensurePinSetPremiumAction(
    descriptionResId: Int,
    pinType: PinType = PinType.REGULAR,
    setter: (Boolean) -> Unit
): (Boolean) -> Unit = { enabled ->
    if (enabled) {
        premiumAction {
            ensurePinSet(descriptionResId, pinType) {
                setter(true)
            }
        }
    } else {
        setter(false)
    }
}

/**
 * Authorizes access to Login Logging screens.
 * Requires LOG_LOGGING PIN if set, otherwise REGULAR PIN if set.
 */
fun HSNavigation.authorizedLoggingAction(action: () -> Unit) {
    val pinType = if (App.pinComponent.isLogLoggingPinSet()) {
        PinType.LOG_LOGGING
    } else {
        null  // Will use default REGULAR check
    }

    authorizedAction(
        input = pinType?.let {
            ConfirmPinPage.InputConfirm(R.string.confirm_pin_to_access_login_logging, it)
        },
        action = action
    )
}

fun HSNavigation.slideToDeleteContactsTerms() {
    slideFromRight(DeleteContactsTermsPage())
}

fun HSNavigation.authorizedDeleteContactsPasscodeAction(action: () -> Unit) {
    authorizedAction(
        input = ConfirmPinPage.InputConfirm(
            R.string.confirm_pin_to_disable_delete_all_contacts_passcode,
            PinType.DELETE_CONTACTS
        ),
        action = action
    )
}
