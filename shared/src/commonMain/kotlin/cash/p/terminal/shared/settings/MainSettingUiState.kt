package cash.p.terminal.shared.settings

data class MainSettingUiState(
    val isUpdateAvailable: Boolean,
    val currentLanguage: String,
    val baseCurrencyCode: String,
    val appWebPageLink: String,
    val hasNonStandardAccount: Boolean,
    val allBackedUp: Boolean,
    val pendingRequestCount: Int,
    val walletConnectSessionCount: Int,
    val manageWalletShowAlert: Boolean,
    val securityCenterShowAlert: Boolean,
    val securityCenterShowNewBadge: Boolean,
    val aboutAppShowAlert: Boolean,
    val wcCounterType: CounterType?,
    val premiumSettingsShowAlert: Boolean,
    val isPayCoreEnabled: Boolean,
)

sealed class CounterType {
    class SessionCounter(val number: Int) : CounterType()
    class PendingRequestCounter(val number: Int) : CounterType()
}
