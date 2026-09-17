package cash.p.terminal.modules.manageaccount

import android.os.Parcelable
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.modules.manageaccount.generalprivatekey.GeneralPrivateKeyFragment
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.tangem.ui.accesscoderecovery.AccessCodeRecoveryDialog
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.navigation.slideFromRightForResult
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.IAccountManager
import kotlinx.parcelize.Parcelize
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

class ManageAccountFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Input>(navController) { input ->
            ManageAccountContent(navController, input)
        }
    }

    @Composable
    private fun ManageAccountContent(navController: NavController, input: Input) {
        val view = LocalView.current
        val accountManager: IAccountManager by inject(IAccountManager::class.java)
        val account = remember { accountManager.account(input.accountId) }
        if (account == null) {
            LaunchedEffect(Unit) {
                HudHelper.showErrorMessage(view, getString(R.string.error_no_active_account))
                navController.popBackStack()
            }
            return
        }
        val viewModel = viewModel<ManageAccountViewModel>(factory = ManageAccountModule.Factory(account))

        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showAccessCodeRecoveryDialog.collect { card ->
                    navController.slideFromRightForResult<AccessCodeRecoveryDialog.Result>(
                        resId = R.id.accessCodeRecoveryDialog,
                        input = AccessCodeRecoveryDialog.Input(card.userSettings.isUserCodeRecoveryAllowed)
                    ) {

                    }
                }
            }
        }

        ManageAccountScreen(
            navController = navController,
            viewState = viewModel.viewState,
            account = viewModel.account,
            onCloseClicked = viewModel::onClose,
            onSaveClicked = viewModel::onSave,
            onNameChanged = viewModel::onChange,
            onActionClick = { action ->
                when (action) {
                    ManageAccountModule.KeyAction.ViewKey -> openMoneroKey(
                        navController = navController,
                        view = view,
                        key = viewModel.getMoneroViewKey(),
                        titleRes = R.string.view_key
                    )

                    ManageAccountModule.KeyAction.SpendKey -> openMoneroKey(
                        navController = navController,
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
        navController: NavController,
        view: View,
        key: String?,
        @StringRes titleRes: Int
    ) {
        if (key == null) {
            HudHelper.showErrorMessage(view, getString(R.string.monero_keys_unavailable))
            return
        }
        navController.authorizedAction {
            navController.slideFromRight(
                R.id.generalPrivateKeyFragment,
                GeneralPrivateKeyFragment.Input(key, getString(titleRes))
            )
        }
    }

    @Parcelize
    data class Input(val accountId: String) : Parcelable
}
