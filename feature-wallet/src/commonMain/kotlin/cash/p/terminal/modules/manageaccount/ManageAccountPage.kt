package cash.p.terminal.modules.manageaccount

import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.common.CommonParcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cash.p.terminal.feature.wallet.rememberSecretAction
import cash.p.terminal.modules.manageaccount.generalprivatekey.GeneralPrivateKeyPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.error_no_active_account
import cash.p.terminal.resources.monero_keys_unavailable
import cash.p.terminal.resources.spend_key
import cash.p.terminal.resources.view_key

class ManageAccountPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountContent(navigation, input)
    }

    @CommonParcelize
    data class Input(val accountId: String) : CommonParcelable
}

@Composable
private fun ManageAccountContent(navigation: HSNavigation, input: ManageAccountPage.Input) {
    val hudMessenger = rememberHudMessenger()
    val coroutineScope = rememberCoroutineScope()
    val openKey = navigation.rememberSecretAction<GeneralPrivateKeyPage.Input> {
        navigation.slideFromRight(GeneralPrivateKeyPage(it))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountManager: IAccountManager by lazy { getKoinInstance<IAccountManager>() }
    val account = remember { accountManager.account(input.accountId) }
    if (account == null) {
        LaunchedEffect(Unit) {
            hudMessenger.showError(getString(Res.string.error_no_active_account))
            navigation.navigateUp()
        }
        return
    }
    val viewModel = koinViewModel<ManageAccountViewModel> { parametersOf(account) }
    AccountDeletionError(viewModel.deletionState)

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.accessCodeRecoveryPages.collect { navigation.slideFromRight(it) }
        }
    }

    ManageAccountScreen(
        navigation = navigation,
        viewState = viewModel.viewState,
        account = viewModel.account,
        onCloseClicked = viewModel::onClose,
        onSaveClicked = viewModel::onSave,
        onNameChanged = viewModel::onChange,
        onActionClick = { action ->
            when (action) {
                ManageAccountModule.KeyAction.ViewKey -> openMoneroKey(
                    openKey = openKey,
                    hudMessenger = hudMessenger,
                    coroutineScope = coroutineScope,
                    key = viewModel.getMoneroViewKey(),
                    titleRes = Res.string.view_key
                )

                ManageAccountModule.KeyAction.SpendKey -> openMoneroKey(
                    openKey = openKey,
                    hudMessenger = hudMessenger,
                    coroutineScope = coroutineScope,
                    key = viewModel.getMoneroSpendKey(),
                    titleRes = Res.string.spend_key
                )

                else -> viewModel.onActionClick(action)
            }
        }
    )
}

private fun openMoneroKey(
    openKey: (GeneralPrivateKeyPage.Input) -> Unit,
    hudMessenger: HudMessenger,
    coroutineScope: CoroutineScope,
    key: String?,
    titleRes: StringResource
) = coroutineScope.launch {
    if (key == null) {
        hudMessenger.showError(getString(Res.string.monero_keys_unavailable))
        return@launch
    }
    openKey(GeneralPrivateKeyPage.Input(key, getString(titleRes)))
}
