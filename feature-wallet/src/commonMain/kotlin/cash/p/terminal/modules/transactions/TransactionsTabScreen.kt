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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import cash.p.terminal.resources.ic_calendar_24
import cash.p.terminal.resources.transactions_calendar_period_selected
import cash.p.terminal.resources.transactions_calendar_title
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
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
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
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TransactionsScreen(
    navigation: HSNavigation,
    paddingValues: PaddingValues,
    viewModel: TransactionsViewModel,
    onShowAllTransactionsClicked: () -> Unit,
    onHiddenCalendarClick: () -> Unit,
    onHiddenSearchClick: () -> Unit,
) {
    val accountsViewModel = koinViewModel<BalanceAccountsViewModel>()

    val filterTypes by viewModel.filterTypesFlow.collectAsStateWithLifecycle()
    val showFilterAlertDot by viewModel.filterResetEnabled.collectAsStateWithLifecycle()

    val offlineSignedViewModel: OfflineSignedTransactionsViewModel = koinViewModel()
    val offlineSignedState = offlineSignedViewModel.uiState

    val uiState = viewModel.uiState
    val transactions = uiState.transactions
    val accountViewItemId =
        (accountsViewModel.balanceScreenState as? BalanceScreenState.HasAccount)?.accountViewItem?.id

    var offlineSignedSelected by rememberSaveable { mutableStateOf(false) }
    var showAmlInfoSheet by remember { mutableStateOf(false) }
    val hud = LocalHudMessenger.current
    val amlDismissedMessage = stringResource(Res.string.aml_promo_dismiss_hud)
    val setAmlCheckEnabled = navigation.rememberPremiumToggle(viewModel::setAmlCheckEnabled)
    // Hidden transactions are revealed by PIN first, then the calendar or the search opens.
    val onCalendarClick = if (uiState.transactionsHidden) onHiddenCalendarClick else viewModel::onCalendarClick
    val onSearchClick = if (uiState.transactionsHidden) onHiddenSearchClick else viewModel::onSearchClick
    val selectedType = filterTypes?.firstOrNull { it.selected }?.item ?: FilterTransactionType.All

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            TransactionsAppBar(
                uiState = uiState,
                showFilterAlertDot = showFilterAlertDot,
                // The offline-signed tab is not an on-chain history; the period stays applied to the others.
                onCalendarClick = onCalendarClick.takeUnless { offlineSignedSelected },
                onSearchClick = onSearchClick,
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
                AppliedPeriodBar(
                    period = uiState.period,
                    onClick = viewModel::onCalendarClick,
                    onBackToLatest = viewModel::onBackToLatest,
                )
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
                                    TransactionsEmptyContent(
                                        uiState = uiState,
                                        tab = selectedType,
                                        onSearchFurther = viewModel::searchFurther,
                                        onChangePeriod = viewModel::onCalendarClick,
                                    )
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
                                        onClickShowAll = onShowAllTransactionsClicked,
                                        onSearchFurther = viewModel::searchFurther,
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

    TransactionCalendarBottomSheet(
        period = uiState.period,
        onApply = viewModel::onPeriodSelect,
        onDismiss = viewModel::onCalendarDismiss,
    )
}

/** Hosts [TransactionCalendarSheet] while [TransactionPeriodUiState.sheetVisible]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionCalendarBottomSheet(
    period: TransactionPeriodUiState,
    onApply: (TransactionPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!period.sheetVisible) return
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    TransparentModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TransactionCalendarSheet(
            today = LocalDate.now(),
            appliedPeriod = period.period,
            onApply = { selected -> hideThen { onApply(selected) } },
            onClose = { hideThen(onDismiss) },
        )
    }
}

@Composable
internal fun AppliedPeriodBar(
    period: TransactionPeriodUiState,
    onClick: () -> Unit,
    onBackToLatest: () -> Unit,
) {
    val applied = period.period ?: return
    TransactionPeriodBar(
        text = transactionPeriodBarText(applied, period.oldestDate),
        onClick = onClick,
        onBackToLatest = onBackToLatest,
    )
}

@Composable
private fun TransactionsEmptyContent(
    uiState: TransactionsUiState,
    tab: FilterTransactionType,
    onSearchFurther: () -> Unit,
    onChangePeriod: () -> Unit,
) {
    when {
        uiState.searchQuery.isNotBlank() -> SearchWithoutRowsView(uiState, onSearchFurther)
        uiState.syncing -> ListEmptyView(
            text = stringResource(Res.string.Transactions_WaitForSync),
            icon = Res.drawable.ic_clock
        )
        uiState.period.active -> TransactionPeriodEmptyView(tab = tab, onChangePeriod = onChangePeriod)
        else -> ListEmptyView(
            text = stringResource(Res.string.Transactions_EmptyList),
            icon = Res.drawable.ic_outgoingraw
        )
    }
}

/** A null [onCalendarClick] hides the calendar. */
@Composable
private fun TransactionsAppBar(
    uiState: TransactionsUiState,
    showFilterAlertDot: Boolean,
    onCalendarClick: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterClick: () -> Unit,
) {
    val calendarItems = listOfNotNull(onCalendarClick?.let { calendarMenuItem(uiState.period.active, it) })
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
            menuItems = calendarItems,
            showSpinner = false
        )
    } else {
        AppBar(
            title = stringResource(Res.string.Transactions_Title),
            showSpinner = uiState.syncing,
            menuItems = calendarItems + listOf(
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

private fun calendarMenuItem(active: Boolean, onClick: () -> Unit) = MenuItem(
    title = TranslatableString.ResString(
        if (active) Res.string.transactions_calendar_period_selected else Res.string.transactions_calendar_title
    ),
    icon = Res.drawable.ic_calendar_24,
    showAlertDot = active,
    onClick = onClick,
)

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
private fun SearchWithoutRowsView(uiState: TransactionsUiState, onSearchFurther: () -> Unit) {
    val pausedCount = uiState.searchPausedCount
    when {
        uiState.searchScanning -> SearchInProgressView()
        pausedCount != null -> SearchPausedView(
            scannedCount = pausedCount,
            hasResults = false,
            inPeriod = uiState.period.active,
            onSearchFurther = onSearchFurther,
        )
        else -> SearchEmptyResultsView()
    }
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
    onSearchFurther: () -> Unit = {},
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
            uiState.searchPausedCount?.let { count ->
                item {
                    SearchPausedView(
                        scannedCount = count,
                        hasResults = true,
                        inPeriod = uiState.period.active,
                        onSearchFurther = onSearchFurther,
                    )
                }
            }
            if (uiState.period.showsEndFooter) {
                item { TransactionPeriodEndFooter() }
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

private val PreviewPeriod = TransactionPeriodUiState(
    period = TransactionPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 28)),
)

private fun previewUiState(
    period: TransactionPeriodUiState = TransactionPeriodUiState(),
    searchActive: Boolean = false,
) =
    TransactionsUiState(
        transactions = emptyMap(),
        viewState = ViewState.Success,
        transactionListId = null,
        syncing = false,
        hasHiddenTransactions = false,
        searchActive = searchActive,
        searchQuery = if (searchActive) "0x7a2f" else "",
        period = period,
    )

@Composable
private fun TransactionsHeaderPreview(uiState: TransactionsUiState, tab: FilterTransactionType? = null) {
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            TransactionsAppBar(
                uiState = uiState,
                showFilterAlertDot = false,
                onCalendarClick = {},
                onSearchClick = {},
                onSearchClose = {},
                onSearchQueryChange = {},
                onFilterClick = {},
            )
            AppliedPeriodBar(period = uiState.period, onClick = {}, onBackToLatest = {})
            tab?.let { TransactionPeriodEmptyView(tab = it, onChangePeriod = {}) }
        }
    }
}

@Preview
@Composable
private fun TransactionsAppBarPreview() {
    TransactionsHeaderPreview(previewUiState())
}

@Preview
@Composable
private fun TransactionsPeriodAppliedPreview() {
    TransactionsHeaderPreview(previewUiState(PreviewPeriod))
}

@Preview
@Composable
private fun TransactionsPeriodEmptyPreview() {
    TransactionsHeaderPreview(previewUiState(PreviewPeriod), tab = FilterTransactionType.Incoming)
}

@Preview
@Composable
private fun TransactionsSearchWithPeriodPreview() {
    TransactionsHeaderPreview(
        previewUiState(
            period = TransactionPeriodUiState(
                period = TransactionPeriod(null, LocalDate.of(2026, 11, 12)),
                oldestDate = LocalDate.of(2021, 3, 3),
            ),
            searchActive = true,
        )
    )
}
