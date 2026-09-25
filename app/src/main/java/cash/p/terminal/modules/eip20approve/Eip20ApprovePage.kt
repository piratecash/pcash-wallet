package cash.p.terminal.modules.eip20approve

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

class Eip20ApprovePage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        Eip20ApproveScreen(navigation, input)
    }

    @Parcelize
    data class Input(
        val token: Token,
        val requiredAllowance: BigDecimal,
        val spenderAddress: String,
        val allowanceMode: AllowanceMode = AllowanceMode.OnlyRequired
    ) : Parcelable
}

@Composable
fun Eip20ApproveScreen(navigation: HSNavigation, input: Eip20ApprovePage.Input) {
    val viewModel = viewModel<Eip20ApproveViewModel>(factory = Eip20ApproveViewModel.Factory(input))

    val uiState = viewModel.uiState
    val view = LocalView.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is Eip20ApproveViewModel.Event.NavigateToConfirm -> {
                    navigation.slideFromRight(Eip20ApproveConfirmPage(event.input))
                }

                is Eip20ApproveViewModel.Event.ShowError -> {
                    HudHelper.showErrorMessage(view, event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.Swap_Unlock_PageTitle),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Close),
                        icon = R.drawable.ic_close_24,
                        onClick = navigation::navigateUpSafely
                    )
                )
            )
        },
        containerColor = ComposeAppTheme.colors.tyler,
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight()
        ) {
            VSpacer(height = 12.dp)
            headline1_leah(
                text = stringResource(R.string.Swap_Unlock_Subtitle),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            VSpacer(height = 24.dp)

            SectionUniversalLawrence {
                val allowanceModeEnabled = !uiState.preparing
                val setOnlyRequired = { viewModel.setAllowanceMode(AllowanceMode.OnlyRequired) }
                CellUniversal(
                    onClick = setOnlyRequired.takeIf { allowanceModeEnabled }
                ) {
                    HsCheckbox(
                        checked = uiState.allowanceMode == AllowanceMode.OnlyRequired,
                        enabled = allowanceModeEnabled,
                        onCheckedChange = { setOnlyRequired.invoke() }
                    )
                    HSpacer(width = 16.dp)
                    val coinValue = CoinValue(
                        uiState.token,
                        uiState.requiredAllowance
                    ).getFormattedFull()
                    subhead2_leah(text = coinValue)
                }

                val setUnlimited = { viewModel.setAllowanceMode(AllowanceMode.Unlimited) }
                CellUniversal(
                    borderTop = true,
                    onClick = setUnlimited.takeIf { allowanceModeEnabled }
                ) {
                    HsCheckbox(
                        checked = uiState.allowanceMode == AllowanceMode.Unlimited,
                        enabled = allowanceModeEnabled,
                        onCheckedChange = { setUnlimited.invoke() }
                    )
                    HSpacer(width = 16.dp)
                    subhead2_leah(text = stringResource(id = R.string.Swap_Unlock_Unlimited))
                }
            }
            InfoText(text = stringResource(R.string.Swap_Unlock_Info))
            Spacer(Modifier.weight(1f))

            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                title = stringResource(R.string.Button_Next),
                onClick = viewModel::prepareApprove,
                enabled = !uiState.preparing,
                loadingIndicator = uiState.preparing,
            )
        }
    }
}
