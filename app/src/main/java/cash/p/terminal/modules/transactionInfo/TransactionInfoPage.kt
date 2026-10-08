package cash.p.terminal.modules.transactionInfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.restartMain
import cash.p.terminal.core.orHide
import cash.p.terminal.modules.main.MainPage
import cash.p.terminal.modules.settings.addresschecker.AddressCheckPage
import cash.p.terminal.modules.transactions.AmlCheckInfoBottomSheet
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.DescriptionCell
import cash.p.terminal.ui.compose.components.PriceWithToggleCell
import cash.p.terminal.ui.compose.components.SectionTitleCell
import cash.p.terminal.ui.compose.components.TransactionAmountCell
import cash.p.terminal.ui.compose.components.TransactionInfoAddressCell
import cash.p.terminal.ui.compose.components.TransactionInfoBtcLockCell
import cash.p.terminal.ui.compose.components.TransactionInfoContactCell
import cash.p.terminal.ui.compose.components.TransactionInfoDoubleSpendCell
import cash.p.terminal.ui.compose.components.TransactionInfoExplorerCell
import cash.p.terminal.ui.compose.components.TransactionInfoOfflineStatusCell
import cash.p.terminal.ui.compose.components.TransactionInfoRawTransaction
import cash.p.terminal.ui.compose.components.TransactionInfoAmlCheckCell
import cash.p.terminal.ui.compose.components.TransactionInfoSentToSelfCell
import cash.p.terminal.ui.compose.components.TransactionInfoSpeedUpCell
import cash.p.terminal.ui.compose.components.TransactionInfoStatusCell
import cash.p.terminal.ui.compose.components.TransactionInfoTransactionHashCell
import cash.p.terminal.ui.compose.components.TransactionNftAmountCell
import cash.p.terminal.modules.transactions.poison_status.AddressPoisoningInfoDialog
import cash.p.terminal.ui.compose.components.PoisonWarningCell
import cash.p.terminal.ui.compose.components.WarningMessageCell
import cash.p.terminal.ui.compose.BalanceHideOnFlipHandling
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.ConnectionStatusView
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.TitleAndValueClickableCell
import cash.p.terminal.ui_compose.components.TitleAndValueColoredCell
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.activity.compose.LocalActivity

class TransactionInfoPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val transactionsViewModel: TransactionsViewModel = koinViewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(MainPage::class)
        )

        val viewItem = transactionsViewModel.tmpItemToShow
        val activity = LocalActivity.current
        if (viewItem == null) {
            if (!navigation.removeLastUntil(TransactionInfoPage::class, true)) activity?.restartMain()
            return
        }

        val viewModel = viewModel<TransactionInfoViewModel>(factory = TransactionInfoModule.Factory(viewItem))

        BalanceHideOnFlipHandling()

        TransactionInfoScreen(
            state = TransactionInfoScreenState(
                viewItems = viewModel.viewItems,
                hideSensitiveInfo = viewModel.balanceHidden,
                isPending = viewModel.isPending,
            ),
            actions = TransactionInfoScreenActions(
                onClose = navigation::navigateUpSafely,
                onDeletePendingTransaction = viewModel::deletePendingTransaction,
                onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
                getRawTransaction = viewModel::getRawTransaction,
            ),
            navigation = navigation,
        )
    }
}

private data class TransactionInfoScreenState(
    val viewItems: List<List<TransactionInfoViewItem>>,
    val hideSensitiveInfo: Boolean,
    val isPending: Boolean,
)

private class TransactionInfoScreenActions(
    val onClose: () -> Unit,
    val onDeletePendingTransaction: () -> Unit,
    val onToggleBalanceVisibility: () -> Unit,
    val getRawTransaction: () -> String?,
)

