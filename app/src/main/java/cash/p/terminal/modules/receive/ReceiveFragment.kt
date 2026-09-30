package cash.p.terminal.modules.receive

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamNetwork
import cash.p.terminal.R
import cash.p.terminal.core.titleResId
import cash.p.terminal.modules.receive.ui.ReceiveAddressScreen
import cash.p.terminal.modules.receive.ui.UsedAddressesParams
import cash.p.terminal.modules.receive.viewmodels.ReceiveAddressViewModel
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui.compose.components.SelectorDialogCompose
import cash.p.terminal.ui.compose.components.SelectorItem
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_grey50
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.parcelize.Parcelize

class ReceiveFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Input>(navController) {
            val wallet = it.wallet
            val token = wallet.token
            when (token.blockchainType) {
                BlockchainType.Stellar -> {
                    if (token.type is TokenType.Asset) {
                        ReceiveStellarAssetScreen(navController, wallet, it.receiveEntryPointDestId)
                    } else if (token.type == TokenType.Native) {
                        ReceiveScreen(
                            navController,
                            wallet,
                            it.receiveEntryPointDestId
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
                    ReceiveMoneroScreen(navController, wallet, it.receiveEntryPointDestId)
                }

                else -> {
                    ReceiveScreen(
                        navController,
                        wallet,
                        it.receiveEntryPointDestId
                    )
                }
            }
        }
    }

    @Parcelize
    data class Input(val wallet: Wallet, val receiveEntryPointDestId: Int = 0) : Parcelable

}

@Composable
fun ReceiveScreen(navController: NavController, wallet: Wallet, receiveEntryPointDestId: Int) {
    val viewModelKey = if (wallet.token.blockchainType == BlockchainType.Beam) {
        "beam-receive:${wallet.account.id}:${wallet.token.blockchainType.uid}:${BeamNetwork.Mainnet.name}"
    } else {
        null
    }
    val addressViewModel =
        viewModel<ReceiveAddressViewModel>(
            key = viewModelKey,
            factory = ReceiveModule.Factory(wallet),
        )

    val uiState = addressViewModel.uiState
    val beamTypeSelector: @Composable () -> Unit = {
        uiState.beamAddressType?.let { type ->
            BeamReceiveTypeSelector(type, addressViewModel::onBeamAddressTypeSelect)
        }
    }
    ReceiveAddressScreen(
        title = stringResource(R.string.Deposit_Title, wallet.coin.code),
        uiState = uiState,
        setAmount = { amount -> addressViewModel.setAmount(amount) },
        onErrorClick = { addressViewModel.onErrorClick() },
        allowSetAmount = wallet.token.blockchainType != BlockchainType.Beam,
        statusContent = beamTypeSelector,
        topContent = {
            ReceiveTopContent(uiState, beamTypeSelector) {
                navController.slideFromRight(
                    R.id.btcUsedAddressesFragment,
                    UsedAddressesParams(wallet.coin.name, uiState.usedAddresses, uiState.usedChangeAddresses)
                )
            }
        },
        onBackPress = { navController.popBackStackSafely() },
        closeModule = {
            if (receiveEntryPointDestId == 0) {
                navController.popBackStackSafely()
            } else {
                navController.popBackStack(receiveEntryPointDestId, true)
            }
        }
    )
}

@Composable
private fun ReceiveTopContent(
    uiState: ReceiveModule.UiState,
    beamTypeSelector: @Composable () -> Unit,
    onUsedAddressesClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.beamAddressType != null) {
            beamTypeSelector()
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = ComposeAppTheme.colors.steel20,
            )
        }
        if (uiState.isAddressHistorySupported) {
            val hasHistory = uiState.usedAddresses.isNotEmpty() || uiState.usedChangeAddresses.isNotEmpty()
            UsedAddressesRow(
                enabled = hasHistory,
                onClick = if (hasHistory) onUsedAddressesClick else null,
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = ComposeAppTheme.colors.steel20,
            )
        }
    }
}

@Composable
private fun BeamReceiveTypeSelector(
    selected: BeamAddressType,
    onSelect: (BeamAddressType) -> Unit,
) {
    var showSelector by remember { mutableStateOf(false) }
    // The right-hand half of the Appearance screen's MenuItemWithDialog: no filled background,
    // subhead1 instead of the caption ButtonSecondary draws, the same 20.dp chevron. Not reused
    // from there because that component lays out a left-hand title with weight(1f), and this
    // control has none. leah rather than the reference's grey: standing alone the row is the
    // control itself, not a value read next to a label. Centred, because the receive screen is
    // a centred column around the QR rather than a left-aligned settings list.
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = { showSelector = true },
    ) {
        Spacer(modifier = Modifier.weight(1f))
        subhead1_leah(
            text = stringResource(selected.titleResId()),
            maxLines = 1,
        )
        Image(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp),
            painter = painterResource(id = R.drawable.ic_down_arrow_20),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
    if (showSelector) {
        SelectorDialogCompose(
            title = stringResource(R.string.beam_send_receiver_type),
            items = BEAM_RECEIVE_ADDRESS_TYPES.map {
                SelectorItem(stringResource(it.titleResId()), it == selected, it)
            },
            onDismissRequest = { showSelector = false },
            onSelectItem = onSelect,
        )
    }
}

private val BEAM_RECEIVE_ADDRESS_TYPES = listOf(
    BeamAddressType.PublicOffline,
    BeamAddressType.Offline,
    BeamAddressType.MaxPrivacy,
)

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

@Composable
inline fun <reified T : ViewModel> NavBackStackEntry.sharedViewModel(
    navController: NavHostController,
): T {
    val navGraphRoute = destination.parent?.route ?: return viewModel()
    val parentEntry = remember(this) {
        navController.getBackStackEntry(navGraphRoute)
    }
    return viewModel(parentEntry)
}
