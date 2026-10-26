package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.usecase.PayCoreNavigationTarget
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.featureStacking.ui.staking.StackingPage
import cash.p.terminal.modules.balance.AccountViewItem
import cash.p.terminal.modules.balance.BackupRequirementState
import cash.p.terminal.modules.balance.BalanceUiState
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.balance.BalancePlatformActions
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.HeaderNote
import cash.p.terminal.modules.balance.TotalUIState
import cash.p.terminal.modules.displayoptions.DisplayOptionsPage
import cash.p.terminal.modules.manageaccount.dialogs.showBackupRequiredDialog
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.receive.ui.ReceiveChooseCoinPage
import cash.p.terminal.modules.restoreaccount.RestoreAccountPage
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.AccountRecovery_MigrationRecommended
import cash.p.terminal.resources.AccountRecovery_MigrationRequired
import cash.p.terminal.resources.Balance_NoCoinsAlert
import cash.p.terminal.resources.Balance_Receive
import cash.p.terminal.resources.Balance_Receive_BackupRequired_Description
import cash.p.terminal.resources.Balance_Send
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap
import cash.p.terminal.resources.balance_swap_backup_required_description
import cash.p.terminal.resources.ic_arrow_down_left_24
import cash.p.terminal.resources.ic_arrow_up_right_24
import cash.p.terminal.resources.ic_coins_stacking
import cash.p.terminal.resources.ic_empty_wallet
import cash.p.terminal.resources.ic_swap_24
import cash.p.terminal.resources.multi_swap_unfinished
import cash.p.terminal.resources.multi_swap_unfinished_plural
import cash.p.terminal.resources.stacking
import cash.p.terminal.resources.staking_details
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.BalanceActionButton
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.RowWithArrow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.openOrNotifyUnsupported
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.showNotSupportedOnPlatform
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.policy.PlatformOperation
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BalanceItems(
    balanceViewItems: List<BalanceViewItem2>,
    viewModel: BalanceViewModel,
    onItemClick: (BalanceViewItem2) -> Unit,
    onBalanceClick: (BalanceViewItem2) -> Unit,
    accountViewItem: AccountViewItem,
    navigation: HSNavigation,
    uiState: BalanceUiState,
    totalState: TotalUIState,
    onOpenTransactionInfo: (TransactionItem) -> Unit,
) {
    val platformActions: BalancePlatformActions = koinInject()
    val pages: WalletSendPages = koinInject()
    val mainPage = koinInject<WalletPlatformPages>().mainPage
    val operationSupport = koinInject<PlatformOperationSupport>()
    val swapSupported = operationSupport.supports(PlatformOperation.Swap)
    val hud = rememberHudMessenger()
    DisposableEffect(true) {
        platformActions.onBalancePageActive()
        onDispose {
            platformActions.onBalancePageInactive()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.payCoreNavigationEvents.collect { event ->
            when (event) {
                is PayCoreNavigationTarget.OpenTransactionInfo ->
                    onOpenTransactionInfo(event.transactionItem)
                is PayCoreNavigationTarget.OpenPayCoreDetail ->
                    navigation.openOrNotifyUnsupported(pages.multiSwapExchanges(null, event.date), hud)
            }
        }
    }

    var revealedCardId by remember { mutableStateOf<Int?>(null) }

    val onClickSyncError: (BalanceViewItem2) -> Unit = remember {
        {
            onSyncErrorClicked(
                it,
                viewModel,
                navigation,
                hud
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
                            hud.vibrate()
                        }
                    },
                    onToggleTotalType = remember {
                        {
                            viewModel.toggleTotalType()
                            hud.vibrate()
                        }
                    },
                    actions = when {
                        standardActionsVisible -> {
                            {
                                BalanceActionButton(
                                    icon = Res.drawable.ic_arrow_up_right_24,
                                    label = stringResource(Res.string.Balance_Send),
                                    iconRotation = -90f,
                                    onClick = {
                                        navigation.openIfSupported(operationSupport, PlatformOperation.Send, hud) {
                                            pages.sendTokenSelect(null)
                                        }
                                    },
                                )
                                BalanceActionButton(
                                    icon = Res.drawable.ic_arrow_down_left_24,
                                    label = stringResource(Res.string.Balance_Receive),
                                    dimmed = viewModel.isBackupRequired,
                                    onClick = {
                                        navigation.openIfBackedUp(
                                            viewModel.getBackupRequirementState(),
                                            Res.string.Balance_Receive_BackupRequired_Description,
                                        ) {
                                            viewModel.getSingleWalletForReceive()
                                            navigation.slideFromRight(ReceiveChooseCoinPage())
                                        }
                                    },
                                )
                                if (viewModel.isSwapEnabled) {
                                    BalanceActionButton(
                                        icon = Res.drawable.ic_swap_24,
                                        label = stringResource(Res.string.Swap),
                                        dimmed = viewModel.isBackupRequired,
                                        onClick = {
                                            navigation.openSwap(
                                                operationSupport,
                                                viewModel.getBackupRequirementState(),
                                                hud,
                                            ) { pages.swapFrom(null) }
                                        },
                                    )
                                }
                                if (viewModel.isStackingEnabled) {
                                    BalanceActionButton(
                                        icon = Res.drawable.ic_coins_stacking,
                                        label = stringResource(Res.string.stacking),
                                        onClick = {
                                            navigation.slideFromRight(StackingPage(null))
                                        },
                                    )
                                }
                            }
                        }

                        watchStakingVisible -> {
                            {
                                BalanceActionButton(
                                    icon = Res.drawable.ic_coins_stacking,
                                    label = stringResource(Res.string.staking_details),
                                    onClick = {
                                        navigation.slideFromRight(StackingPage(null))
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
                        navigation.slideFromBottom(DisplayOptionsPage())
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
                            text = stringResource(Res.string.AccountRecovery_MigrationRequired),
                            onClick = {
                                navigation.openFaq(FaqTopic.MigrationRequired)
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
                            text = stringResource(Res.string.AccountRecovery_MigrationRecommended),
                            onClick = {
                                navigation.openFaq(FaqTopic.MigrationRecommended)
                            },
                            onClose = {
                                viewModel.onCloseHeaderNote(HeaderNote.NonRecommendedAccount)
                            }
                        )
                    }
                }
            }

            if (uiState.pendingSwapCount > 0 && swapSupported) {
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
                                    else -> navigation.openOrNotifyUnsupported(
                                        pages.multiSwapExchanges(swapId, null),
                                        hud,
                                    )
                                }
                            } else {
                                navigation.openOrNotifyUnsupported(pages.multiSwapExchanges(null, null), hud)
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
                            if (item.notSupportedOnPlatform) hud.showNotSupportedOnPlatform() else onItemClick(item)
                        },
                        onBalanceClick = {
                            hud.vibrate()
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
        navigation.openOrNotifyUnsupported(pages.sendTokenSelect(openSend), hud)
        viewModel.onSendOpened()
    }
    uiState.openRestoreFromQr?.let { restore ->
        navigation.slideFromRight(
            RestoreAccountPage(
                ManageAccountsModule.Input(
                    popOffOnSuccess = mainPage,
                    popOffInclusive = false,
                    prefillWords = restore.words,
                    prefillPassphrase = restore.passphrase,
                    prefillMoneroHeight = restore.moneroHeight,
                    prefillMnemonicLanguageName = restore.language?.name
                )
            )
        )
        viewModel.onRestoreFromQrOpened()
    }
    uiState.openOfflineBroadcast?.let { input ->
        navigation.openOrNotifyUnsupported(pages.offlineBroadcast(input), hud)
        viewModel.onOfflineBroadcastOpened()
    }
}

/** The control stays visible where the platform lacks [operation]; it only says so. */
internal fun HSNavigation.openIfSupported(
    operationSupport: PlatformOperationSupport,
    operation: PlatformOperation,
    hud: HudMessenger,
    page: () -> HSPage?,
) = runIfSupported(operationSupport, operation, hud) { openOrNotifyUnsupported(page(), hud) }

/** Platform support comes first: an unavailable swap must not ask for a backup. */
internal fun HSNavigation.openSwap(
    operationSupport: PlatformOperationSupport,
    backupState: BackupRequirementState?,
    hud: HudMessenger,
    swapPage: () -> HSPage?,
) = runIfSupported(operationSupport, PlatformOperation.Swap, hud) {
    openIfBackedUp(backupState, Res.string.balance_swap_backup_required_description) {
        openOrNotifyUnsupported(swapPage(), hud)
    }
}

private fun runIfSupported(
    operationSupport: PlatformOperationSupport,
    operation: PlatformOperation,
    hud: HudMessenger,
    action: () -> Unit,
) {
    if (operationSupport.supports(operation)) {
        action()
    } else {
        hud.showNotSupportedOnPlatform()
    }
}

private fun HSNavigation.openIfBackedUp(
    state: BackupRequirementState?,
    backupText: StringResource,
    open: () -> Unit,
) {
    when (state) {
        BackupRequirementState.Allowed -> open()
        is BackupRequirementState.BackupRequired -> showBackupRequiredDialog(
            state.account,
            Translator.getString(backupText, state.account.name)
        )

        null -> Unit
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
                    color = ComposeAppTheme.colors.surfacePlaceholder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(Res.drawable.ic_empty_wallet),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
        VSpacer(32.dp)
        subhead2_grey(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.Balance_NoCoinsAlert),
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
            .border(1.dp, ComposeAppTheme.colors.textSecondary, shape)
    ) {
        RowWithArrow(
            text = if (count == 1) {
                stringResource(Res.string.multi_swap_unfinished)
            } else {
                stringResource(Res.string.multi_swap_unfinished_plural).format(count)
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