@Composable
private fun TransactionInfoScreen(
    state: TransactionInfoScreenState,
    actions: TransactionInfoScreenActions,
    navigation: HSNavigation,
    amlStatusManager: AmlStatusManager = koinInject()
) {
    var showAmlInfoSheet by remember { mutableStateOf(false) }
    var amlAddressSelectionData by remember { mutableStateOf<AmlAddressSelectionData?>(null) }
    val onPendingStatusTap = rememberPendingStatusTap(
        isPending = state.isPending,
        onDeletePendingTransaction = actions.onDeletePendingTransaction,
        onNavigateUp = navigation::navigateUp,
    )

    TransactionInfoContent(
        state = state,
        actions = actions,
        navigation = navigation,
        onAmlInfoClick = { showAmlInfoSheet = true },
        onPendingStatusTap = onPendingStatusTap,
        onAmlRiskClick = { addresses, status ->
            openAmlDetails(
                addresses = addresses,
                status = status,
                navigation = navigation,
                amlStatusManager = amlStatusManager,
                onMultipleAddresses = { amlAddressSelectionData = it },
            )
        },
    )

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

    amlAddressSelectionData?.let { data ->
        AmlAddressSelectionBottomSheet(
            addresses = data.addresses,
            onAddressSelected = { address ->
                amlAddressSelectionData = null
                navigation.slideFromRight(AddressCheckPage(AddressCheckPage.Input(address)))
            },
            onLaterClick = { amlAddressSelectionData = null },
            onDismiss = { amlAddressSelectionData = null }
        )
    }
}

@Composable
private fun TransactionInfoContent(
    state: TransactionInfoScreenState,
    actions: TransactionInfoScreenActions,
    navigation: HSNavigation,
    onAmlInfoClick: () -> Unit,
    onPendingStatusTap: (() -> Unit)?,
    onAmlRiskClick: (List<String>, AmlStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(color = ComposeAppTheme.colors.tyler)) {
        AppBar(
            title = stringResource(R.string.TransactionInfo_Title),
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(R.string.Button_Close),
                    icon = R.drawable.ic_close_24,
                    onClick = actions.onClose
                )
            )
        )
        Box(modifier = Modifier.weight(1f)) {
            TransactionInfo(
                state = state,
                actions = actions,
                navigation = navigation,
                onAmlInfoClick = onAmlInfoClick,
                onPendingStatusTap = onPendingStatusTap,
                onAmlRiskClick = onAmlRiskClick,
            )
            ConnectionStatusView(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun rememberPendingStatusTap(
    isPending: Boolean,
    onDeletePendingTransaction: () -> Unit,
    onNavigateUp: () -> Unit,
): (() -> Unit)? {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    if (!isPending) return null

    return {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 2000) tapCount = 0
        lastTapTime = now
        tapCount++
        if (tapCount >= 5) {
            tapCount = 0
            onDeletePendingTransaction()
            onNavigateUp()
        }
    }
}

private fun openAmlDetails(
    addresses: List<String>,
    status: AmlStatus,
    navigation: HSNavigation,
    amlStatusManager: AmlStatusManager,
    onMultipleAddresses: (AmlAddressSelectionData) -> Unit,
) {
    if (addresses.size == 1) {
        navigation.slideFromRight(AddressCheckPage(AddressCheckPage.Input(addresses.first())))
    } else {
        onMultipleAddresses(
            AmlAddressSelectionData(
                addresses = addresses.map { address ->
                    address to (amlStatusManager.getAddressStatus(address) ?: status)
                }
            )
        )
    }
}

private data class AmlAddressSelectionData(
    val addresses: List<Pair<String, AmlStatus>>
)

@Composable
private fun TransactionInfo(
    state: TransactionInfoScreenState,
    actions: TransactionInfoScreenActions,
    navigation: HSNavigation,
    onAmlInfoClick: () -> Unit = {},
    onPendingStatusTap: (() -> Unit)? = null,
    onAmlRiskClick: (List<String>, AmlStatus) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        items(state.viewItems) { section ->
            TransactionInfoSection(
                section = section,
                navigation = navigation,
                onSensitiveValueClick = {
                    HudHelper.vibrate(App.instance)
                    actions.onToggleBalanceVisibility()
                },
                getRawTransaction = actions.getRawTransaction,
                hideSensitiveInfo = state.hideSensitiveInfo,
                onAmlInfoClick = onAmlInfoClick,
                onPendingStatusTap = onPendingStatusTap,
                onAmlRiskClick = onAmlRiskClick,
            )
        }
    }
}

