package cash.p.terminal.modules.manageaccount

import android.os.Parcelable
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.modules.manageaccount.generalprivatekey.GeneralPrivateKeyPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.tangem.ui.accesscoderecovery.AccessCodeRecoverySheet
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.IAccountManager
import kotlinx.parcelize.Parcelize
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

class ManageAccountPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountContent(navigation, input)
    }

    @Parcelize
    data class Input(val accountId: String) : Parcelable
}

@Composable
private fun ManageAccountContent(navigation: HSNavigation, input: ManageAccountPage.Input) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountManager: IAccountManager by inject(IAccountManager::class.java)
    val account = remember { accountManager.account(input.accountId) }
    if (account == null) {
        LaunchedEffect(Unit) {
            HudHelper.showErrorMessage(view, view.context.getString(R.string.error_no_active_account))
            navigation.navigateUp()
        }
        return
    }
    val viewModel = viewModel<ManageAccountViewModel>(factory = ManageAccountModule.Factory(account))

    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.showAccessCodeRecoveryDialog.collect { card ->
                navigation.slideFromRightForResult<AccessCodeRecoverySheet.Result>(
                    AccessCodeRecoverySheet(
                        AccessCodeRecoverySheet.Input(card.userSettings.isUserCodeRecoveryAllowed)
                    )
                ) {

                }
            }
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
                    navigation = navigation,
                    view = view,
                    key = viewModel.getMoneroViewKey(),
                    titleRes = R.string.view_key
                )

                ManageAccountModule.KeyAction.SpendKey -> openMoneroKey(
                    navigation = navigation,
                    view = view,
                    key = viewModel.getMoneroSpendKey(),
                    titleRes = R.string.spend_key
                )

                else -> viewModel.onActionClick(action)
            }
        }
    )
}

private fun openMoneroKey(
    navigation: HSNavigation,
    view: View,
    key: String?,
    @StringRes titleRes: Int
) {
    if (key == null) {
        HudHelper.showErrorMessage(view, view.context.getString(R.string.monero_keys_unavailable))
        return
    }
    navigation.authorizedAction {
        navigation.slideFromRight(
            GeneralPrivateKeyPage(GeneralPrivateKeyPage.Input(key, view.context.getString(titleRes)))
        )
    }
}
