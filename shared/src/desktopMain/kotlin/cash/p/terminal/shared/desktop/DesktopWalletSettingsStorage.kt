package cash.p.terminal.shared.desktop

import cash.p.terminal.core.AppVersionStorage
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.NetworkSettingsStorage
import cash.p.terminal.core.ReleaseNotesStorage
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.AppVersion
import cash.p.terminal.entities.decodeAppVersions
import cash.p.terminal.entities.encodeToJson
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.entities.EncryptedString
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.flow.StateFlow

/** Same defaults as Android `LocalStorageManager`; the PIN arrives already encrypted. Tor is Android-only. */
class DesktopWalletSettingsStorage(private val store: DesktopSettingsStore) :
    BalanceSettingsStorage, TransactionSettingsStorage, NetworkSettingsStorage, ReleaseNotesStorage,
    AppVersionStorage {

    override var sortType: BalanceSortType
        get() = BalanceSortType.getTypeFromString(store.string(SORT_TYPE) ?: BalanceSortType.Value.getAsString())
        set(value) = store.setString(SORT_TYPE, value.getAsString())

    override var balanceHidden: Boolean
        get() = store.flag(BALANCE_HIDDEN) ?: false
        set(value) = store.setFlag(BALANCE_HIDDEN, value)

    override var balanceAutoHideEnabled: Boolean
        get() = store.flag(BALANCE_AUTO_HIDE) ?: false
        set(value) = store.setFlag(BALANCE_AUTO_HIDE, value)

    override var balanceTotalCoinUid: String?
        get() = store.string(BALANCE_TOTAL_COIN_UID)
        set(value) = store.setString(BALANCE_TOTAL_COIN_UID, value)

    override var isRoundingAmountMainPage: Boolean
        get() = store.flag(ROUNDING_AMOUNT) ?: true
        set(value) = store.setFlag(ROUNDING_AMOUNT, value)

    override val displayDiffPricePeriodFlow: StateFlow<DisplayPricePeriod> =
        store.stringFlow(PRICE_PERIOD) { it.toEnumOrNull<DisplayPricePeriod>() ?: DisplayPricePeriod.ONE_DAY }

    override var displayDiffPricePeriod: DisplayPricePeriod
        get() = displayDiffPricePeriodFlow.value
        set(value) = store.setString(PRICE_PERIOD, value.name)

    override val displayDiffOptionTypeFlow: StateFlow<DisplayDiffOptionType> =
        store.stringFlow(DIFF_OPTION_TYPE) { it.toEnumOrNull<DisplayDiffOptionType>() ?: DisplayDiffOptionType.BOTH }

    override var displayDiffOptionType: DisplayDiffOptionType
        get() = displayDiffOptionTypeFlow.value
        set(value) = store.setString(DIFF_OPTION_TYPE, value.name)

    override var nonRecommendedAccountAlertDismissedAccounts: Set<String>
        get() = stringSet(NON_RECOMMENDED_DISMISSED)
        set(value) = setStringSet(NON_RECOMMENDED_DISMISSED, value)

    override var transactionHideEnabled: Boolean
        get() = store.flag(TRANSACTION_HIDE) ?: false
        set(value) = store.setFlag(TRANSACTION_HIDE, value)

    override var transactionHideSecretPin: EncryptedString?
        get() = store.string(TRANSACTION_HIDE_PIN)?.let(::EncryptedString)
        set(value) = store.setString(TRANSACTION_HIDE_PIN, value?.value)

    override var transactionDisplayLevel: TransactionDisplayLevel
        get() = store.string(TRANSACTION_DISPLAY_LEVEL).toEnumOrNull() ?: TransactionDisplayLevel.NOTHING
        set(value) = store.setString(TRANSACTION_DISPLAY_LEVEL, value.name)

    override var transactionFiltersEnabled: Boolean
        get() = store.flag(TRANSACTION_FILTERS) ?: false
        set(value) = store.setFlag(TRANSACTION_FILTERS, value)

    override var hideSuspiciousTransactions: Boolean
        get() = store.flag(HIDE_SUSPICIOUS) ?: true
        set(value) = store.setFlag(HIDE_SUSPICIOUS, value)

    override var addressPoisoningViewMode: AddressPoisoningViewMode
        get() = store.string(ADDRESS_POISONING_VIEW_MODE).toEnumOrNull() ?: AddressPoisoningViewMode.STANDARD
        set(value) = store.setString(ADDRESS_POISONING_VIEW_MODE, value.name)

    override var recipientAddressBaseCheckEnabled: Boolean
        get() = store.flag(RECIPIENT_ADDRESS_BASE_CHECK) ?: false
        set(value) = store.setFlag(RECIPIENT_ADDRESS_BASE_CHECK, value)

    override var recipientAddressContractCheckEnabled: Boolean
        get() = store.flag(RECIPIENT_ADDRESS_CONTRACT_CHECK) ?: true
        set(value) = store.setFlag(RECIPIENT_ADDRESS_CONTRACT_CHECK, value)

    override val torEnabled: Boolean = false

    override var zcashIronwoodMigrationTxIds: Set<String>
        get() = stringSet(ZCASH_IRONWOOD_TX_IDS)
        set(value) = setStringSet(ZCASH_IRONWOOD_TX_IDS, value)

    override var zcashAccountIds: Set<String>
        get() = stringSet(ZCASH_ACCOUNT_IDS)
        set(value) = setStringSet(ZCASH_ACCOUNT_IDS, value)

    override var moneroSkipNewAddressConfirm: Boolean
        get() = store.flag(MONERO_SKIP_NEW_ADDRESS_CONFIRM) ?: false
        set(value) = store.setFlag(MONERO_SKIP_NEW_ADDRESS_CONFIRM, value)

    override var changelogShownForAppVersion: String?
        get() = store.string(CHANGELOG_SHOWN_FOR_APP_VERSION)
        set(value) = store.setString(CHANGELOG_SHOWN_FOR_APP_VERSION, value)

    override var showChangelogAfterUpdate: Boolean
        get() = store.flag(SHOW_CHANGELOG_AFTER_UPDATE) ?: true
        set(value) = store.setFlag(SHOW_CHANGELOG_AFTER_UPDATE, value)

    override var appVersions: List<AppVersion>
        get() = store.string(APP_VERSIONS)?.let { tryOrNull { it.decodeAppVersions() } }.orEmpty()
        set(value) = store.setString(APP_VERSIONS, value.encodeToJson())

    override fun isNetworkFeeWarningDismissed(blockchainTypeUid: String): Boolean =
        store.flag(networkFeeWarningKey(blockchainTypeUid)) ?: false

    override fun dismissNetworkFeeWarning(blockchainTypeUid: String) =
        store.setFlag(networkFeeWarningKey(blockchainTypeUid), true)

    private fun networkFeeWarningKey(uid: String) = "$NETWORK_FEE_WARNING_PREFIX$uid"

    private fun stringSet(name: String): Set<String> =
        store.string(name)?.split(SEPARATOR)?.filter(String::isNotEmpty)?.toSet().orEmpty()

    private fun setStringSet(name: String, value: Set<String>) = store.setString(name, value.joinToString(SEPARATOR))

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        enumValues<T>().firstOrNull { it.name == this }

    private companion object {
        const val SEPARATOR = ","
        const val APP_VERSIONS = "appVersions"
        const val SORT_TYPE = "balanceSortType"
        const val BALANCE_HIDDEN = "balanceHidden"
        const val BALANCE_AUTO_HIDE = "balanceAutoHideEnabled"
        const val BALANCE_TOTAL_COIN_UID = "balanceTotalCoinUid"
        const val ROUNDING_AMOUNT = "roundingAmountMainPage"
        const val PRICE_PERIOD = "displayDiffPricePeriod"
        const val DIFF_OPTION_TYPE = "displayDiffOptionType"
        const val NON_RECOMMENDED_DISMISSED = "nonRecommendedAccountAlertDismissedAccounts"
        const val TRANSACTION_HIDE = "transactionHideEnabled"
        const val TRANSACTION_HIDE_PIN = "transactionHideSecretPin"
        const val TRANSACTION_DISPLAY_LEVEL = "transactionDisplayLevel"
        const val TRANSACTION_FILTERS = "transactionFiltersEnabled"
        const val HIDE_SUSPICIOUS = "hideSuspiciousTransactions"
        const val ADDRESS_POISONING_VIEW_MODE = "addressPoisoningViewMode"
        const val RECIPIENT_ADDRESS_BASE_CHECK = "recipientAddressBaseCheckEnabled"
        const val RECIPIENT_ADDRESS_CONTRACT_CHECK = "recipientAddressContractCheckEnabled"
        const val ZCASH_IRONWOOD_TX_IDS = "zcashIronwoodMigrationTxIds"
        const val ZCASH_ACCOUNT_IDS = "zcashAccountIds"
        const val MONERO_SKIP_NEW_ADDRESS_CONFIRM = "moneroSkipNewAddressConfirm"
        const val CHANGELOG_SHOWN_FOR_APP_VERSION = "changelogShownForAppVersion"
        const val SHOW_CHANGELOG_AFTER_UPDATE = "showChangelogAfterUpdate"
        const val NETWORK_FEE_WARNING_PREFIX = "networkFeeWarningDismissed_"
    }
}
