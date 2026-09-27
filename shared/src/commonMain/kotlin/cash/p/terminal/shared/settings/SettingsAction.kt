package cash.p.terminal.shared.settings

sealed interface SettingsAction {
    data object Donate : SettingsAction
    data object MiniApp : SettingsAction
    data object ManageWallets : SettingsAction
    data object BlockchainSettings : SettingsAction
    data object WalletConnect : SettingsAction
    data object TonConnect : SettingsAction
    data object BackupManager : SettingsAction
    data object SecurityCenter : SettingsAction
    data object Contacts : SettingsAction
    data object Appearance : SettingsAction
    data object BaseCurrency : SettingsAction
    data object Language : SettingsAction
    data object AddressChecker : SettingsAction
    data object SwapProviders : SettingsAction
    data object OfflineBroadcast : SettingsAction
    data object AboutPremium : SettingsAction
    data object PremiumSettings : SettingsAction
    data object AdvancedSecurity : SettingsAction
    data object SoftwareUpdate : SettingsAction
    data object AboutApp : SettingsAction
    data object RateApp : SettingsAction
    data object ShareApp : SettingsAction
    data class Contact(val isPayCoreEnabled: Boolean) : SettingsAction
    data object CompanyWebsite : SettingsAction
}
