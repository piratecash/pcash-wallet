package cash.p.terminal.feature.miniapp.ui.connect

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.feature.miniapp.ui.TELEGRAM_BOT_URL
import cash.p.terminal.feature.miniapp.ui.components.MiniAppStepScaffold
import cash.p.terminal.feature.miniapp.ui.components.StepDescriptionStyle
import cash.p.terminal.feature.miniapp.ui.components.rememberStepIndicatorState
import cash.p.terminal.feature.miniapp.ui.connect.screens.CreateWalletStepScreen
import cash.p.terminal.feature.miniapp.ui.connect.screens.FinishStepScreen
import cash.p.terminal.feature.miniapp.ui.connect.screens.SpecialProposalStepScreen
import cash.p.terminal.feature.miniapp.ui.connect.screens.SpecialProposalUiState
import cash.p.terminal.feature.miniapp.ui.connect.screens.TokenCheckingScreen
import cash.p.terminal.feature.miniapp.ui.connect.screens.TokenMissingScreen
import cash.p.terminal.feature.miniapp.ui.connect.screens.WalletSelectionScreen
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.navigation.WalletPages
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.inject

class ConnectMiniAppPage(val input: ConnectMiniAppDeeplinkInput?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<ConnectMiniAppViewModel> { parametersOf(input) }
        val context = LocalContext.current

        ConnectMiniAppContent(
            viewModel = viewModel,
            navigation = navigation,
            onOpenMiniAppClick = {
                navigation.navigateUpSafely()
                val intent = Intent(Intent.ACTION_VIEW, TELEGRAM_BOT_URL.toUri())
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun ConnectMiniAppContent(
    viewModel: ConnectMiniAppViewModel,
    navigation: HSNavigation,
    onOpenMiniAppClick: () -> Unit
) {
    val appPages: AppPages = koinInject()
    val walletPages: WalletPages = koinInject()
    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.connect_mini_app_title),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.PlainString(""),
                        icon = R.drawable.ic_close_24,
                        onClick = { navigation.navigateUpSafely() }
                    )
                )
            )
        }
    ) { paddingValues ->
        val stepIndicatorState = rememberStepIndicatorState()
        val coroutineScope = rememberCoroutineScope()

        // Update state when step changes
        LaunchedEffect(uiState.currentStep) {
            stepIndicatorState.currentStep = uiState.currentStep
        }

        when (uiState.currentStep) {
            ConnectMiniAppViewModel.STEP_WALLET -> {
                when {
                    uiState.tokenCheckError != null -> {
                        MiniAppStepScaffold(
                            stepTitle = stringResource(R.string.connect_mini_app_step_1),
                            stepDescription = uiState.tokenCheckError.orEmpty(),
                            descriptionStyle = StepDescriptionStyle.Red,
                            stepIndicatorState = stepIndicatorState,
                            modifier = Modifier.padding(paddingValues),
                            bottomContent = {
                                ButtonPrimaryYellow(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = stringResource(R.string.Button_Retry),
                                    onClick = viewModel::onRetryTokenCheck
                                )
                            },
                            content = {}
                        )
                    }

                    uiState.isCheckingTokens -> {
                        TokenCheckingScreen(
                            stepIndicatorState = stepIndicatorState,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    uiState.missingTokenQueries.isNotEmpty() -> {
                        TokenMissingScreen(
                            allTokensText = uiState.allTokensText,
                            missingTokenNames = uiState.missingTokenNames,
                            isAddingTokens = uiState.isAddingTokens,
                            onAddClick = viewModel::onAddTokensClick,
                            stepIndicatorState = stepIndicatorState,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    uiState.walletItems.size > 1 && uiState.chosenAccountId == null -> {
                        WalletSelectionScreen(
                            isLoading = uiState.isLoading,
                            walletItems = uiState.walletItems,
                            selectedAccountId = uiState.preselectedAccountId,
                            onWalletSelected = viewModel::onWalletSelected,
                            onContinueClick = viewModel::onConfirmWalletSelectedClick,
                            stepIndicatorState = stepIndicatorState,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    else -> {
                        CreateWalletStepScreen(
                            isLoading = uiState.isLoading,
                            needsBackup = uiState.needsBackup,
                            onNewWalletClick = {
                                navigation.slideFromRight(appPages.createAccount())
                            },
                            onImportWalletClick = {
                                navigation.slideFromRight(appPages.restoreAccount())
                            },
                            onManualBackupClick = {
                                uiState.chosenAccountId?.let { chosenAccountId ->
                                    navigation.slideFromBottom(
                                        appPages.backupKey(BackupKeyInput(chosenAccountId))
                                    )
                                }
                            },
                            onLocalBackupClick = {
                                uiState.chosenAccountId?.let { chosenAccountId ->
                                    val accountManager: IAccountManager by inject(
                                        IAccountManager::class.java
                                    )
                                    accountManager.account(chosenAccountId)
                                        ?.let { account ->
                                            navigation.slideFromBottom(
                                                walletPages.backupLocal(account)
                                            )
                                        }
                                }
                            },
                            stepIndicatorState = stepIndicatorState,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }

            ConnectMiniAppViewModel.STEP_SPECIAL_PROPOSAL -> {
                // Refresh data when returning from buy screen
                PageResumeEffect(
                    onResume = viewModel::loadSpecialProposalData,
                    onPause = {}
                )

                SpecialProposalStepScreen(
                    uiState = SpecialProposalUiState(
                        data = uiState.specialProposalData,
                        selectedTab = uiState.selectedCoinTab,
                        isLoading = uiState.isSpecialProposalLoading,
                        isPremium = uiState.isPremiumUser,
                        error = uiState.specialProposalError,
                        isJwtExpired = uiState.isJwtExpired
                    ),
                    stepIndicatorState = stepIndicatorState,
                    onTabSelected = viewModel::onCoinTabSelected,
                    onBuyClick = {
                        coroutineScope.launch {
                            viewModel.getTokenForSwap()?.let { token ->
                                navigation.slideFromRight(walletPages.swap(token))
                            }
                        }
                    },
                    onConnectClick = viewModel::connectWallet,
                    onRetryClick = viewModel::loadSpecialProposalData,
                    onOpenMiniAppClick = onOpenMiniAppClick,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            ConnectMiniAppViewModel.STEP_FINISH -> {
                uiState.finishState?.let { finishState ->
                    // Handle close event
                    LaunchedEffect(uiState.closeEvent) {
                        if (uiState.closeEvent) {
                            navigation.navigateUp()
                        }
                    }

                    FinishStepScreen(
                        finishState = finishState,
                        onCloseClick = viewModel::onFinishClose,
                        onRetryClick = viewModel::onRetryClick,
                        onOpenMiniAppClick = onOpenMiniAppClick,
                        stepIndicatorState = stepIndicatorState,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}

