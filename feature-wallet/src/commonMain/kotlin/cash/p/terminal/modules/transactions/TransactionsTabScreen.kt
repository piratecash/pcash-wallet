package cash.p.terminal.modules.transactions

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Search
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_WaitForSync
import cash.p.terminal.resources.Transactions_EmptyList
import cash.p.terminal.resources.Transactions_Title
import cash.p.terminal.resources.offline_signed_empty
import cash.p.terminal.resources.offline_signed_title
import cash.p.terminal.resources.hide_balance
import cash.p.terminal.resources.aml_promo_dismiss_hud
import cash.p.terminal.resources.ic_clock
import cash.p.terminal.resources.ic_outgoingraw
import cash.p.terminal.resources.ic_eye_off
import cash.p.terminal.resources.ic_eye_20
import cash.p.terminal.resources.Transactions_Filter
import cash.p.terminal.resources.ic_manage_2_24
import cash.p.terminal.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.core.rememberPremiumToggle
import cash.p.terminal.modules.balance.BalanceAccountsViewModel
import cash.p.terminal.modules.balance.BalanceScreenState
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.send.offline.OfflineSignedTransactionViewItem
import cash.p.terminal.modules.send.offline.OfflineSignedTransactionsUiState
import cash.p.terminal.modules.send.offline.OfflineSignedTransactionsViewModel
import cash.p.terminal.modules.transactionInfo.TransactionInfoPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun TransactionsScreen(
    navigation: HSNavigation,
    paddingValues: PaddingValues,
    viewModel: TransactionsViewModel,
    onShowAllTransactionsClicked: () -> Unit
) {
    val accountsViewModel = koinViewModel<BalanceAccountsViewModel>()

    val filterTypes by viewModel.filterTypesFlow.collectAsStateWithLifecycle()
    val showFilterAlertDot by viewModel.filterResetEnabled.collectAsStateWithLifecycle()

    val offlineSignedViewModel: OfflineSignedTransactionsViewModel = koinViewModel()
    val offlineSignedState = offlineSignedViewModel.uiState

    val uiState = viewModel.uiState
    val syncing = uiState.syncing
    val transactions = uiState.transactions
    val accountViewItemId =
        (accountsViewModel.balanceScreenState as? BalanceScreenState.HasAccount)?.accountViewItem?.id

    var offlineSignedSelected by rememberSaveable { mutableStateOf(false) }
    var showAmlInfoSheet by remember { mutableStateOf(false) }
    val hud = LocalHudMessenger.current
    val amlDismissedMessage = stringResource(Res.string.aml_promo_dismiss_hud)
    val setAmlCheckEnabled = navigation.rememberPremiumToggle(viewModel::setAmlCheckEnabled)

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            TransactionsAppBar(
                uiState = uiState,
                syncing = syncing,
                showFilterAlertDot = showFilterAlertDot,
                onSearchClick = viewModel::onSearchClick,
                onSearchClose = viewModel::onSearchClose,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onFilterClick = {
                    navigation.slideFromRight(TransactionsFilterPage())
                },
            )
            filterTypes?.let { filterTypes ->
                FilterTypeTabs(
                    filterTypes = filterTypes,
                    offlineSignedSelected = offlineSignedSelected,
                    onTransactionTypeClick = {
                        offlineSignedSelected = false
                        viewModel.setFilterTransactionType(it)
                    },
                    onOfflineSignedClick = {
                        offlineSignedSelected = true
                    },
                )
            }

            if (offlineSignedSelected) {
                OfflineSignedTransactionsTabContent(
                    uiState = offlineSignedState,
                    convertToViewItem = viewModel::convertToViewItem,
                    balanceHidden = uiState.balanceHidden,
                    accountViewItemId = accountViewItemId,
                    onItemClick = { item ->
                        onOfflineSignedTransactionClick(
                            item = item,
                            transactionsViewModel = viewModel,
                            navigation = navigation,
                        )
                    },
                    onToggleBalanceVisibility = {
                        hud.vibrate()
                        viewModel.toggleBalanceHidden()
                    },
                )
            } else {
                Crossfade(uiState.viewState, label = "") { viewState ->
                    when (viewState) {
                        ViewState.Loading -> {
                            ListEmptyView(
                                text = stringResource(Res.string.Transactions_WaitForSync),
                                icon = Res.drawable.ic_clock
                            )
                        }

                        ViewState.Success -> {
                            transactions?.let { transactionItems ->
                                if (transactionItems.isEmpty() && !uiState.hasHiddenTransactions) {
                                    if (uiState.searchQuery.isNotBlank()) {
                                        if (uiState.searchScanning) {
                                            SearchInProgressView()
                                        } else {
                                            SearchEmptyResultsView()
                                        }
                                    } else if (uiState.syncing) {
                                        ListEmptyView(
                                            text = stringResource(Res.string.Transactions_WaitForSync),
                                            icon = Res.drawable.ic_clock
                                        )
                                    } else {
                                        ListEmptyView(
                                            text = stringResource(Res.string.Transactions_EmptyList),
                                            icon = Res.drawable.ic_outgoingraw
                                        )
                                    }
                                } else {
                                    TransactionListContent(
                                        uiState = uiState,
                                        accountViewItemId = accountViewItemId,
                                        onClickTransaction = { onTransactionClick(it, viewModel, navigation) },
                                        onClickSensitiveValue = {
                                            hud.vibrate()
                                            viewModel.toggleTransactionInfoHidden(it.uid)
                                        },
                                        onToggleBalanceVisibility = {
                                            hud.vibrate()
                                            viewModel.toggleBalanceHidden()
                                        },
                                        willShow = viewModel::willShow,
                                        onReachBottom = viewModel::onBottomReached,
                                        onClickShowAll = onShowAllTransactionsClicked
                                    ) {
                                        AmlCheckPromoBanner(
                                            amlCheckEnabled = uiState.amlCheckEnabled,
                                            onToggleChange = setAmlCheckEnabled,
                                            onInfoClick = { showAmlInfoSheet = true },
                                            onClose = {
                                                viewModel.dismissAmlPromo()
                                                hud.showPremium(amlDismissedMessage, SnackbarDuration.LONG)
                                            },
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        is ViewState.Error -> {
                            // Show error state if needed
                        }
                    }
                }
            }
        }
    }

    if (showAmlInfoSheet) {
        AmlCheckInfoBottomSheet(
            onPremiumSettingsClick = {
                showAmlInfoSheet = false
                navigation.slideFromRight(PremiumSettingsPage())
            },
            onLaterClick = { showAmlInfoSheet = false },
            onDismiss = { showAmlInfoSheet = false }
        )
    }
}

@Composable
private fun TransactionsAppBar(
    uiState: TransactionsUiState,
    syncing: Boolean,
    showFilterAlertDot: Boolean,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
) {
    if (uiState.searchActive) {
        AppBar(
            title = {
                TransactionSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                )
            },
            navigationIcon = {
                HsBackButton(onClick = onSearchClose)
            },
            showSpinner = false
        )
    } else {
        AppBar(
            title = stringResource(Res.string.Transactions_Title),
            showSpinner = syncing,
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(Res.string.Button_Search),
                    icon = Res.drawable.ic_search,
                    onClick = onSearchClick,
                ),
                MenuItem(
                    title = TranslatableString.ResString(Res.string.Transactions_Filter),
                    icon = Res.drawable.ic_manage_2_24,
                    showAlertDot = showFilterAlertDot,
                    onClick = onFilterClick,
                )
            )
        )
    }
}

