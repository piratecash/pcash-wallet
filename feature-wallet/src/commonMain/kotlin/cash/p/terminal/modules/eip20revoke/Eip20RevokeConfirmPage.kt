package cash.p.terminal.modules.eip20revoke

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.evmfee.Cautions
import cash.p.terminal.modules.multiswap.TokenRow
import cash.p.terminal.modules.fee.DataFieldFee
import cash.p.terminal.modules.offline.rememberOfflineGatedAction
import cash.p.terminal.ui.compose.components.TransactionInfoAddressCell
import cash.p.terminal.ui.compose.components.TransactionInfoContactCell
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import io.horizontalsystems.chartview.cell.BoxBorderedTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Approve_Spender
import cash.p.terminal.resources.Approve_YouRevoke
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.Swap_Revoke
import cash.p.terminal.resources.Swap_Revoking
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import cash.p.terminal.core.HardwareWalletSupport

class Eip20RevokeConfirmPage(val input: Input) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        Eip20RevokeScreen(navigation, input) { result ->
            navigation.setResult(this, result)
            navigation.navigateUp()
        }
    }

    data class Input(
        val token: Token,
        val spenderAddress: String,
        val allowance: BigDecimal,
    )

    data class Result(val revoked: Boolean)
}

@Composable
fun Eip20RevokeScreen(
    navigation: HSNavigation,
    input: Eip20RevokeConfirmPage.Input,
    onResult: (Eip20RevokeConfirmPage.Result) -> Unit,
) {
    val viewModel = viewModel<Eip20RevokeConfirmViewModel>(
        factory = Eip20RevokeConfirmViewModel.Factory(input.token, input.spenderAddress, input.allowance)
    )

    val uiState = viewModel.uiState
    val hud = rememberHudMessenger()
    val offlineGatedAction = rememberOfflineGatedAction(viewModel.wallet)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is Eip20RevokeConfirmViewModel.Event.ShowError -> {
                    hud.showError(event.message)
                }
            }
        }
    }

    ConfirmTransactionScreen(
        onClickBack = navigation::navigateUpSafely,
        onClickSettings = {
            navigation.slideFromRight(Eip20RevokeTransactionSettingsPage(input))
        },
        onClickClose = navigation::navigateUpSafely,
        buttonsSlot = {
            val coroutineScope = rememberCoroutineScope()
            var buttonEnabled by remember { mutableStateOf(true) }

            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Swap_Revoke),
                onClick = {
                    offlineGatedAction.onClick(uiState.revokeAvailability) {
                        coroutineScope.launch {
                            buttonEnabled = false
                            hud.showInProgress(
                                Translator.getString(Res.string.Swap_Revoking),
                                SnackbarDuration.INDEFINITE
                            )

                            val result = try {
                                viewModel.revoke()

                                hud.showSuccess(Translator.getString(Res.string.Hud_Text_Done))
                                delay(1200)
                                Eip20RevokeConfirmPage.Result(true)
                            } catch (t: Throwable) {
                                if (HardwareWalletSupport.isTrezorCancellation(t)) {
                                    buttonEnabled = true
                                    return@launch
                                }
                                val msg =
                                    (t as? IllegalStateException)?.message ?: t.javaClass.simpleName
                                hud.showError(msg)
                                Eip20RevokeConfirmPage.Result(false)
                            }

                            buttonEnabled = true
                            onResult(result)
                        }
                    }
                },
                enabled = uiState.revokeAvailability.clickable && buttonEnabled,
                loadingIndicator = uiState.preparing,
            )
            VSpacer(16.dp)
            ButtonPrimaryDefault(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Cancel),
                onClick = {
                    navigation.navigateUpSafely()
                }
            )
        }
    ) {
        SectionUniversalLawrence {
            TokenRow(
                token = uiState.token,
                amount = uiState.allowance,
                fiatAmount = uiState.fiatAmount,
                currency = uiState.currency,
                borderTop = false,
                title = stringResource(Res.string.Approve_YouRevoke),
                amountColor = ComposeAppTheme.colors.textPrimary
            )

            BoxBorderedTop {
                TransactionInfoAddressCell(
                    title = stringResource(Res.string.Approve_Spender),
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

    offlineGatedAction.Sheet()
}
