package cash.p.terminal.modules.addtoken

import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.TitleValueCell
import io.horizontalsystems.core.common.CommonParcelable
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.addtoken.blockchainselector.AddTokenBlockchainSelectorPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.resources.Button_Add
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
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
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.resources.AddToken_AddressOrSymbol
import cash.p.terminal.resources.AddToken_Blockchain
import cash.p.terminal.resources.AddToken_CoinCode
import cash.p.terminal.resources.AddToken_CoinName
import cash.p.terminal.resources.AddToken_Decimals
import cash.p.terminal.resources.AddToken_Title
import cash.p.terminal.resources.Hud_Text_Done
import cash.p.terminal.resources.ic_blocks_24
import cash.p.terminal.resources.ic_down_arrow_20

class AddTokenPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AddTokenScreen(
            navigation = navigation,
            page = this,
            viewModel = koinViewModel(),
        )
    }

    @CommonParcelize
    data class Result(val success: Boolean, val tokenToEnable: Token? = null) : CommonParcelable
}

@Composable
private fun AddTokenScreen(
    navigation: HSNavigation,
    page: AddTokenPage,
    viewModel: AddTokenViewModel,
) {
    val uiState = viewModel.uiState
    val hud = rememberHudMessenger()
    val doneText = stringResource(Res.string.Hud_Text_Done)
    val blockchainLauncher = navigation.rememberResultLauncher<AddTokenBlockchainSelectorPage.Result> {
        viewModel.onBlockchainSelect(it.blockchain)
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            val tokenToEnable = uiState.tokenToEnable
            // Hardware wallets defer creation to the scan pipeline, so nothing is added yet:
            // skip the "Done" confirmation and hand the token back to the caller.
            if (tokenToEnable == null) {
                hud.showSuccess(doneText, SnackbarDuration.MEDIUM)
                delay(300)
            }
            navigation.setResult(page, AddTokenPage.Result(success = true, tokenToEnable = tokenToEnable))
            // State-driven one-shot close: raw call, not the safe wrapper (CLAUDE.md navigation rule).
            navigation.navigateUp()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.AddToken_Title),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Add),
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
                            blockchainLauncher.slideFromRight(
                                AddTokenBlockchainSelectorPage(viewModel.blockchains, viewModel.selectedBlockchain)
                            )
                        }
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_blocks_24),
                            contentDescription = null
                        )
                        HSpacer(16.dp)
                        body_leah(
                            text = stringResource(Res.string.AddToken_Blockchain),
                            modifier = Modifier.weight(1f)
                        )
                        subhead1_grey(
                            text = viewModel.selectedBlockchain.name,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Icon(
                            painter = painterResource(Res.drawable.ic_down_arrow_20),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.iconSecondary
                        )
                    }
                }
            )

            VSpacer(32.dp)

            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                enabled = false,
                hint = stringResource(Res.string.AddToken_AddressOrSymbol),
                state = getState(uiState.caution, uiState.loading),
                qrScannerEnabled = true,
                navigation = navigation,
                qrScannerTitle = stringResource(Res.string.AddToken_AddressOrSymbol),
            ) {
                viewModel.onEnterText(it)
            }

            VSpacer(32.dp)

            uiState.tokenInfo?.let { tokenInfo ->
                CellUniversalLawrenceSection(
                    listOf(
                        {
                            TitleValueCell(
                                stringResource(Res.string.AddToken_CoinName),
                                tokenInfo.token.coin.name
                            )
                        }, {
                            TitleValueCell(
                                stringResource(Res.string.AddToken_CoinCode),
                                tokenInfo.token.coin.code
                            )
                        }, {
                            TitleValueCell(
                                stringResource(Res.string.AddToken_Decimals),
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
