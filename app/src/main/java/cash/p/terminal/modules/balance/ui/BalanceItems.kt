package cash.p.terminal.modules.balance.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavController
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.managers.FaqManager
import cash.p.terminal.core.usecase.PayCoreNavigationTarget
import cash.p.terminal.modules.balance.AccountViewItem
import cash.p.terminal.modules.balance.BalanceUiState
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.HeaderNote
import cash.p.terminal.modules.balance.ReceiveAllowedState
import cash.p.terminal.modules.balance.TotalUIState
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesFragment
import cash.p.terminal.modules.rateapp.RateAppViewModel
import cash.p.terminal.modules.send.offline.OfflineBroadcastFragment
import cash.p.terminal.modules.sendtokenselect.SendTokenSelectFragment
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.components.BalanceActionButton
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.RowWithArrow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.Subhead1
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun NoteWarning(
    modifier: Modifier = Modifier,
    text: String,
    onClick: (() -> Unit),
    onClose: (() -> Unit)?
) {
    Note(
        modifier = modifier.clickable(onClick = onClick),
        text = text,
        title = stringResource(id = R.string.AccountRecovery_Note),
        icon = R.drawable.ic_attention_20,
        borderColor = ComposeAppTheme.colors.yellow,
        backgroundColor = ComposeAppTheme.colors.yellow20,
        textColor = ComposeAppTheme.colors.yellow,
        iconColor = ComposeAppTheme.colors.yellow,
        onClose = onClose
    )
}

@Composable
fun NoteError(
    modifier: Modifier = Modifier,
    text: String,
    onClick: (() -> Unit)
) {
    Note(
        modifier = modifier.clickable(onClick = onClick),
        text = text,
        title = stringResource(id = R.string.AccountRecovery_Note),
        icon = R.drawable.ic_attention_20,
        borderColor = ComposeAppTheme.colors.lucian,
        backgroundColor = ComposeAppTheme.colors.red20,
        textColor = ComposeAppTheme.colors.lucian,
        iconColor = ComposeAppTheme.colors.lucian
    )
}

