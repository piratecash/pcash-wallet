package cash.p.terminal.modules.settings.donate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.modules.offline.OfflineBlockedBottomSheet
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.tokenselect.TokenSelectScreen
import cash.p.terminal.modules.tokenselect.TokenSelectViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Balance_NoAssetsToSend
import cash.p.terminal.resources.Settings_DonateToken
import cash.p.terminal.resources.Settings_DonateWith
import cash.p.terminal.resources.Settings_Donate_GetAddress
import cash.p.terminal.resources.Settings_Donate_Info
import cash.p.terminal.resources.Settings_Donate_OrSelectCoinToDonate
import cash.p.terminal.resources.ic_heart_filled_24
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class DonateTokenSelectPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<TokenSelectViewModel> { parametersOf(null, null) }
        val hud = rememberHudMessenger()
        var blockedWallet by remember { mutableStateOf<Wallet?>(null) }

        TokenSelectScreen(
            navigation = navigation,
            title = stringResource(Res.string.Settings_DonateWith),
            onClickItem = { viewItem ->
                if (viewItem.sendAvailability == OperationAvailability.BlockedOffline) {
                    blockedWallet = viewItem.wallet
                } else {
                    navigateToDonate(viewItem.wallet, navigation)
                }
            },
            onBalanceClick = { viewModel.onBalanceClick(it, hud::vibrate) },
            uiState = viewModel.uiState,
            updateFilter = viewModel::updateFilter,
            emptyItemsText = stringResource(Res.string.Balance_NoAssetsToSend)
        ) { DonateHeader(navigation) }

        blockedWallet?.let { wallet ->
            OfflineBlockedBottomSheet(
                wallet = wallet,
                onWentOnline = {
                    blockedWallet = null
                    navigateToDonate(wallet, navigation)
                },
                onDismiss = { blockedWallet = null },
            )
        }
    }

    private fun navigateToDonate(wallet: Wallet, navigation: HSNavigation) {
        val donateAddress = SharedAppConfig.donateAddresses[wallet.token.blockchainType] ?: return
        val sendTitle =
            Translator.getString(Res.string.Settings_DonateToken, wallet.token.fullCoin.coin.code)
        navigation.slideFromRight(
            SendPage(
                SendPage.Input(
                    wallet = wallet,
                    title = sendTitle,
                    sendEntryPoint = DonateTokenSelectPage::class,
                    prefilledData = PrefilledData(donateAddress),
                    hideAddress = true
                )
            )
        )
    }
}

@Composable
internal fun DonateHeader(navigation: HSNavigation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer(24.dp)
        headline2_leah(
            text = stringResource(Res.string.Settings_Donate_Info),
            textAlign = TextAlign.Center
        )
        VSpacer(24.dp)
        Icon(
            painter = painterResource(Res.drawable.ic_heart_filled_24),
            tint = ComposeAppTheme.colors.brandDefault,
            contentDescription = null,
        )
    }

    GetAddressCell {
        navigation.slideFromRight(DonateAddressesPage())
    }
}

@Composable
private fun GetAddressCell(
    onClick: () -> Unit
) {
    VSpacer(24.dp)
    ButtonPrimaryDefault(
        title = stringResource(Res.string.Settings_Donate_GetAddress),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        onClick = onClick
    )
    VSpacer(24.dp)
    subhead2_grey(
        text = stringResource(Res.string.Settings_Donate_OrSelectCoinToDonate),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    VSpacer(24.dp)
}
