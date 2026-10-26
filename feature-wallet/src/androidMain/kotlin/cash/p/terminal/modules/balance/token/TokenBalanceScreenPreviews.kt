package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.transactions.Filter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Swap
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.balance.DeemedValue
import java.util.Date

@Preview
@Composable
private fun TokenNotSyncedSectionPreview() {
    ComposeAppTheme {
        TokenNotSyncedSection(
            onBlockchainStatusClick = {},
            onRetry = {},
            showRetry = true,
        )
    }
}

@Preview
@Composable
private fun TokenNotSyncedSectionSyncingPreview() {
    ComposeAppTheme {
        TokenNotSyncedSection(
            onBlockchainStatusClick = {},
            onRetry = {},
            showRetry = false,
        )
    }
}

@Preview
@Composable
private fun TokenOfflineSectionPreview() {
    ComposeAppTheme {
        TokenOfflineSection(
            offlineSince = Date().time,
            onGoOnline = {},
        )
    }
}

@Preview
@Composable
private fun MoneroSendPreparationBottomSheetPreview() {
    ComposeAppTheme {
        MoneroSendPreparationBottomSheet(
            ledgerWallet = false,
            syncInProgress = false,
            error = null,
            fullWalletRecoveryAvailable = false,
            onSync = {},
            onFullWalletRecovery = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "With tabs")
@Composable
internal fun TokenBalanceScreenContentPreview() {
    PreviewTokenBalanceScreenContent(previewTokenBalanceUiState())
}

@Preview(name = "Without tabs")
@Composable
internal fun TokenBalanceScreenContentNoTabsPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(transactionFiltersEnabled = false)
    )
}

@Preview(name = "Search, with tabs")
@Composable
internal fun TokenBalanceScreenContentSearchWithTabsPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(searchActive = true)
    )
}

@Preview(name = "Search, without tabs")
@Composable
internal fun TokenBalanceScreenContentSearchNoTabsPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(searchActive = true, transactionFiltersEnabled = false)
    )
}

@Preview(name = "Transactions load failed")
@Composable
internal fun TokenBalanceScreenContentLoadFailedPreview() {
    PreviewTokenBalanceScreenContent(
        previewTokenBalanceUiState(transactionsLoadFailed = true)
    )
}

@Composable
private fun PreviewTokenBalanceScreenContent(
    uiState: TokenBalanceModule.TokenBalanceUiState
) {
    ComposeAppTheme(darkTheme = true) {
        TokenBalanceScreenContent(
            uiState = uiState,
            secondaryValue = DeemedValue("$1,234.56"),
            sendResult = null,
            navigation = HSNavigation(NavBackStack()),
            refreshing = false,
            onToggleFavorite = {},
            onToggleBalanceVisibility = {},
            onSearchClick = {},
            onSearchClose = {},
            onSearchQueryChange = {},
            onSetTransactionType = {},
            onWillShow = {},
            onTransactionClick = {},
            onSensitiveTransactionClick = {},
            onBottomReached = {},
            onSetAmlCheckEnabled = {},
            onDismissAmlPromo = {},
            onDismissNetworkFeeWarning = {},
            onSendClick = {},
            onReceiveClick = {},
            onSwapClick = {},
            onShieldClick = {},
            onSyncErrorClick = {},
            onStackingClicked = {},
            onShowAllTransactionsClicked = {},
            onClickSubtitle = {},
            onRefresh = {},
            onSettingsClick = {},
            onGoOnline = {},
        )
    }
}

private fun previewTokenBalanceUiState(
    transactionFiltersEnabled: Boolean = true,
    searchActive: Boolean = false,
    transactionsLoadFailed: Boolean = false,
) = TokenBalanceModule.TokenBalanceUiState(
    title = "Preview Coin",
    coinCode = "PCN",
    balanceViewItem = previewBalanceViewItem(),
    transactions = emptyMap(),
    hasHiddenTransactions = false,
    isFavorite = true,
    syncing = false,
    transactionFiltersEnabled = transactionFiltersEnabled,
    transactionFilterTypes = listOf(
        Filter(FilterTransactionType.All, selected = true),
        Filter(FilterTransactionType.Incoming, selected = false),
        Filter(FilterTransactionType.Outgoing, selected = false),
        Filter(FilterTransactionType.Swap, selected = false),
    ),
    searchActive = searchActive,
    searchQuery = if (searchActive) "0x1f9840" else "",
    searchEmptyResult = searchActive,
    transactionsLoadFailed = transactionsLoadFailed,
)

private fun previewBalanceViewItem() = BalanceViewItem(
    wallet = WalletFactory.previewWallet(),
    primaryValue = DeemedValue("1.2345 PCN"),
    exchangeValue = DeemedValue("$1,000.00", visible = false),
    secondaryValue = DeemedValue("$1,234.56"),
    lockedValues = emptyList(),
    sendAvailability = OperationAvailability.Available,
    syncingProgress = SyncingProgress(null, null),
    syncingTextValue = null,
    syncedUntilTextValue = null,
    failedIconVisible = false,
    coinIconVisible = true,
    badge = null,
    swapVisible = true,
    swapAvailability = OperationAvailability.Available,
    errorMessage = null,
    isWatchAccount = false,
    warning = null,
    displayDiffOptionType = DisplayDiffOptionType.NONE,
)
