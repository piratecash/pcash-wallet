package cash.p.terminal.modules.balance.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.usecase.GetRestoreHeightForWalletUseCase
import cash.p.terminal.featureStacking.ui.staking.StackingPage
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewScreen
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockScreen
import cash.p.terminal.modules.balance.token.creationblock.CreationBlockViewModel
import cash.p.terminal.modules.main.MainPage
import cash.p.terminal.modules.offline.OfflineModeToggleViewModel
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.transactions.TransactionsViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.core.premiumAction
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.ui.compose.BalanceHideOnFlipHandling
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.isPirateCash
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class TokenBalancePage(val input: Wallet) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val transactionsViewModel: TransactionsViewModel = koinViewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(MainPage::class)
        )

        val viewModel = viewModel<TokenBalanceViewModel>(factory = TokenBalanceModule.Factory(input))
        val offlineModeToggleViewModel: OfflineModeToggleViewModel =
            koinViewModel { parametersOf(input) }

        val context = LocalContext.current

        OfflineModeErrorEffect(offlineModeToggleViewModel)

        ResumeEffects(viewModel, navigation)

        BalanceHideOnFlipHandling()
        viewModel.refreshTransactionDisplaySettings()
        TokenBalanceScreen(
            viewModel = viewModel,
            transactionsViewModel = transactionsViewModel,
            navigation = navigation,
            onStackingClicked = {
                navigation.slideFromRight(
                    StackingPage(if (input.isPirateCash()) StackingType.PCASH else StackingType.COSANTA)
                )
            },
            onShowAllTransactionsClicked = {
                navigation.authorizedAction(
                    ConfirmPinPage.InputConfirm(
                        descriptionResId = R.string.Unlock_EnterPasscode_Transactions_Hide,
                        pinType = PinType.TRANSACTIONS_HIDE
                    )
                ) {
                    viewModel.showAllTransactions(true)
                }
            },
            onClickSubtitle = {
                viewModel.toggleTotalType()
                HudHelper.vibrate(context)
            },
            onRefresh = viewModel::refresh,
            refreshing = viewModel.refreshing,
            onSettingsClick = { navigation.slideFromRight(AssetSettingsPage(input)) },
            onGoOnline = offlineModeToggleViewModel::goOnline,
        )
    }

    @Composable
    private fun ResumeEffects(viewModel: TokenBalanceViewModel, navigation: HSNavigation) {
        PageResumeEffect(
            onResume = {
                viewModel.startStatusChecker()
                viewModel.onResume()
            },
            onPause = {
                viewModel.stopStatusChecker()
                // No need to hide transactions when the user goes to the next screen, but hide
                // them when they go to background on back.
                val skipHideTransactions =
                    navigation.backStack.contains(this) && navigation.lastOrNull() !== this
                if (!skipHideTransactions) {
                    viewModel.showAllTransactions(false)
                }
            }
        )
    }
}

class AssetSettingsPage(val wallet: Wallet) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: TokenBalanceViewModel = viewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(TokenBalancePage::class),
            factory = TokenBalanceModule.Factory(wallet)
        )
        val offlineModeToggleViewModel: OfflineModeToggleViewModel = koinViewModel(
            viewModelStoreOwner = navigation.viewModelStoreOwnerForPage(TokenBalancePage::class)
        ) { parametersOf(wallet) }
        OfflineModeErrorEffect(offlineModeToggleViewModel)

        // Zcash created-in-app wallets have no history before their creation checkpoint, so
        // editing the birthday height is meaningful only for restored (imported) Zcash wallets.
        val creationBlockVisible = wallet.token.blockchainType == BlockchainType.Monero ||
            (wallet.token.blockchainType == BlockchainType.Zcash &&
                wallet.account.origin == AccountOrigin.Restored)
        val getRestoreHeight = remember { getKoinInstance<GetRestoreHeightForWalletUseCase>() }
        val currentHeightText by produceState<String?>(null, wallet, creationBlockVisible) {
            value = if (creationBlockVisible) getRestoreHeight(wallet)?.toString() else null
        }
        AssetSettingsScreen(
            amlCheckEnabled = viewModel.uiState.amlCheckEnabled,
            onAmlCheckChange = { enabled ->
                if (enabled) {
                    navigation.premiumAction {
                        viewModel.setAmlCheckEnabled(true)
                    }
                } else {
                    viewModel.setAmlCheckEnabled(false)
                }
            },
            pricePeriod = viewModel.uiState.displayDiffPricePeriod,
            displayDiffOptionType = viewModel.uiState.displayDiffOptionType,
            isRoundingAmount = viewModel.uiState.isRoundingAmount,
            onPricePeriodChange = viewModel::setDisplayPricePeriod,
            onDisplayDiffOptionTypeChange = viewModel::setDisplayDiffOptionType,
            onRoundingAmountChange = viewModel::setRoundingAmount,
            onAddressPoisoningViewClick = {
                navigation.slideFromRight(AddressPoisoningViewPage(wallet))
            },
            transactionFiltersEnabled = viewModel.uiState.transactionFiltersEnabled,
            onTransactionFiltersChange = viewModel::setTransactionFiltersEnabled,
            offlineUiState = offlineModeToggleViewModel.uiState,
            onConfirmOffline = offlineModeToggleViewModel::confirmOffline,
            onGoOnline = offlineModeToggleViewModel::goOnline,
            onOfflineSheetDismiss = offlineModeToggleViewModel::sheetClosed,
            navigation = navigation,
            onBack = navigation::navigateUpSafely,
            creationBlockVisible = creationBlockVisible,
            currentHeightText = currentHeightText,
            onCreationBlockClick = { navigation.slideFromRight(CreationBlockPage(wallet)) },
        )
    }
}

// The transition is started from both the balance banner and the settings switch, and both pages
// share the view model; the error is cleared on show, so it appears once even while both are composed.
@Composable
internal fun OfflineModeErrorEffect(viewModel: OfflineModeToggleViewModel) {
    val view = LocalView.current
    LaunchedEffect(viewModel.uiState.error) {
        viewModel.uiState.error?.let {
            HudHelper.showErrorMessage(view, it)
            viewModel.errorShown()
        }
    }
}

class CreationBlockPage(val wallet: Wallet) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val creationBlockViewModel: CreationBlockViewModel = koinViewModel { parametersOf(wallet) }
        CreationBlockScreen(
            uiState = creationBlockViewModel.uiState,
            onHeightChange = creationBlockViewModel::onHeightChange,
            onDatePick = creationBlockViewModel::onDatePicked,
            onRescanConfirm = creationBlockViewModel::onRescanConfirmed,
            onClose = navigation::navigateUpSafely,
            onRescanStart = navigation::navigateUp,
        )
    }
}

class AddressPoisoningViewPage(val wallet: Wallet) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val addressPoisoningViewModel: AddressPoisoningViewModel = koinViewModel {
            parametersOf(wallet.coin.uid, wallet.isPirateCash(), wallet.token.blockchainType)
        }
        AddressPoisoningViewScreen(
            uiState = addressPoisoningViewModel.uiState,
            onSelect = addressPoisoningViewModel::onSelect,
            onClose = navigation::navigateUpSafely,
        )
    }
}
