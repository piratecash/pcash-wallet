package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.wallet.hardware.HardwareCancelledException
import cash.p.terminal.wallet.hardware.HardwareSigningException

/** The open app returned a key other than the stored one: another device or another passphrase. */
class LedgerIdentityMismatchException : HardwareSigningException("Another Ledger or passphrase is connected")

class LedgerAppMissingException(val appName: String) :
    HardwareSigningException("$appName app is not installed on the Ledger")

/** None of the apps that could prove the device is the account's one is installed. */
class LedgerIdentityAppMissingException(val appNames: List<String>) :
    HardwareSigningException("No app holding this account's keys is installed on the Ledger")

class LedgerRejectedException : HardwareCancelledException("Rejected on the Ledger")

/** The app refuses contract data and typed messages until its Blind signing setting is on. */
class LedgerBlindSigningRequiredException(val appName: String) :
    HardwareSigningException("Blind signing is disabled in the $appName app")

/** The device answered, but not with a usable signature for this transaction. */
class LedgerSigningFailedException(message: String) : HardwareSigningException(message)

/** The session reconnected to a device the identity proof did not cover. */
internal class LedgerConnectionChangedException : Exception("The Ledger connection changed")

class LedgerNotFoundException : Exception("No Ledger is connected")

/** No device answered: none was connected, or it was unplugged. */
val Throwable.isLedgerUnreachable: Boolean
    get() = this is LedgerNotFoundException || this is LedgerException.TransportClosed

class LedgerUsbPermissionDeniedException : Exception("USB access to the Ledger was denied")

class LedgerNoSupportedAppsException : Exception("No supported Ledger app gave a key")

/** The SDK error as the app-level error the UI and callers handle. */
internal fun LedgerException.forApp(appName: String): Exception = when (this) {
    is LedgerException.AppNotInstalled -> LedgerAppMissingException(appName)
    is LedgerException.UserRejected -> LedgerRejectedException()
    is LedgerException.BlindSigningRequired -> LedgerBlindSigningRequiredException(appName)
    else -> this
}
