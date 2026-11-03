package cash.p.terminal.modules.sendtokenselect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.offline.OfflineBlockedBottomSheet
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.tokenselect.TokenSelectScreen
import cash.p.terminal.modules.tokenselect.TokenSelectViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Balance_NoAssetsToSend
import cash.p.terminal.resources.Balance_Send
import cash.p.terminal.resources.Balance_SendHint_CoinName
import cash.p.terminal.resources.Hud_WaitForSynchronization
import cash.p.terminal.resources.Send_Title

class SendTokenSelectPage(val input: Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val blockchainTypes = input?.blockchainTypes
        val tokenTypes = input?.tokenTypes
        val prefilledData = input?.prefilledData
        val viewModel = koinViewModel<TokenSelectViewModel> { parametersOf(blockchainTypes, tokenTypes) }
        val hud = rememberHudMessenger()
        var blockedWallet by remember { mutableStateOf<Wallet?>(null) }

        val onClickItem: (BalanceViewItem2) -> Unit = { viewItem ->
            when {
                viewItem.sendAvailability == OperationAvailability.BlockedOffline ->
                    blockedWallet = viewItem.wallet

                viewItem.sendAvailability == OperationAvailability.Available ->
                    navigation.navigateToSend(viewItem.wallet)

                viewItem.syncingProgress.progress != null ->
                    hud.showWarning(Translator.getString(Res.string.Hud_WaitForSynchronization))

                viewItem.errorMessage != null ->
                    hud.showError(viewItem.errorMessage.orEmpty())
            }
        }

        TokenSelectScreen(
            navigation = navigation,
            title = stringResource(Res.string.Balance_Send),
            searchHintText = stringResource(Res.string.Balance_SendHint_CoinName),
            onClickItem = onClickItem,
            onBalanceClick = { viewItem ->
                if (viewModel.balanceHidden) {
                    viewModel.onBalanceClick(viewItem, hud::vibrate)
                } else {
                    onClickItem(viewItem)
                }
            },
            uiState = viewModel.uiState,
            updateFilter = viewModel::updateFilter,
            emptyItemsText = stringResource(Res.string.Balance_NoAssetsToSend)
        )

        blockedWallet?.let { wallet ->
            OfflineBlockedBottomSheet(
                wallet = wallet,
                onWentOnline = {
                    blockedWallet = null
                    navigation.navigateToSend(wallet)
                },
                onDismiss = { blockedWallet = null },
            )
        }
    }

    private fun HSNavigation.navigateToSend(wallet: Wallet) {
        val sendTitle = Translator.getString(Res.string.Send_Title, wallet.token.fullCoin.coin.code)
        slideFromRight(
            SendPage(
                input?.toSendInput(wallet, sendTitle)
                    ?: SendPage.Input(
                        wallet = wallet,
                        title = sendTitle,
                        sendEntryPoint = SendTokenSelectPage::class,
                    )
            )
        )
    }

    data class Input(
        val blockchainTypes: List<BlockchainType>?,
        val tokenTypes: List<TokenType>?,
        val prefilledData: PrefilledData,
    )
}

internal fun SendTokenSelectPage.Input.toSendInput(
    wallet: Wallet,
    title: String,
) = SendPage.Input(
    wallet = wallet,
    title = title,
    sendEntryPoint = SendTokenSelectPage::class,
    prefilledData = prefilledData,
)