private fun onTransactionClick(
    transactionViewItem: TransactionViewItem,
    viewModel: TransactionsViewModel,
    navigation: HSNavigation
) {
    val transactionItem = viewModel.getTransactionItem(transactionViewItem) ?: return

    viewModel.tmpItemToShow = transactionItem

    navigation.slideFromBottom(TransactionInfoPage())
}

private fun onOfflineSignedTransactionClick(
    item: OfflineSignedTransactionViewItem,
    transactionsViewModel: TransactionsViewModel,
    navigation: HSNavigation,
) {
    transactionsViewModel.tmpItemToShow = item.transactionItem
    navigation.slideFromBottom(TransactionInfoPage())
}

@Composable
private fun OfflineSignedTransactionsTabContent(
    uiState: OfflineSignedTransactionsUiState,
    convertToViewItem: suspend (TransactionItem) -> TransactionViewItem,
    balanceHidden: Boolean,
    accountViewItemId: String?,
    onItemClick: (OfflineSignedTransactionViewItem) -> Unit,
    onToggleBalanceVisibility: () -> Unit,
) {
    if (uiState.items.isEmpty()) {
        ListEmptyView(
            text = stringResource(Res.string.offline_signed_empty),
            icon = Res.drawable.ic_outgoingraw,
        )
        return
    }

    val itemByUid = remember(uiState.items) {
        uiState.items.associateBy { it.uid }
    }
    val signedTitle = stringResource(Res.string.offline_signed_title)
    val currentConvertToViewItem by rememberUpdatedState(convertToViewItem)
    val transactionItems by produceState(
        initialValue = emptyMap<String, List<TransactionViewItem>>(),
        uiState.items,
        signedTitle,
        balanceHidden,
    ) {
        value = uiState.items.map { item ->
            val viewItem = currentConvertToViewItem(item.transactionItem)
            viewItem.copy(
                progress = null,
                title = signedTitle,
                subtitle = if (item.metadataUnknown) UNKNOWN_VALUE else viewItem.subtitle,
                primaryValue = if (item.metadataUnknown) {
                    ColoredValue(UNKNOWN_VALUE, ColorName.Secondary)
                } else {
                    viewItem.primaryValue
                },
                secondaryValue = if (item.metadataUnknown) null else viewItem.secondaryValue,
                showAmount = !balanceHidden,
                addressPoisoningViewMode = AddressPoisoningViewMode.STANDARD,
                statusValue = item.statusValue,
            )
        }.groupBy { it.formattedDate }
    }

    TransactionListContent(
        uiState = TransactionsUiState(
            transactions = transactionItems,
            viewState = ViewState.Success,
            transactionListId = OFFLINE_SIGNED_TRANSACTION_LIST_ID,
            syncing = false,
            hasHiddenTransactions = false,
            balanceHidden = balanceHidden,
        ),
        accountViewItemId = accountViewItemId,
        onClickTransaction = { viewItem ->
            itemByUid[viewItem.uid]?.let(onItemClick)
        },
        onClickSensitiveValue = {},
        onToggleBalanceVisibility = onToggleBalanceVisibility,
        willShow = {},
        onReachBottom = {},
    )
}

