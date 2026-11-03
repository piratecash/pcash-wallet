package cash.p.terminal.core

import cash.p.terminal.modules.amount.AmountInputType
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.entities.EncryptedString
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import kotlinx.coroutines.flow.StateFlow

interface BalanceSettingsStorage {
    var sortType: BalanceSortType
    var balanceHidden: Boolean
    var balanceAutoHideEnabled: Boolean
    var balanceTotalCoinUid: String?
    var isRoundingAmountMainPage: Boolean
    var displayDiffPricePeriod: DisplayPricePeriod
    val displayDiffPricePeriodFlow: StateFlow<DisplayPricePeriod>
    var displayDiffOptionType: DisplayDiffOptionType
    val displayDiffOptionTypeFlow: StateFlow<DisplayDiffOptionType>
    var nonRecommendedAccountAlertDismissedAccounts: Set<String>
}

interface TransactionSettingsStorage {
    var transactionHideEnabled: Boolean

    /** Already encrypted by the caller with `IEncryptionManager`. */
    var transactionHideSecretPin: EncryptedString?
    var transactionDisplayLevel: TransactionDisplayLevel
    var transactionFiltersEnabled: Boolean
    var hideSuspiciousTransactions: Boolean
    var addressPoisoningViewMode: AddressPoisoningViewMode
    var recipientAddressBaseCheckEnabled: Boolean
    var recipientAddressContractCheckEnabled: Boolean
    var amountInputType: AmountInputType?
    var rbfEnabled: Boolean
    var utxoExpertModeEnabled: Boolean
    val utxoExpertModeEnabledFlow: StateFlow<Boolean>
    var swapMevProtectionEnabled: Boolean
    var transferPasscodeEnabled: Boolean
}

interface NetworkSettingsStorage {
    val torEnabled: Boolean

    /** Orchard -> Ironwood migration transactions, keyed as `accountId:canonicalTransactionHash`. */
    var zcashIronwoodMigrationTxIds: Set<String>
    var zcashAccountIds: Set<String>
    var moneroSkipNewAddressConfirm: Boolean
    fun isNetworkFeeWarningDismissed(blockchainTypeUid: String): Boolean
    fun dismissNetworkFeeWarning(blockchainTypeUid: String)
}
