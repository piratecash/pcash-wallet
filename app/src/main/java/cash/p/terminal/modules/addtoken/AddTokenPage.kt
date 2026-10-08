package cash.p.terminal.modules.addtoken

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.addtoken.blockchainselector.AddTokenBlockchainSelectorPage
import cash.p.terminal.modules.walletconnect.session.ui.TitleValueCell
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.FormsInputStateWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.delay
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class AddTokenPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AddTokenScreen(
            navigation = navigation,
            page = this,
            viewModel = koinViewModel(),
        )
    }

    @Parcelize
    data class Result(val success: Boolean, val tokenToEnable: Token? = null) : Parcelable
}

@Composable
private fun AddTokenScreen(
    navigation: HSNavigation,
    page: AddTokenPage,
    viewModel: AddTokenViewModel,
) {
    val uiState = viewModel.uiState
    val view = LocalView.current

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            val tokenToEnable = uiState.tokenToEnable
            // Hardware wallets defer creation to the scan pipeline, so nothing is added yet:
            // skip the "Done" confirmation and hand the token back to the caller.
            if (tokenToEnable == null) {
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Done, SnackbarDuration.MEDIUM)
                delay(300)
            }
            navigation.setResult(page, AddTokenPage.Result(success = true, tokenToEnable = tokenToEnable))
            // State-driven one-shot close: raw call, not the safe wrapper (CLAUDE.md navigation rule).
            navigation.navigateUp()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.AddToken_Title),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Add),
                        onClick = viewModel::onAddClick,
                        enabled = uiState.addButtonEnabled
                    )
                )
            )
        },
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .padding(innerPaddings)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)

            CellUniversalLawrenceSection(
                listOf {
                    RowUniversal(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = {
                            navigation.slideFromRightForResult<AddTokenBlockchainSelectorPage.Result>(
                                AddTokenBlockchainSelectorPage(viewModel.blockchains, viewModel.selectedBlockchain)
                            ) {
                                viewModel.onBlockchainSelect(it.blockchain)
                            }
                        }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_blocks_24),
                            contentDescription = null
                        )
                        HSpacer(16.dp)
                        body_leah(
                            text = stringResource(R.string.AddToken_Blockchain),
                            modifier = Modifier.weight(1f)
                        )
                        subhead1_grey(
                            text = viewModel.selectedBlockchain.name,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_down_arrow_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.grey
                        )
                    }
                }
            )

            VSpacer(32.dp)

            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                enabled = false,
                hint = stringResource(R.string.AddToken_AddressOrSymbol),
                state = getState(uiState.caution, uiState.loading),
                qrScannerEnabled = true,
                navigation = navigation,
                qrScannerTitle = stringResource(R.string.AddToken_AddressOrSymbol),
            ) {
                viewModel.onEnterText(it)
            }

            VSpacer(32.dp)

            uiState.tokenInfo?.let { tokenInfo ->
                CellUniversalLawrenceSection(
                    listOf(
                        {
                            TitleValueCell(
                                stringResource(R.string.AddToken_CoinName),
                                tokenInfo.token.coin.name
                            )
                        }, {
                            TitleValueCell(
                                stringResource(R.string.AddToken_CoinCode),
                                tokenInfo.token.coin.code
                            )
                        }, {
                            TitleValueCell(
                                stringResource(R.string.AddToken_Decimals),
                                tokenInfo.token.decimals.toString()
                            )
                        }
                    )
                )
            }
        }
    }
}

private fun getState(caution: Caution?, loading: Boolean) = when (caution?.type) {
    Caution.Type.Error -> DataState.Error(Exception(caution.text))
    Caution.Type.Warning -> DataState.Error(FormsInputStateWarning(caution.text))
    null -> if (loading) DataState.Loading else null
}
