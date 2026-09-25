package cash.p.terminal.modules.eip20approve

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.eip20approve.AllowanceMode.OnlyRequired
import cash.p.terminal.modules.eip20approve.AllowanceMode.Unlimited
import cash.p.terminal.modules.evmfee.Cautions
import cash.p.terminal.modules.multiswap.TokenRow
import cash.p.terminal.modules.multiswap.TokenRowUnlimited
import cash.p.terminal.modules.fee.DataFieldFee
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.offline.rememberOfflineGatedAction
import cash.p.terminal.modules.send.rememberExistingViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.ui.compose.components.TransactionInfoAddressCell
import cash.p.terminal.ui.compose.components.TransactionInfoContactCell
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.chartview.cell.BoxBorderedTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class Eip20ApproveConfirmPage(val input: Eip20ApprovePage.Input) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        Eip20ApproveConfirmScreen(navigation)
    }

    @Parcelize
    data class Result(val approved: Boolean) : Parcelable
}

@Composable
internal fun Eip20ApproveConfirmScreen(navigation: HSNavigation) {
    val viewModel = navigation.rememberExistingViewModel(
        Eip20ApprovePage::class,
        Eip20ApproveViewModel::class
    ) ?: return

    val uiState = viewModel.uiState
    val view = LocalView.current

    LaunchedEffect(viewModel) {
        viewModel.restoreApproveTransaction()?.let { message ->
            HudHelper.showErrorMessage(view, message)
        }
    }

    ConfirmTransactionScreen(
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = {
            navigation.slideFromRight(Eip20ApproveTransactionSettingsPage(uiState.toInput()))
        },
        onClickClose = {
            navigation.removeLastUntil(Eip20ApprovePage::class, true)
        },
        buttonsSlot = {
            Eip20ApproveConfirmButtons(
                onApprove = viewModel::approve,
                onResult = navigation::finishApproveFlow,
                onCancel = {
                    navigation.removeLastUntil(Eip20ApprovePage::class, true)
                },
                wallet = viewModel.wallet,
                approveAvailability = uiState.approveAvailability,
                preparing = uiState.preparing
            )
        }
    ) {
        Eip20ApproveConfirmContent(uiState, navigation)
    }
}

private fun HSNavigation.finishApproveFlow(result: Eip20ApproveConfirmPage.Result) {
    if (!navigateUp()) return

    // The approve page is current after popping confirm, so this targets the original swap caller.
    setResult(checkNotNull(lastOrNull()), result)
    navigateUp()
}

@Composable
private fun Eip20ApproveConfirmButtons(
    onApprove: suspend () -> Unit,
    onResult: (Eip20ApproveConfirmPage.Result) -> Unit,
    onCancel: () -> Unit,
    wallet: Wallet?,
    approveAvailability: OperationAvailability,
    preparing: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    var buttonEnabled by remember { mutableStateOf(true) }
    val view = LocalView.current
    val offlineGatedAction = rememberOfflineGatedAction(wallet)

    Column {
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Swap_Approve),
            onClick = {
                offlineGatedAction.onClick(approveAvailability) {
                    coroutineScope.launch {
                        buttonEnabled = false
                        val currentSnackbar = HudHelper.showInProcessMessage(
                            view,
                            R.string.Swap_Approving,
                            SnackbarDuration.INDEFINITE
                        )

                        val result = try {
                            onApprove()

                            HudHelper.showSuccessMessage(view, R.string.Hud_Text_Done)
                            delay(1200)
                            Eip20ApproveConfirmPage.Result(true)
                        } catch (e: TrezorCancelledException) {
                            currentSnackbar?.dismiss()
                            Eip20ApproveConfirmPage.Result(false)
                        } catch (t: Throwable) {
                            if (t.isHardwareWalletUserCancelled()) {
                                currentSnackbar?.dismiss()
                            } else {
                                val msg = (t as? IllegalStateException)?.message ?: t.javaClass.simpleName
                                HudHelper.showErrorMessage(view, msg)
                            }
                            Eip20ApproveConfirmPage.Result(false)
                        }

                        buttonEnabled = true
                        onResult(result)
                    }
                }
            },
            enabled = approveAvailability.clickable && buttonEnabled && !preparing,
            loadingIndicator = preparing
        )
        VSpacer(16.dp)
        ButtonPrimaryDefault(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.Button_Cancel),
            onClick = onCancel
        )
    }
    offlineGatedAction.Sheet()
}

@Composable
private fun Eip20ApproveConfirmContent(
    uiState: Eip20ApproveUiState,
    navigation: HSNavigation
) {
    Eip20ApproveTokenSection(uiState, navigation)

    VSpacer(height = 16.dp)
    SectionUniversalLawrence {
        DataFieldFee(
            uiState.networkFee?.primary?.getFormattedPlain() ?: "---",
            uiState.networkFee?.secondary?.getFormattedPlain() ?: "---"
        )
    }

    if (uiState.cautions.isNotEmpty()) {
        Cautions(cautions = uiState.cautions)
    }
}

@Composable
private fun Eip20ApproveTokenSection(
    uiState: Eip20ApproveUiState,
    navigation: HSNavigation
) {
    SectionUniversalLawrence {
        when (uiState.allowanceMode) {
            OnlyRequired -> {
                TokenRow(
                    token = uiState.token,
                    amount = uiState.requiredAllowance,
                    fiatAmount = uiState.fiatAmount,
                    currency = uiState.currency,
                    borderTop = false,
                    title = stringResource(R.string.Approve_YouApprove),
                    amountColor = ComposeAppTheme.colors.leah
                )
            }

            Unlimited -> {
                TokenRowUnlimited(
                    token = uiState.token,
                    borderTop = false,
                    title = stringResource(R.string.Approve_YouApprove),
                    amountColor = ComposeAppTheme.colors.leah
                )
            }
        }

        BoxBorderedTop {
            TransactionInfoAddressCell(
                title = stringResource(R.string.Approve_Spender),
                value = uiState.spenderAddress,
                showAdd = uiState.contact == null,
                blockchainType = uiState.token.blockchainType,
                navigation = navigation
            )
        }

        uiState.contact?.let {
            BoxBorderedTop {
                TransactionInfoContactCell(it.name)
            }
        }
    }
}

private fun Eip20ApproveUiState.toInput() = Eip20ApprovePage.Input(
    token,
    requiredAllowance,
    spenderAddress,
    allowanceMode
)
