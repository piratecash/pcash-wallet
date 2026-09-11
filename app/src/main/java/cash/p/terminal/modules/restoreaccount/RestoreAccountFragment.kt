package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.composablePage
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsScreen
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.modules.declinedtokens.DeclinedTokensSheets
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletScreen
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.restoreaccount.restoreblockchains.ManageWalletsScreen
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreByMenu
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKey
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuModule
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestorePhrase
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestorePhraseNonStandard
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.Translator.getString
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.wallet.IAccountManager
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.inject

class RestoreAccountFragment : BaseComposeFragment(screenshotEnabled = false) {

    companion object {
        const val ROUTE_DUPLICATE = "duplicate_wallet"
        const val ROUTE_RESTORE_PHRASE = "restore_phrase"
        const val ROUTE_RESTORE_PHRASE_ADVANCED = "restore_phrase_advanced"
    }

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<ManageAccountsModule.Input>()
        val popUpToInclusiveId = input?.popOffOnSuccess ?: R.id.restoreAccountFragment
        val inclusive = input?.popOffInclusive ?: false
        val defaultRoute = input?.defaultRoute ?: ROUTE_RESTORE_PHRASE

        RestoreAccountNavHost(
            fragmentNavController = navController,
            popUpToInclusiveId = popUpToInclusiveId,
            inclusive = inclusive,
            defaultRoute = defaultRoute,
            accountId = input?.accountId.orEmpty(),
            mnemonicDraft = input?.mnemonicDraft
        )
    }

}

@Composable
private fun RestoreAccountNavHost(
    fragmentNavController: NavController,
    popUpToInclusiveId: Int,
    inclusive: Boolean,
    defaultRoute: String,
    accountId: String,
    mnemonicDraft: MnemonicImportDraft? = null
) {
    val navController = rememberNavController()
    val restoreMenuViewModel: RestoreMenuViewModel =
        viewModel(factory = RestoreMenuModule.Factory())
    val mainViewModel = viewModel<RestoreViewModel>().also { it.initializeDraft(mnemonicDraft) }

    // Navigate to advanced screen if passphrase is present from QR code
    val actualStartDestination = if (mnemonicDraft?.passphraseEnabled == true) {
        RestoreAccountFragment.ROUTE_RESTORE_PHRASE_ADVANCED
    } else {
        defaultRoute
    }

    val onFinish = { fragmentNavController.popBackStack(popUpToInclusiveId, inclusive); Unit }
    NavHost(navController = navController, startDestination = actualStartDestination) {
        addPhraseRoutes(navController, fragmentNavController, restoreMenuViewModel, mainViewModel, onFinish)
        addDuplicateRoutes(navController, fragmentNavController, accountId, onFinish)
        addWalletRoutes(navController, mainViewModel, onFinish)
        addRestoreTokenConfigureRoutes(navController, mainViewModel)
    }
}

private fun NavGraphBuilder.addPhraseRoutes(
    navController: NavController,
    fragmentNavController: NavController,
    restoreMenuViewModel: RestoreMenuViewModel,
    mainViewModel: RestoreViewModel,
    onFinish: () -> Unit,
) {
    val openSelectCoins = { navController.navigate("restore_select_coins") }
    val openNonStandard = { navController.navigate("restore_phrase_nonstandard") }
    composable(RestoreAccountFragment.ROUTE_RESTORE_PHRASE) {
        RestorePhrase(
            advanced = false, mainViewModel = mainViewModel,
            openRestoreAdvanced = { navController.navigate(RestoreAccountFragment.ROUTE_RESTORE_PHRASE_ADVANCED) },
            openSelectCoins = openSelectCoins, openNonStandardRestore = openNonStandard,
            onBackClick = { fragmentNavController.navigateUpSafely() }, onFinish = onFinish,
        )
    }
    composablePage(RestoreAccountFragment.ROUTE_RESTORE_PHRASE_ADVANCED) {
        val onBack = {
            if (!navController.navigateUpSafely()) fragmentNavController.navigateUpSafely()
            Unit
        }
        AdvancedRestoreScreen(
            restoreOption = restoreMenuViewModel.restoreOption,
            recoveryPhrase = {
                RestorePhrase(
                    advanced = true, mainViewModel = mainViewModel,
                    openSelectCoins = openSelectCoins, openNonStandardRestore = openNonStandard,
                    onBackClick = onBack, onFinish = onFinish,
                    restoreMenu = { RestoreByMenu(restoreMenuViewModel) }
                )
            },
            privateKey = {
                RestorePrivateKey(
                    restoreMenuViewModel = restoreMenuViewModel, mainViewModel = mainViewModel,
                    openSelectCoinsScreen = openSelectCoins, onBackClick = onBack
                )
            }
        )
    }
}

