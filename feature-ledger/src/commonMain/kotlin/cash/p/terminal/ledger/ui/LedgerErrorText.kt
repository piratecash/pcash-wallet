package cash.p.terminal.ledger.ui

import androidx.compose.runtime.Composable
import cash.p.terminal.ledger.account.LedgerAccountApp
import cash.p.terminal.ledger.domain.LedgerAppMissingException
import cash.p.terminal.ledger.domain.LedgerBlindSigningRequiredException
import cash.p.terminal.ledger.domain.LedgerIdentityAppMissingException
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerNoSupportedAppsException
import cash.p.terminal.ledger.domain.LedgerNotFoundException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerSkippedApp
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.domain.LedgerUsbPermissionDeniedException
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ledger_blind_signing_required
import cash.p.terminal.resources.ledger_connect_failed
import cash.p.terminal.resources.ledger_data_too_large
import cash.p.terminal.resources.ledger_device_locked
import cash.p.terminal.resources.ledger_device_not_set_up
import cash.p.terminal.resources.ledger_identity_mismatch
import cash.p.terminal.resources.ledger_install_app
import cash.p.terminal.resources.ledger_install_identity_app
import cash.p.terminal.resources.ledger_no_supported_apps
import cash.p.terminal.resources.ledger_not_found
import cash.p.terminal.resources.ledger_rejected
import cash.p.terminal.resources.ledger_signing_failed
import cash.p.terminal.resources.ledger_ton_blind_signing_required
import cash.p.terminal.resources.ledger_tron_settings_required
import cash.p.terminal.resources.ledger_update_app
import cash.p.terminal.resources.ledger_usb_permission_denied
import cash.p.terminal.resources.ledger_zcash_reopen_app
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ledgerErrorText(error: Throwable): String {
    val res = error.messageRes()
    return error.appName()?.let { stringResource(res, it) } ?: stringResource(res)
}

fun ledgerErrorMessage(error: Throwable): String =
    ledgerErrorMessageOrNull(error) ?: Translator.getString(Res.string.ledger_connect_failed)

/** The localized text of an error the Ledger raised, or null when [error] did not come from the Ledger. */
fun ledgerErrorMessageOrNull(error: Throwable): String? {
    val res = error.ledgerMessageRes() ?: return null
    return error.appName()?.let { Translator.getString(res, it) } ?: Translator.getString(res)
}

/** What to fix for the first skipped app that needs the user's action, or null when every skip was a decline. */
fun ledgerSkippedAppsMessage(skipped: List<LedgerSkippedApp>): String? =
    skipped.firstOrNull { it.reason !is LedgerRejectedException }?.let { ledgerErrorMessageOrNull(it.reason) }

/** The Ledger's reason as a resource without arguments, or null when [error] did not come from the Ledger. */
fun ledgerErrorResOrNull(error: Throwable): StringResource? =
    error.ledgerMessageRes()?.let { if (error.appName() == null) it else Res.string.ledger_connect_failed }

private fun Throwable.messageRes(): StringResource = ledgerMessageRes() ?: Res.string.ledger_connect_failed

private fun Throwable.ledgerMessageRes(): StringResource? = when (this) {
    is LedgerIdentityMismatchException -> Res.string.ledger_identity_mismatch
    is LedgerAppMissingException -> Res.string.ledger_install_app
    is LedgerIdentityAppMissingException -> Res.string.ledger_install_identity_app
    is LedgerNotFoundException -> Res.string.ledger_not_found
    is LedgerUsbPermissionDeniedException -> Res.string.ledger_usb_permission_denied
    is LedgerNoSupportedAppsException -> Res.string.ledger_no_supported_apps
    is LedgerRejectedException -> Res.string.ledger_rejected
    is LedgerSigningFailedException -> Res.string.ledger_signing_failed
    is LedgerBlindSigningRequiredException -> blindSigningInstruction()
    is LedgerException -> statusMessageRes()
    else -> null
}

private fun LedgerException.statusMessageRes(): StringResource = when (this) {
    is LedgerException.AppVersionTooOld -> Res.string.ledger_update_app
    is LedgerException.DeviceLocked -> Res.string.ledger_device_locked
    is LedgerException.DeviceNotOnboarded -> Res.string.ledger_device_not_set_up
    is LedgerException.DataTooLarge -> Res.string.ledger_data_too_large
    // Only the Zcash app caps its key derivations; reopening it starts a new budget.
    is LedgerException.KeyDerivationLimit -> Res.string.ledger_zcash_reopen_app
    else -> Res.string.ledger_connect_failed
}

// The Tron app names its settings per kind of data, the TON app its switch "Allow blind sign".
private fun LedgerBlindSigningRequiredException.blindSigningInstruction(): StringResource = when (appName) {
    LedgerAccountApp.Tron.appName -> Res.string.ledger_tron_settings_required
    LedgerAccountApp.Ton.appName -> Res.string.ledger_ton_blind_signing_required
    else -> Res.string.ledger_blind_signing_required
}

private fun Throwable.appName(): String? = when (this) {
    is LedgerAppMissingException -> appName
    is LedgerIdentityAppMissingException -> appNames.joinToString(", ")
    is LedgerBlindSigningRequiredException -> appName
    is LedgerException.AppVersionTooOld -> appName
    else -> null
}
