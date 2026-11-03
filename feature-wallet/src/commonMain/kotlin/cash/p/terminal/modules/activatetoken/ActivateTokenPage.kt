package cash.p.terminal.modules.activatetoken

import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.feature.wallet.ActivateTokenResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.modules.confirm.ConfirmTransactionScreen
import cash.p.terminal.modules.fee.DataFieldFee
import cash.p.terminal.modules.offline.OfflineOperationBlockedException
import cash.p.terminal.modules.receive.ActivateTokenError
import cash.p.terminal.modules.receive.ActivateTokenViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsImageCircle
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.resources.Button_Activate
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.CoinPlatforms_Native
import cash.p.terminal.resources.Error_ParameterNotSet
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.activate_activating
import cash.p.terminal.resources.activate_already_active_description
import cash.p.terminal.resources.activate_already_active_title
import cash.p.terminal.resources.activate_insufficient_balance_description
import cash.p.terminal.resources.activate_insufficient_balance_title
import cash.p.terminal.resources.activate_you_activate
import cash.p.terminal.resources.offline_mode_operation_blocked

class ActivateTokenPage(val input: Wallet) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ActivateTokenScreen(navigation, input) { result ->
            navigation.setResult(this, result)
            navigation.navigateUp()
        }
    }
}


@Composable
fun ActivateTokenScreen(
    navigation: HSNavigation,
    wallet: Wallet,
    onResult: (ActivateTokenResult) -> Unit,
) {
    val viewModel =
        koinViewModel<ActivateTokenViewModel> { parametersOf(wallet) }

    val uiState = viewModel.uiState
    val token = uiState.token

    ConfirmTransactionScreen(
        onClickBack = {},
        onClickClose = navigation::navigateUpSafely,
        onClickSettings = null,
        buttonsSlot = {
            val coroutineScope = rememberCoroutineScope()
            var buttonEnabled by remember { mutableStateOf(true) }
            val hud = rememberHudMessenger()

            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Activate),
                onClick = {
                    coroutineScope.launch {
                        buttonEnabled = false
                        hud.showInProgress(
                            Translator.getString(Res.string.activate_activating),
                            SnackbarDuration.INDEFINITE
                        )

                        val result = try {
                            viewModel.activate()

                            hud.showSuccess(Translator.getString(Res.string.Hud_Text_Done))
                            ActivateTokenResult(true)
                        } catch (e: OfflineOperationBlockedException) {
                            hud.showError(e.message.orEmpty())
                            ActivateTokenResult(false)
                        } catch (t: Throwable) {
                            hud.showError(t.javaClass.simpleName)
                            ActivateTokenResult(false)
                        }

                        buttonEnabled = true
                        onResult(result)
                    }
                },
                enabled = uiState.activateEnabled && buttonEnabled
            )
            VSpacer(16.dp)
            ButtonPrimaryDefault(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Cancel),
                onClick = navigation::navigateUpSafely
            )
        }
    ) {
        SectionUniversalLawrence {
            CellUniversal(borderTop = false) {
                HsImageCircle(
                    modifier = Modifier.size(32.dp),
                    url = token.coin.imageUrl,
                    alternativeUrl = token.coin.alternativeImageUrl,
                    placeholder = token.iconPlaceholder
                )
                HSpacer(width = 16.dp)
                Column {
                    subhead2_leah(text = stringResource(Res.string.activate_you_activate))
                    VSpacer(height = 1.dp)
                    caption_grey(
                        text = token.badge ?: stringResource(Res.string.CoinPlatforms_Native)
                    )
                }
                HFillSpacer(minWidth = 16.dp)
                Column(horizontalAlignment = Alignment.End) {
                    subhead1_leah(
                        text = token.coin.code,
                    )
                }
            }
        }

        VSpacer(height = 16.dp)
        SectionUniversalLawrence {
            DataFieldFee(
                uiState.feeCoinValue?.getFormattedFull() ?: "---",
                uiState.feeFiatValue?.getFormattedFull() ?: "---"
            )
        }

        uiState.error?.let { error ->
            VSpacer(16.dp)
            val modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)

            when (error) {
                is ActivateTokenError.AlreadyActive -> {
                    TextImportantError(
                        modifier = modifier,
                        text = stringResource(Res.string.activate_already_active_description),
                        title = stringResource(Res.string.activate_already_active_title),
                        icon = Res.drawable.ic_attention_20
                    )
                }

                is ActivateTokenError.NullAdapter -> {
                    TextImportantError(
                        modifier = modifier,
                        text = stringResource(Res.string.Error_ParameterNotSet),
                        title = null,
                        icon = null
                    )
                }

                is ActivateTokenError.InsufficientBalance -> {
                    TextImportantError(
                        modifier = modifier,
                        title = stringResource(Res.string.activate_insufficient_balance_title),
                        text = stringResource(Res.string.activate_insufficient_balance_description),
                        icon = Res.drawable.ic_attention_20
                    )
                }

                is ActivateTokenError.Offline -> {
                    TextImportantError(
                        modifier = modifier,
                        text = stringResource(
                            Res.string.offline_mode_operation_blocked,
                            token.blockchain.name
                        ),
                        title = null,
                        icon = Res.drawable.ic_attention_20
                    )
                }
            }
        }
    }
}
