package cash.p.terminal.modules.manageaccount.shamirbackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.modules.manageaccount.backupconfirmkey.WordCheckScreen
import cash.p.terminal.modules.manageaccount.recoveryphrase.rememberSafetyRules
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule.SafetyRulesMode
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesPage
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.removeLastUntilSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.shamir_check_description
import cash.p.terminal.resources.shamir_check_title
import cash.p.terminal.ui_compose.components.rememberTextShare
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Owns the flow: [ShamirBackupViewModel] and the shares it holds live as long as this page. */
class ShamirBackupIntroPage(val accountId: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ShamirIntroScreen(
            onCreate = { navigation.slideFromRight(ShamirSchemePage(accountId)) },
            onBack = navigation::navigateUpSafely
        )
    }
}

class ShamirSchemePage(private val accountId: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.shamirBackupViewModel(accountId)
        ShamirSchemeScreen(
            scheme = viewModel.uiState.scheme,
            actions = ShamirSchemeActions(
                onSelectTotal = viewModel::onSelectTotal,
                onSelectThreshold = viewModel::onSelectThreshold,
                onSelectPreset = viewModel::onSelectPreset,
            ),
            onNext = { navigation.slideFromRight(ShamirWarningsPage(accountId)) },
            onBack = navigation::navigateUpSafely
        )
    }
}

class ShamirWarningsPage(private val accountId: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.shamirBackupViewModel(accountId)
        ShamirWarningsScreen(
            scheme = viewModel.uiState.scheme,
            showPassphraseWarning = viewModel.uiState.showPassphraseWarning,
            onShowShares = {
                viewModel.onShowShares()
                navigation.slideFromRight(ShamirSharesPage(accountId))
            },
            onBack = navigation::navigateUpSafely
        )
    }
}

class ShamirSharesPage(private val accountId: String) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.shamirBackupViewModel(accountId)
        val copyWithHud = rememberCopyWithHud()
        val textShare = rememberTextShare()
        val confirmCopy = navigation.rememberSafetyRules(SafetyRulesMode.COPY_CONFIRM, SafetyRulesPage.Result.RISK_IT) {
            viewModel.uiState.currentSharePhrase?.let(copyWithHud)
        }
        ShamirSharesScreen(
            uiState = viewModel.uiState,
            actions = ShamirShareActions(
                onCopy = confirmCopy,
                onShare = if (textShare.isAvailable) {
                    { viewModel.uiState.currentSharePhrase?.let(textShare::share) }
                } else {
                    null
                },
                onWrittenDownChange = viewModel::onWrittenDownChange,
                onConfirm = {
                    val partNumber = viewModel.uiState.confirmedCount + 1
                    viewModel.onStartCheck()
                    navigation.slideFromRight(ShamirShareCheckPage(accountId, partNumber))
                },
            ),
            onFinish = { navigation.removeLastUntilSafely(ShamirBackupIntroPage::class, inclusive = true) },
            onBack = navigation::navigateUpSafely
        )
    }
}

class ShamirShareCheckPage(private val accountId: String, private val partNumber: Int) :
    HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.shamirBackupViewModel(accountId)
        val check = viewModel.uiState.check ?: return
        WordCheckScreen(
            title = stringResource(Res.string.shamir_check_title, partNumber),
            subtitle = stringResource(Res.string.shamir_check_description, partNumber),
            check = check,
            onSelect = viewModel::onSelectWord,
            onBack = navigation::navigateUpSafely,
            onVerify = { navigation.navigateUpFrom(this@ShamirShareCheckPage) }
        )
    }
}

@Composable
private fun HSNavigation.shamirBackupViewModel(accountId: String): ShamirBackupViewModel {
    val viewModel: ShamirBackupViewModel =
        koinViewModel(viewModelStoreOwner = viewModelStoreOwnerForPage(ShamirBackupIntroPage::class)) {
            parametersOf(accountId)
        }
    val accountMissing = viewModel.uiState.accountMissing
    LaunchedEffect(accountMissing) {
        if (accountMissing) removeLastUntil(ShamirBackupIntroPage::class, inclusive = true)
    }
    return viewModel
}