@Composable
private fun TransactionListContent(
    uiState: TransactionsUiState,
    onClickTransaction: (TransactionViewItem) -> Unit,
    onClickSensitiveValue: (TransactionViewItem) -> Unit,
    onToggleBalanceVisibility: () -> Unit,
    willShow: (TransactionViewItem) -> Unit,
    onReachBottom: () -> Unit,
    accountViewItemId: String? = null,
    onClickShowAll: () -> Unit = {},
    amlPromoContent: @Composable () -> Unit = {}
) {
    val transactionItems = uiState.transactions ?: return
    val showAmlPromo = uiState.showAmlPromo
    val listState = rememberSaveable(
        uiState.transactionListId, accountViewItemId, saver = LazyListState.Saver
    ) { LazyListState(0, 0) }

    val currentStickyDate by remember(listState, transactionItems, showAmlPromo) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = if (showAmlPromo) 1 else 0
            if (firstVisible < offset) return@derivedStateOf null
            var idx = offset
            var lastDate: String? = null
            for ((date, txs) in transactionItems) {
                lastDate = date
                if (firstVisible < idx + 1 + txs.size + 1) return@derivedStateOf date
                idx += 1 + txs.size + 1
            }
            lastDate
        }
    }

    Box {
        LazyColumn(state = listState, contentPadding = PaddingValues(top = 44.dp)) {
            if (showAmlPromo) { item { amlPromoContent() } }
            transactionList(
                transactionsMap = transactionItems, willShow = willShow,
                isItemBalanceHidden = { !it.showAmount },
                onSensitiveValueClick = onClickSensitiveValue,
                onClick = onClickTransaction, onBottomReached = onReachBottom
            )
            if (uiState.hasHiddenTransactions) {
                transactionsHiddenBlock(transactionItems.isNotEmpty(), onClickShowAll)
            }
        }
        HideBalanceOverlay(
            currentStickyDate, uiState.balanceHidden,
            onToggleBalanceVisibility, Modifier.align(Alignment.TopStart)
        )
    }
}


@Composable
private fun HideBalanceOverlay(
    dateHeader: String?,
    balanceHidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dateHeader != null) {
                subhead1_grey(
                    text = dateHeader,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                subhead2_grey(
                    text = stringResource(Res.string.hide_balance),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    painter = painterResource(
                        if (balanceHidden) Res.drawable.ic_eye_off
                        else Res.drawable.ic_eye_20
                    ),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider,
        )
    }
}

private const val OFFLINE_SIGNED_TRANSACTION_LIST_ID = "offline-signed"
private const val UNKNOWN_VALUE = "---"
