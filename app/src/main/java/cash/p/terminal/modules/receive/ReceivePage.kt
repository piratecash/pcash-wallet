package cash.p.terminal.modules.receive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.receive.ui.ReceiveAddressScreen
import cash.p.terminal.modules.receive.ui.UsedAddressesParams
import cash.p.terminal.modules.receive.viewmodels.ReceiveAddressViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_grey50
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.reflect.KClass

class ReceivePage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val wallet = input.wallet
        val token = wallet.token
        when (token.blockchainType) {
            BlockchainType.Stellar -> {
                if (token.type is TokenType.Asset) {
                    ReceiveStellarAssetScreen(navigation, wallet, input.receiveEntryPoint)
                } else if (token.type == TokenType.Native) {
                    ReceiveScreen(
                        navigation,
                        wallet,
                        input.receiveEntryPoint
                    )
                }
            }
//        BlockchainType.ArbitrumOne -> TODO()
//        BlockchainType.Avalanche -> TODO()
//        BlockchainType.Base -> TODO()
//        BlockchainType.BinanceSmartChain -> TODO()
//        BlockchainType.Bitcoin -> TODO()
//        BlockchainType.BitcoinCash -> TODO()
//        BlockchainType.Dash -> TODO()
//        BlockchainType.ECash -> TODO()
//        BlockchainType.Ethereum -> TODO()
//        BlockchainType.Fantom -> TODO()
//        BlockchainType.Gnosis -> TODO()
//        BlockchainType.Litecoin -> TODO()
//        BlockchainType.Optimism -> TODO()
//        BlockchainType.Polygon -> TODO()
//        BlockchainType.Solana -> TODO()
//        BlockchainType.Ton -> TODO()
//        BlockchainType.Tron -> TODO()
//        is BlockchainType.Unsupported -> TODO()
//        BlockchainType.Zcash -> TODO()
//        BlockchainType.ZkSync -> TODO()
            BlockchainType.Monero -> {
                ReceiveMoneroScreen(navigation, wallet, input.receiveEntryPoint)
            }

            else -> {
                ReceiveScreen(
                    navigation,
                    wallet,
                    input.receiveEntryPoint
                )
            }
        }
    }

    data class Input(val wallet: Wallet, val receiveEntryPoint: KClass<out HSPage>? = null)

}

@Composable
fun ReceiveScreen(navigation: HSNavigation, wallet: Wallet, receiveEntryPoint: KClass<out HSPage>?) {
    val addressViewModel =
        viewModel<ReceiveAddressViewModel>(factory = ReceiveModule.Factory(wallet))

    val uiState = addressViewModel.uiState
    ReceiveAddressScreen(
        title = stringResource(R.string.Deposit_Title, wallet.coin.code),
        uiState = uiState,
        setAmount = { amount -> addressViewModel.setAmount(amount) },
        onErrorClick = { addressViewModel.onErrorClick() },
        topContent = {
            if (uiState.isAddressHistorySupported) {
                val hasHistory =
                    uiState.usedAddresses.isNotEmpty() || uiState.usedChangeAddresses.isNotEmpty()
                UsedAddressesRow(
                    enabled = hasHistory,
                    onClick = if (hasHistory) {
                        {
                            navigation.slideFromRight(
                                BtcUsedAddressesPage(
                                    UsedAddressesParams(
                                        wallet.coin.name,
                                        uiState.usedAddresses,
                                        uiState.usedChangeAddresses
                                    )
                                )
                            )
                        }
                    } else null
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = ComposeAppTheme.colors.steel20,
                )
            }
        },
        onBackPress = navigation::navigateUpSafely,
        closeModule = { navigation.closeReceiveModule(receiveEntryPoint) }
    )
}

internal fun HSNavigation.closeReceiveModule(receiveEntryPoint: KClass<out HSPage>?) {
    if (receiveEntryPoint == null) {
        navigateUpSafely()
    } else {
        removeLastUntil(receiveEntryPoint, true)
    }
}

@Composable
fun UsedAddressesRow(
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    RowUniversal(
        modifier = modifier
            .padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        if (enabled) {
            body_grey(
                modifier = Modifier
                    .weight(1f),
                text = stringResource(R.string.Balance_Receive_UsedAddresses),
            )
        } else {
            body_grey50(
                modifier = Modifier
                    .weight(1f),
                text = stringResource(R.string.Balance_Receive_UsedAddresses),
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = if (enabled) ComposeAppTheme.colors.grey else ComposeAppTheme.colors.grey50,
        )
    }
}