@Composable
fun Note(
    modifier: Modifier = Modifier,
    text: String,
    title: String,
    @DrawableRes icon: Int,
    iconColor: Color,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    onClose: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconColor
            )
            Subhead1(
                modifier = Modifier.weight(1f),
                text = title,
                color = textColor,
            )
            onClose?.let {
                HsIconButton(
                    modifier = Modifier.size(20.dp),
                    onClick = onClose
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_24),
                        tint = iconColor,
                        contentDescription = null,
                    )
                }
            }
        }
        if (text.isNotEmpty()) {
            subhead2_leah(text = text)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BalanceItems(
    balanceViewItems: List<BalanceViewItem2>,
    viewModel: BalanceViewModel,
    onItemClick: (BalanceViewItem2) -> Unit,
    onBalanceClick: (BalanceViewItem2) -> Unit,
    accountViewItem: AccountViewItem,
    navController: NavController,
    uiState: BalanceUiState,
    totalState: TotalUIState,
    onOpenTransactionInfo: (TransactionItem) -> Unit,
) {
    val rateAppViewModel = koinViewModel<RateAppViewModel>()
    DisposableEffect(true) {
        rateAppViewModel.onBalancePageActive()
        onDispose {
            rateAppViewModel.onBalancePageInactive()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.payCoreNavigationEvents.collect { event ->
            when (event) {
                is PayCoreNavigationTarget.OpenTransactionInfo ->
                    onOpenTransactionInfo(event.transactionItem)
                is PayCoreNavigationTarget.OpenPayCoreDetail ->
                    navController.slideFromRight(
                        R.id.multiSwapExchanges,
                        MultiSwapExchangesFragment.ARG_PAYCORE_DATE to event.date,
                    )
            }
        }
    }

    val context = LocalContext.current
    val view = LocalView.current
    var revealedCardId by remember { mutableStateOf<Int?>(null) }

    val onClickSyncError: (BalanceViewItem2) -> Unit = remember {
        {
            onSyncErrorClicked(
                it,
                viewModel,
                navController,
                view
            )
        }
    }

    val onDisable: (BalanceViewItem2) -> Unit = remember {
        {
            viewModel.disable(it)
        }
    }

    HSSwipeRefresh(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::onRefresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberSaveable(
                accountViewItem.id,
                uiState.sortType,
                saver = LazyListState.Saver
            ) {
                LazyListState()
            }
        ) {
            item {
                val standardActionsVisible =
                    uiState.balanceTabButtonsEnabled && !accountViewItem.isWatchAccount
                val watchStakingVisible =
                    uiState.showStackingForWatchAccount && accountViewItem.isWatchAccount
                BalanceSummary(
                    modifier = Modifier.padding(vertical = 12.dp),
                    totalState = totalState,
                    onToggleVisibility = remember {
                        {
                            viewModel.toggleBalanceVisibility()
                            HudHelper.vibrate(context)
                        }
                    },
                    onToggleTotalType = remember {
                        {
                            viewModel.toggleTotalType()
                            HudHelper.vibrate(context)
                        }
                    },
                    actions = when {
                        standardActionsVisible -> {
                            {
                                BalanceActionButton(
                                    icon = R.drawable.ic_arrow_up_right_24,
                                    label = stringResource(R.string.Balance_Send),
                                    iconRotation = -90f,
                                    onClick = {
                                        navController.slideFromRight(R.id.sendTokenSelectFragment)
                                    },
                                )
                                BalanceActionButton(
                                    icon = R.drawable.ic_arrow_down_left_24,
                                    label = stringResource(R.string.Balance_Receive),
                                    onClick = {
                                        when (val receiveAllowedState =
                                            viewModel.getReceiveAllowedState()) {
                                            ReceiveAllowedState.Allowed -> {
                                                viewModel.getSingleWalletForReceive()
                                                navController.slideFromRight(
                                                    R.id.receiveChooseCoinFragment
                                                )
                                            }

                                            is ReceiveAllowedState.BackupRequired -> {
                                                val account = receiveAllowedState.account
                                                val text =
                                                    cash.p.terminal.strings.helpers.Translator.getString(
                                                        R.string.Balance_Receive_BackupRequired_Description,
                                                        account.name
                                                    )
                                                navController.slideFromBottom(
                                                    R.id.backupRequiredDialog,
                                                    BackupRequiredDialog.Input(account, text)
                                                )
                                            }

                                            null -> Unit
                                        }
                                    },
                                )
                                if (viewModel.isSwapEnabled) {
                                    BalanceActionButton(
                                        icon = R.drawable.ic_swap_24,
                                        label = stringResource(R.string.Swap),
                                        onClick = {
                                            navController.slideFromRight(R.id.multiswap)
                                        },
                                    )
                                }
                                if (viewModel.isStackingEnabled) {
                                    BalanceActionButton(
                                        icon = R.drawable.ic_coins_stacking,
                                        label = stringResource(R.string.stacking),
                                        onClick = {
                                            navController.slideFromRight(R.id.stacking)
                                        },
                                    )
                                }
                            }
                        }

                        watchStakingVisible -> {
                            {
                                BalanceActionButton(
                                    icon = R.drawable.ic_coins_stacking,
                                    label = stringResource(R.string.staking_details),
                                    onClick = {
                                        navController.slideFromRight(R.id.stacking)
                                    },
                                )
                            }
                        }

                        else -> null
                    },
                )
            }

            stickyHeader {
                BalanceFilters(
                    sort = Select(uiState.sortType, uiState.sortTypes),
                    displayDiffOptionType = uiState.displayDiffOptionType,
                    displayPricePeriod = uiState.displayPricePeriod,
                    isWatchAccount = accountViewItem.isWatchAccount,
                    onSelectSortType = viewModel::setSortType,
                    onDisplayPricePeriod = viewModel::setDisplayPricePeriod,
                    onSettingsClick = {
                        navController.slideFromBottom(R.id.displayOptionsFragment)
                    },
                )
            }

            item {
                when (uiState.headerNote) {
                    HeaderNote.None -> Unit
                    HeaderNote.NonStandardAccount -> {
                        NoteError(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 24.dp
                            ),
                            text = stringResource(R.string.AccountRecovery_MigrationRequired),
                            onClick = {
                                FaqManager.showFaqPage(FaqManager.faqMigrationRequired)
                            }
                        )
                    }

                    HeaderNote.NonRecommendedAccount -> {
                        NoteWarning(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 24.dp
                            ),
                            text = stringResource(R.string.AccountRecovery_MigrationRecommended),
                            onClick = {
                                FaqManager.showFaqPage(FaqManager.faqMigrationRecommended)
                            },
                            onClose = {
                                viewModel.onCloseHeaderNote(HeaderNote.NonRecommendedAccount)
                            }
                        )
                    }
                }
            }

            if (uiState.pendingSwapCount > 0) {
                item {
                    PendingSwapBanner(
                        count = uiState.pendingSwapCount,
                        modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp),
                        showSpinner = uiState.singlePayCoreSwapLoading,
                        onClick = {
                            if (uiState.pendingSwapCount == 1) {
                                val payCoreDate = uiState.singlePayCoreSwapDate
                                val swapId = uiState.singlePendingSwapId
                                when {
                                    payCoreDate != null -> viewModel.onSinglePayCoreSwapClick()
                                    swapId != null -> navController.slideFromRight(
                                        R.id.multiSwapExchanges,
                                        MultiSwapExchangesFragment.ARG_PENDING_MULTI_SWAP_ID to swapId,
                                    )
                                    else -> navController.slideFromRight(R.id.multiSwapExchanges)
                                }
                            } else {
                                navController.slideFromRight(R.id.multiSwapExchanges)
                            }
                        }
                    )
                }
            }

            if (balanceViewItems.isEmpty()) {
                item {
                    NoCoinsBlock()
                }
            } else {
                wallets(
                    items = balanceViewItems,
                    key = {
                        it.wallet.token.tokenQuery.id
                    }
                ) { item ->
                    BalanceCardSwipable(
                        viewItem = item,
                        revealed = revealedCardId == item.wallet.hashCode(),
                        onReveal = { walletHashCode ->
                            if (revealedCardId != walletHashCode) {
                                revealedCardId = walletHashCode
                            }
                        },
                        onConceal = {
                            revealedCardId = null
                        },
                        onClick = {
                            onItemClick(item)
                        },
                        onBalanceClick = {
                            onBalanceClick(item)
                        },
                        onClickSyncError = {
                            onClickSyncError.invoke(item)
                        },
                        onDisable = {
                            onDisable.invoke(item)
                            revealedCardId = null
                        }
                    )
                }
            }
        }
    }
    uiState.openSend?.let { openSend ->
        navController.slideFromRight(
            R.id.sendTokenSelectFragment,
            SendTokenSelectFragment.Input(
                openSend.blockchainTypes,
                openSend.tokenTypes,
                openSend.prefilledData
            )
        )
        viewModel.onSendOpened()
    }
    uiState.openRestoreFromQr?.let { restore ->
        navController.slideFromRight(
            R.id.restoreAccountFragment,
            ManageAccountsModule.Input(
                popOffOnSuccess = R.id.mainFragment,
                popOffInclusive = false,
                mnemonicDraft = restore.draft
            )
        )
        viewModel.onRestoreFromQrOpened()
    }
    uiState.openOfflineBroadcast?.let { input ->
        navController.slideFromRight(
            MainGraphDirections.actionGlobalToOfflineBroadcastFragment(
                OfflineBroadcastFragment.Input(initialInput = input)
            )
        )
        viewModel.onOfflineBroadcastOpened()
    }
}

@Composable
private fun NoCoinsBlock() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer(height = 100.dp)
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = ComposeAppTheme.colors.raina,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(R.drawable.ic_empty_wallet),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey
            )
        }
        VSpacer(32.dp)
        subhead2_grey(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.Balance_NoCoinsAlert),
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        VSpacer(height = 32.dp)
    }
}

@Composable
private fun PendingSwapBanner(
    count: Int,
    modifier: Modifier,
    showSpinner: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, ComposeAppTheme.colors.grey, shape)
    ) {
        RowWithArrow(
            text = if (count == 1) {
                stringResource(R.string.multi_swap_unfinished)
            } else {
                stringResource(R.string.multi_swap_unfinished_plural, count)
            },
            showSpinner = showSpinner,
            onClick = onClick
        )
    }
}

fun <T> LazyListScope.wallets(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable (LazyItemScope.(item: T) -> Unit),
) {
    items(items = items, key = key, itemContent = itemContent)
}
