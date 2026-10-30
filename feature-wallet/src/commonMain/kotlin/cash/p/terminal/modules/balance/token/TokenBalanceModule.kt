package cash.p.terminal.modules.balance.token

import cash.p.terminal.core.MoneroSpendReadiness
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.modules.balance.TotalBalance
import cash.p.terminal.modules.balance.TotalService
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.transactions.Filter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.wallet.Wallet
import org.jetbrains.compose.resources.StringResource
import org.koin.core.scope.Scope

fun Scope.tokenBalanceViewModel(wallet: Wallet) = TokenBalanceViewModel(
    totalBalance = TotalBalance(TotalService(get(), get(), get(), get()), get()),
    wallet = wallet,
    balanceService = TokenBalanceService(
        wallet = wallet,
        xRateRepository = DefaultBalanceXRateRepository("wallet", get(), get()),
        balanceAdapterRepository = get(),
    ),
    balanceViewItemFactory = get(),
    transactionsService = TokenTransactionsService(
        wallet = wallet,
        rateRepository = get(),
        transactionSyncStateRepository = get(),
        transactionAdapterManager = get(),
        spamManager = get(),
    ),
    transactionViewItem2Factory = get(),
    balanceHiddenManager = get(),
    connectivityManager = get(),
    accountManager = get(),
    transactionHiddenManager = get(),
    premiumSettings = get(),
    amlStatusManager = get(),
    marketFavoritesManager = get(),
    stackingManager = get(),
    priceManager = get(),
    balanceSettings = get(),
    transactionSettings = get(),
    networkSettings = get(),
    numberFormatter = get(),
    contactsRepository = get(),
    offlineModeManager = get(),
)

class TokenBalanceModule {

    data class NetworkFeeWarningBannerData(
        val title: String,
        val body: String,
        val formattedBalance: String,
    )

    enum class StakingStatus { ACTIVE, INACTIVE }

    sealed interface Event {
        data class OpenSend(val wallet: Wallet) : Event
    }

    data class TokenBalanceUiState(
        val title: String,
        val coinCode: String = "",
        val badge: String? = null,
        val balanceViewItem: BalanceViewItem?,
        val transactions: Map<String, List<TransactionViewItem>>?,
        val hasHiddenTransactions: Boolean,
        val showAmlPromo: Boolean = false,
        val amlCheckEnabled: Boolean = false,
        val isFavorite: Boolean = false,
        val stakingStatus: StakingStatus? = null,
        val stackingType: StackingType? = null,
        val stakingUnpaid: String? = null,
        val hoursUntilNextAccrual: Int? = null,
        val isCustomToken: Boolean = false,
        val displayDiffPricePeriod: DisplayPricePeriod = DisplayPricePeriod.ONE_DAY,
        val displayDiffOptionType: DisplayDiffOptionType = DisplayDiffOptionType.BOTH,
        val isRoundingAmount: Boolean = false,
        val isShowShieldFunds: Boolean = false,
        val zcashMigrationRequiredAmount: String? = null,
        val networkFeeWarning: NetworkFeeWarningBannerData? = null,
        val syncing: Boolean = true,
        val transactionsLoadFailed: Boolean = false,
        val transactionFiltersEnabled: Boolean = false,
        val transactionFilterTypes: List<Filter<FilterTransactionType>> = emptyList(),
        val searchActive: Boolean = false,
        val searchQuery: String = "",
        val searchScanning: Boolean = false,
        val searchEmptyResult: Boolean = false,
        val offlineSince: Long? = null,
        val moneroHardwareWallet: Boolean = false,
        val moneroSpendReadiness: MoneroSpendReadiness? = null,
        val moneroKeyImageSyncInProgress: Boolean = false,
        val moneroKeyImageSyncError: StringResource? = null,
        val moneroFullWalletRecoveryAvailable: Boolean = false,
        val backupRequired: Boolean = false,
    ) {
        val moneroKeyImageSyncRequired: Boolean
            get() =
                moneroHardwareWallet &&
                    (moneroSpendReadiness == MoneroSpendReadiness.NeedsKeyImageSync ||
                        moneroSpendReadiness == MoneroSpendReadiness.ReconciliationFailed)

        val sendEntryEnabled: Boolean
            get() = balanceViewItem?.sendAvailability?.clickable == true || moneroKeyImageSyncRequired
    }
}