private fun NavGraphBuilder.addDuplicateRoutes(
    navController: NavController,
    fragmentNavController: NavController,
    accountId: String,
    onFinish: () -> Unit,
) {
    composablePage(RestoreAccountFragment.ROUTE_DUPLICATE) { backStackEntry ->
        DuplicateRestoreRoute(accountId, backStackEntry,
            onBack = { fragmentNavController.navigateUpSafely() },
            onMissingAccount = { fragmentNavController.popBackStack() },
            onOpenTerms = { navController.navigate("passphrase_terms") }, onFinish = onFinish)
    }
    composablePage("passphrase_terms") { RestorePassphraseTermsRoute(navController) }
}

@Composable
private fun DuplicateRestoreRoute(
    accountId: String,
    backStackEntry: NavBackStackEntry,
    onBack: () -> Unit,
    onMissingAccount: () -> Unit,
    onOpenTerms: () -> Unit,
    onFinish: () -> Unit,
) {
    val closeMissingAccount by rememberUpdatedState(onMissingAccount)
    val accountManager: IAccountManager by inject(IAccountManager::class.java)
    val accountToCopy = remember { accountManager.account(accountId) }
    if (accountToCopy == null) {
        val view = LocalView.current
        LaunchedEffect(Unit) {
            HudHelper.showErrorMessage(view, getString(R.string.error_no_active_account))
            closeMissingAccount()
        }
        return
    }

    val viewModel: DuplicateWalletViewModel = koinViewModel(
        parameters = { parametersOf(accountToCopy) }
    )
    val passphraseTermsAgreed by backStackEntry.savedStateHandle
        .getStateFlow("passphrase_terms_agreed", false)
        .collectAsStateWithLifecycle()

    LaunchedEffect(passphraseTermsAgreed) {
        if (passphraseTermsAgreed) {
            viewModel.onPassphraseTermsAgreed()
            backStackEntry.savedStateHandle["passphrase_terms_agreed"] = false
        }
    }
    DuplicateWalletScreen(
        uiState = viewModel.uiState,
        passphraseTermsAccepted = viewModel.passphraseTermsAgreed,
        onEnterName = viewModel::onEnterName,
        onTogglePassphrase = viewModel::onTogglePassphrase,
        onChangePassphrase = viewModel::onChangePassphrase,
        onChangePassphraseConfirmation = viewModel::onChangePassphraseConfirmation,
        onCreate = viewModel::createAccount,
        onBackClick = onBack,
        onOpenTerms = onOpenTerms,
        onFinish = onFinish
    )

    DeclinedTokensSheets(viewModel)
}

@Composable
private fun RestorePassphraseTermsRoute(navController: NavController) {
    val context = LocalContext.current
    val termTitles = context.resources.getStringArray(R.array.passphrase_terms_checkboxes)
    val viewModel = koinViewModel<PassphraseTermsViewModel> { parametersOf(termTitles) }
    PassphraseTermsScreen(
        uiState = viewModel.uiState,
        onCheckboxToggle = viewModel::toggleCheckbox,
        onAgreeClick = {
            viewModel.agree()
            navController.previousBackStackEntry?.savedStateHandle?.set("passphrase_terms_agreed", true)
            navController.navigateUpSafely()
        },
        onBackClick = navController::navigateUpSafely
    )
}

private fun NavGraphBuilder.addWalletRoutes(
    navController: NavController,
    mainViewModel: RestoreViewModel,
    onFinish: () -> Unit,
) {
    composablePage("restore_select_coins") {
        ManageWalletsScreen(
            mainViewModel = mainViewModel,
            openConfigure = { token, initialConfig ->
                navController.openRestoreTokenConfigure(token, initialConfig, mainViewModel)
            },
            onBackClick = navController::navigateUpSafely,
            onFinish = onFinish
        )
    }
    composablePage("restore_phrase_nonstandard") {
        RestorePhraseNonStandard(
            mainViewModel = mainViewModel,
            openSelectCoinsScreen = { navController.navigate("restore_select_coins") },
            onBackClick = navController::navigateUpSafely
        )
    }
}
