package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.createaccount.PassphraseTermsPage
import cash.p.terminal.modules.declinedtokens.DeclinedTokensSheets
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletScreen
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletViewModel
import cash.p.terminal.modules.restoreaccount.restoreblockchains.ManageWalletsScreen
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuModule
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestorePhrase
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestorePhraseNonStandard
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.strings.helpers.Translator.getString
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.IAccountManager
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.inject
import kotlin.reflect.KClass

class RestoreAccountPage(val input: ManageAccountsModule.Input?) : HSPage(screenshotEnabled = false) {

    companion object {
        const val ROUTE_DUPLICATE = "duplicate_wallet"
    }

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val restoreMenuViewModel: RestoreMenuViewModel =
            viewModel(factory = RestoreMenuModule.Factory())
        val mainViewModel: RestoreViewModel = viewModel()
        val prefillWords = input?.prefillWords
        val prefillPassphrase = input?.prefillPassphrase
        val prefillMoneroHeight = input?.prefillMoneroHeight
        val prefillMnemonicLanguage = input?.prefillMnemonicLanguage

        // Initialize prefill data in shared ViewModel once per page: returning from an inner
        // page must not overwrite data a QR scan put there.
        var prefillApplied by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(prefillWords, prefillPassphrase, prefillMoneroHeight, prefillMnemonicLanguage) {
            if (prefillApplied) return@LaunchedEffect
            mainViewModel.setPrefillData(
                prefillWords,
                prefillPassphrase,
                prefillMoneroHeight,
                prefillMnemonicLanguage
            )
            prefillApplied = true
        }

        // Open the advanced screen if passphrase is present from QR code
        when {
            !prefillPassphrase.isNullOrEmpty() ->
                RestorePhraseAdvancedContent(navigation, input, restoreMenuViewModel, mainViewModel)

            input?.defaultRoute == ROUTE_DUPLICATE -> DuplicateWalletContent(navigation, input)

            else -> RestorePhrase(
                navigation = navigation,
                advanced = false,
                restoreMenuViewModel = restoreMenuViewModel,
                mainViewModel = mainViewModel,
                openRestoreAdvanced = { navigation.slideFromRight(RestorePhraseAdvancedPage(input)) },
                openSelectCoins = { navigation.openRestoreSelectCoins(input) },
                openNonStandardRestore = { navigation.slideFromRight(RestorePhraseNonStandardPage(input)) },
                onBackClick = navigation::navigateUpSafely,
                onFinish = { navigation.finishRestoreAccount(input) },
                prefillWords = prefillWords,
                prefillPassphrase = prefillPassphrase,
                prefillMoneroHeight = prefillMoneroHeight,
                prefillMnemonicLanguage = prefillMnemonicLanguage
            )
        }
    }
}

class RestorePhraseAdvancedPage(val input: ManageAccountsModule.Input?) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val flowOwner = navigation.viewModelStoreOwnerForPage(RestoreAccountPage::class)
        val restoreMenuViewModel: RestoreMenuViewModel =
            viewModel(viewModelStoreOwner = flowOwner, factory = RestoreMenuModule.Factory())
        val mainViewModel: RestoreViewModel = viewModel(viewModelStoreOwner = flowOwner)
        RestorePhraseAdvancedContent(navigation, input, restoreMenuViewModel, mainViewModel)
    }
}

class RestorePhraseNonStandardPage(val input: ManageAccountsModule.Input?) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        RestorePhraseNonStandard(
            navigation = navigation,
            mainViewModel = navigation.restoreViewModel(RestoreAccountPage::class),
            openSelectCoinsScreen = { navigation.openRestoreSelectCoins(input) },
            onBackClick = navigation::navigateUpSafely
        )
    }
}