@Composable
fun TransactionInfoSection(
    section: List<TransactionInfoViewItem>,
    navigation: HSNavigation,
    onSensitiveValueClick: () -> Unit,
    getRawTransaction: () -> String?,
    hideSensitiveInfo: Boolean,
    onAmlInfoClick: () -> Unit = {},
    onPendingStatusTap: (() -> Unit)? = null,
    onAmlRiskClick: (List<String>, AmlStatus) -> Unit = { _, _ -> },
) {
    //items without background
    if (section.size == 1) {
        when (val item = section[0]) {
            is TransactionInfoViewItem.WarningMessage -> {
                WarningMessageCell(item.message)
                return
            }

            is TransactionInfoViewItem.PoisonWarning -> {
                var showPoisoningInfo by remember { mutableStateOf(false) }
                PoisonWarningCell(onInfoClick = { showPoisoningInfo = true })
                if (showPoisoningInfo) {
                    AddressPoisoningInfoDialog(onDismiss = { showPoisoningInfo = false })
                }
                return
            }

            is TransactionInfoViewItem.Description -> {
                DescriptionCell(text = item.text)
                return
            }

            else -> {
                //do nothing
            }
        }
    }

    CellUniversalLawrenceSection(
        buildList {
            for (viewItem in section) {
                when (viewItem) {
                    is TransactionInfoViewItem.Transaction -> {
                        add {
                            SectionTitleCell(
                                title = viewItem.leftValue,
                                value = viewItem.rightValue,
                                iconResId = viewItem.icon
                            )
                        }
                    }

                    is TransactionInfoViewItem.Amount -> {
                        add {
                            TransactionAmountCell(
                                amountType = viewItem.amountType,
                                fiatAmount = viewItem.fiatValue,
                                coinAmount = viewItem.coinValue,
                                coinIconUrl = viewItem.coinIconUrl,
                                alternativeCoinIconUrl = viewItem.alternativeCoinIconUrl,
                                badge = viewItem.badge,
                                coinIconPlaceholder = viewItem.coinIconPlaceholder,
                                onValueClick = onSensitiveValueClick,
                                onClick = viewItem.coinUid?.let {
                                    {
                                        navigation.slideFromRight(CoinPage(CoinFragmentInput(it)))
                                    }
                                }
                            )
                        }
                    }

                    is TransactionInfoViewItem.NftAmount -> {
                        add {
                            TransactionNftAmountCell(
                                viewItem.title,
                                viewItem.nftValue,
                                viewItem.nftName,
                                viewItem.iconUrl,
                                viewItem.iconPlaceholder,
                                viewItem.badge,
                            )
                        }
                    }

                    is TransactionInfoViewItem.Value -> {
                        add {
                            TitleAndValueCell(
                                title = viewItem.title,
                                value = viewItem.value,
                            )
                        }
                    }

                    is TransactionInfoViewItem.ValueClickable -> {
                        add {
                            TitleAndValueClickableCell(
                                title = viewItem.title,
                                value = viewItem.value,
                                onClick = onSensitiveValueClick,
                            )
                        }
                    }

                    is TransactionInfoViewItem.ValueColored -> {
                        add {
                            TitleAndValueColoredCell(
                                title = viewItem.title,
                                value = viewItem.value,
                                color = viewItem.color,
                            )
                        }
                    }

                    is TransactionInfoViewItem.PriceWithToggle -> {
                        add {
                            PriceWithToggleCell(
                                title = viewItem.title,
                                valueOne = viewItem.valueTwo,
                                valueTwo = viewItem.valueOne
                            )
                        }
                    }

                    is TransactionInfoViewItem.Address -> {
                        add {
                            TransactionInfoAddressCell(
                                title = viewItem.title,
                                value = viewItem.value.orHide(hideSensitiveInfo),
                                showAdd = viewItem.showAdd,
                                blockchainType = viewItem.blockchainType,
                                navigation = navigation,
                                textAlign = if (!hideSensitiveInfo) TextAlign.End else TextAlign.Start,
                                onCopy = {
                                },
                                onAddToExisting = {
                                },
                                onAddToNew = {
                                },
                                onValueClick = onSensitiveValueClick.takeIf { hideSensitiveInfo },
                                showCopyWarning = viewItem.showCopyWarning,
                                collapseAddress = viewItem.collapseAddress,
                            )
                        }
                    }

                    is TransactionInfoViewItem.ContactItem -> {
                        add {
                            TransactionInfoContactCell(viewItem.contact.name)
                        }
                    }

                    is TransactionInfoViewItem.Status -> {
                        add {
                            TransactionInfoStatusCell(
                                status = viewItem.status,
                                navigation = navigation,
                                onPendingTap = onPendingStatusTap
                            )
                        }
                    }

                    is TransactionInfoViewItem.OfflineStatus -> {
                        add {
                            TransactionInfoOfflineStatusCell(
                                status = viewItem.status,
                                navigation = navigation,
                            )
                        }
                    }

                    is TransactionInfoViewItem.SpeedUpCancel -> {
                        add {
                            TransactionInfoSpeedUpCell(
                                transactionHash = viewItem.transactionHash,
                                blockchainType = viewItem.blockchainType,
                                availability = viewItem.availability,
                                wallet = viewItem.wallet,
                                navigation = navigation
                            )
                        }
                        // MOBILE-593
                        /*add {
                            TransactionInfoCancelCell(
                                transactionHash = viewItem.transactionHash,
                                blockchainType = viewItem.blockchainType,
                                navController = navController
                            )
                        }*/
                    }

                    is TransactionInfoViewItem.TransactionHash -> {
                        if (viewItem.transactionHash.isNotEmpty()) {
                            add {
                                TransactionInfoTransactionHashCell(transactionHash = viewItem.transactionHash)
                            }
                        }
                    }

                    is TransactionInfoViewItem.Explorer -> {
                        viewItem.url?.let {
                            add {
                                TransactionInfoExplorerCell(
                                    title = viewItem.title,
                                    url = viewItem.url,
                                    iconResId = viewItem.iconResId,
                                )
                            }
                        }
                    }

                    is TransactionInfoViewItem.RawTransaction -> {
                        add {
                            TransactionInfoRawTransaction(rawTransaction = getRawTransaction)
                        }
                    }

                    is TransactionInfoViewItem.LockState -> {
                        add {
                            TransactionInfoBtcLockCell(
                                lockState = viewItem,
                                navigation = navigation
                            )
                        }
                    }

                    is TransactionInfoViewItem.DoubleSpend -> {
                        add {
                            TransactionInfoDoubleSpendCell(
                                transactionHash = viewItem.transactionHash,
                                conflictingHash = viewItem.conflictingHash,
                                navigation = navigation
                            )
                        }
                    }

                    is TransactionInfoViewItem.SentToSelf -> {
                        add {
                            TransactionInfoSentToSelfCell()
                        }
                    }

                    is TransactionInfoViewItem.AmlCheck -> {
                        add {
                            TransactionInfoAmlCheckCell(
                                status = viewItem.status,
                                onInfoClick = onAmlInfoClick,
                                onRiskClick = {
                                    onAmlRiskClick(
                                        viewItem.senderAddresses,
                                        viewItem.status
                                    )
                                }
                            )
                        }
                    }

                    else -> {
                        //do nothing
                    }
                }
            }
        }
    )
}
