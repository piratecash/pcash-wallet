package cash.p.terminal.modules.sendtokenselect

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.offline.OfflineBlockedBottomSheet
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.tokenselect.TokenSelectScreen
import cash.p.terminal.modules.tokenselect.TokenSelectViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

class SendTokenSelectPage(val input: Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val blockchainTypes = input?.blockchainTypes
        val tokenTypes = input?.tokenTypes
        val prefilledData = input?.prefilledData
        val view = LocalView.current
        val viewModel: TokenSelectViewModel =
            viewModel(factory = TokenSelectViewModel.FactoryForSend(blockchainTypes, tokenTypes))
        var blockedWallet by remember { mutableStateOf<Wallet?>(null) }

        val onClickItem: (BalanceViewItem2) -> Unit = { viewItem ->
            when {
                viewItem.sendAvailability == OperationAvailability.BlockedOffline ->
                    blockedWallet = viewItem.wallet

                viewItem.sendAvailability == OperationAvailability.Available ->
                    navigation.navigateToSend(viewItem.wallet)

                viewItem.syncingProgress.progress != null ->
                    HudHelper.showWarningMessage(view, R.string.Hud_WaitForSynchronization)

                viewItem.errorMessage != null ->
                    HudHelper.showErrorMessage(view, viewItem.errorMessage)
            }
        }

        TokenSelectScreen(
            navigation = navigation,
            title = stringResource(R.string.Balance_Send),
            searchHintText = stringResource(R.string.Balance_SendHint_CoinName),
            onClickItem = onClickItem,
            onBalanceClick = { viewItem ->
                if (viewModel.balanceHidden) {
                    viewModel.onBalanceClick(viewItem)
                } else {
                    onClickItem(viewItem)
                }
            },
            uiState = viewModel.uiState,
            updateFilter = viewModel::updateFilter,
            emptyItemsText = stringResource(R.string.Balance_NoAssetsToSend)
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
        val sendTitle = Translator.getString(R.string.Send_Title, wallet.token.fullCoin.coin.code)
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

    @Parcelize
    data class Input(
        val blockchainTypes: List<BlockchainType>?,
        val tokenTypes: List<TokenType>?,
        val prefilledData: PrefilledData,
    ) : Parcelable
}

@Parcelize
data class PrefilledData(
    val address: String?,
    val amount: BigDecimal? = null,
    val memo: String? = null,
) : Parcelable {
    companion object {
        fun from(addressUri: AddressUri) = PrefilledData(
            address = addressUri.address,
            amount = addressUri.amount,
            memo = addressUri.value(AddressUri.Field.Memo),
        )
    }
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