class RestoreSelectCoinsPage(
    val owner: KClass<out HSPage>,
    val popOffOnSuccess: KClass<out HSPage>,
    val popOffInclusive: Boolean,
) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainViewModel = navigation.restoreViewModel(owner)
        ManageWalletsScreen(
            mainViewModel = mainViewModel,
            openConfigure = { token, initialConfig ->
                navigation.openRestoreTokenConfigure(token, initialConfig, mainViewModel, owner)
            },
            // Also called from composition when the account type is missing.
            onBackClick = { navigation.navigateUpFrom(this) }
        ) { navigation.removeLastUntil(popOffOnSuccess, popOffInclusive) }
    }
}

@Composable
internal fun HSNavigation.restoreViewModel(owner: KClass<out HSPage>): RestoreViewModel =
    viewModel(viewModelStoreOwner = viewModelStoreOwnerForPage(owner))

@Composable
private fun RestorePhraseAdvancedContent(
    navigation: HSNavigation,
    input: ManageAccountsModule.Input?,
    restoreMenuViewModel: RestoreMenuViewModel,
    mainViewModel: RestoreViewModel,
) {
    AdvancedRestoreScreen(
        navigation = navigation,
        restoreMenuViewModel = restoreMenuViewModel,
        mainViewModel = mainViewModel,
        openSelectCoinsScreen = { navigation.openRestoreSelectCoins(input) },
        openNonStandardRestore = { navigation.slideFromRight(RestorePhraseNonStandardPage(input)) },
        onBackClick = navigation::navigateUpSafely,
        onFinish = { navigation.finishRestoreAccount(input) },
        prefillWords = mainViewModel.prefillWords ?: input?.prefillWords,
        prefillPassphrase = mainViewModel.prefillPassphrase ?: input?.prefillPassphrase,
        prefillMoneroHeight = mainViewModel.prefillMoneroHeight ?: input?.prefillMoneroHeight,
        prefillMnemonicLanguage = mainViewModel.prefillMnemonicLanguage
            ?: input?.prefillMnemonicLanguage
    )
}

@Composable
private fun DuplicateWalletContent(navigation: HSNavigation, input: ManageAccountsModule.Input?) {
    val accountManager: IAccountManager by inject(IAccountManager::class.java)
    val accountToCopy = remember { accountManager.account(input?.accountId.orEmpty()) }
    if (accountToCopy == null) {
        val view = LocalView.current
        LaunchedEffect(Unit) {
            HudHelper.showErrorMessage(view, getString(R.string.error_no_active_account))
            navigation.navigateUp()
        }
        return
    }

    val viewModel: DuplicateWalletViewModel = koinViewModel(
        parameters = { parametersOf(accountToCopy) }
    )
    DuplicateWalletScreen(
        uiState = viewModel.uiState,
        passphraseTermsAccepted = viewModel.passphraseTermsAgreed,
        onEnterName = viewModel::onEnterName,
        onTogglePassphrase = viewModel::onTogglePassphrase,
        onChangePassphrase = viewModel::onChangePassphrase,
        onChangePassphraseConfirmation = viewModel::onChangePassphraseConfirmation,
        onCreate = viewModel::createAccount,
        onBackClick = navigation::navigateUpSafely,
        onOpenTerms = {
            navigation.slideFromRightForResult<Boolean>(PassphraseTermsPage(screenshotEnabled = false)) { agreed ->
                if (agreed) viewModel.onPassphraseTermsAgreed()
            }
        },
        onFinish = { navigation.finishRestoreAccount(input) }
    )

    DeclinedTokensSheets(viewModel)
}

private fun ManageAccountsModule.Input?.popOffTarget(): KClass<out HSPage> =
    this?.popOffOnSuccess ?: RestoreAccountPage::class

private fun ManageAccountsModule.Input?.popOffTargetInclusive(): Boolean = this?.popOffInclusive ?: false

private fun HSNavigation.openRestoreSelectCoins(input: ManageAccountsModule.Input?) {
    slideFromRight(
        RestoreSelectCoinsPage(RestoreAccountPage::class, input.popOffTarget(), input.popOffTargetInclusive())
    )
}

private fun HSNavigation.finishRestoreAccount(input: ManageAccountsModule.Input?) {
    removeLastUntil(input.popOffTarget(), input.popOffTargetInclusive())
}
