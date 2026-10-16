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
    val securityCenterShowAlert: Boolean,
    val securityCenterShowNewBadge: Boolean,
    val aboutAppShowAlert: Boolean,
    val wcCounterType: CounterType?,
    val premiumSettingsShowAlert: Boolean,
    val isPayCoreEnabled: Boolean,
) {
    val manageWalletShowAlert: Boolean get() = !allBackedUp || hasNonStandardAccount
}

sealed class CounterType {
    class SessionCounter(val number: Int) : CounterType()
    class PendingRequestCounter(val number: Int) : CounterType()
}
