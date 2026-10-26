package cash.p.terminal.modules.receive

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.receive.ui.AddressBadgeChip
import cash.p.terminal.modules.receive.ui.ReceiveAddressScreen
import cash.p.terminal.modules.receive.viewmodels.AddressBadge
import cash.p.terminal.modules.receive.viewmodels.MoneroSubaddressParcelable
import cash.p.terminal.modules.receive.viewmodels.MoneroUsedAddressesParams
import cash.p.terminal.modules.receive.viewmodels.ReceiveMoneroUiState
import cash.p.terminal.modules.receive.viewmodels.ReceiveMoneroViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsRadioButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Deposit_Title
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.monero_show_address_on_trezor
import cash.p.terminal.resources.monero_update_with_trezor
import cash.p.terminal.resources.receive_address_badge_new
import cash.p.terminal.resources.receive_address_badge_unused
import cash.p.terminal.resources.receive_address_badge_used
import cash.p.terminal.resources.receive_create_another_address_title
import cash.p.terminal.resources.receive_create_another_address_warning
import cash.p.terminal.resources.receive_create_new
import cash.p.terminal.resources.receive_create_new_address
import cash.p.terminal.resources.receive_dont_ask_again
import cash.p.terminal.resources.receive_keep_current

@Composable
fun ReceiveMoneroScreen(
    navigation: HSNavigation,
    wallet: Wallet,
    receiveEntryPoint: KClass<out HSPage>?,
) {
    val viewModel: ReceiveMoneroViewModel = koinViewModel { parametersOf(wallet) }
    val uiState = viewModel.uiState
    var showConfirmDialog by remember { mutableStateOf(false) }

    ReceiveAddressScreen(
        title = stringResource(Res.string.Deposit_Title, wallet.coin.code),
        uiState = uiState,
        setAmount = viewModel::setAmount,
        onErrorClick = viewModel::onErrorClick,
        topContent = { MoneroTopContent(uiState, viewModel, navigation) },
        addressBadge = { MoneroAddressBadge(uiState.addressBadge) },
        bottomContent = {
            MoneroBottomContent(uiState, viewModel) { showConfirmDialog = true }
        },
        onBackPress = navigation::navigateUpSafely,
        closeModule = { navigation.closeReceiveModule(receiveEntryPoint) }
    )

    if (showConfirmDialog) {
        ConfirmNewAddressBottomSheet(
            onConfirm = { dontAskAgain ->
                if (dontAskAgain) {
                    viewModel.setSkipNewAddressConfirm(true)
                }
                viewModel.createNewAddress()
                showConfirmDialog = false
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

@Composable
private fun MoneroTopContent(
    uiState: ReceiveMoneroUiState,
    viewModel: ReceiveMoneroViewModel,
    navigation: HSNavigation,
) {
    UsedAddressesRow(
        enabled = uiState.hasAddressHistory,
        onClick = if (uiState.hasAddressHistory) {
            {
                val params = MoneroUsedAddressesParams(
                    subaddresses = viewModel.getSubaddressesForHistory().map {
                        MoneroSubaddressParcelable(it.index, it.address, it.receivedAmount)
                    }
                )
                navigation.slideFromRight(MoneroUsedAddressesPage(params))
            }
        } else null
    )
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = ComposeAppTheme.colors.borderDivider,
    )
}

@Composable
private fun MoneroAddressBadge(badge: AddressBadge) {
    val badgeText = when (badge) {
        AddressBadge.NEW -> stringResource(Res.string.receive_address_badge_new)
        AddressBadge.USED -> stringResource(Res.string.receive_address_badge_used)
        AddressBadge.UNUSED -> stringResource(Res.string.receive_address_badge_unused)
    }
    val badgeColor = when (badge) {
        AddressBadge.NEW -> ComposeAppTheme.colors.statusSuccess
        AddressBadge.USED -> ComposeAppTheme.colors.brandDefault
        AddressBadge.UNUSED -> ComposeAppTheme.colors.textSecondary
    }
    VSpacer(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        AddressBadgeChip(text = badgeText, color = badgeColor)
    }
    VSpacer(4.dp)
}

@Composable
private fun MoneroBottomContent(
    uiState: ReceiveMoneroUiState,
    viewModel: ReceiveMoneroViewModel,
    onShowConfirmDialog: () -> Unit,
) {
    if (uiState.hardwareWallet) {
        MoneroHardwareWalletActions(
            uiState = uiState,
            onSyncKeyImages = viewModel::refreshWithTrezor,
            onDisplayAddress = viewModel::displayAddressOnDevice,
        )
    }
    if (!uiState.watchAccount) {
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            title = stringResource(Res.string.receive_create_new_address),
            enabled = !uiState.isCreatingAddress,
            onClick = {
                if (uiState.addressBadge == AddressBadge.NEW && !viewModel.skipNewAddressConfirm) {
                    onShowConfirmDialog()
                } else {
                    viewModel.createNewAddress()
                }
            }
        )
    }
}

@Composable
private fun MoneroHardwareWalletActions(
    uiState: ReceiveMoneroUiState,
    onSyncKeyImages: () -> Unit,
    onDisplayAddress: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        uiState.hardwareOperationError?.let { error ->
            TextImportantWarning(
                modifier = Modifier.padding(vertical = 12.dp),
                text = TranslatableString.ResString(error).getString(),
            )
        }
        if (uiState.showTrezorUpdateAction) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.monero_update_with_trezor),
                enabled = !uiState.isHardwareOperationInProgress,
                loadingIndicator = uiState.isHardwareOperationInProgress,
                onClick = onSyncKeyImages,
            )
        }
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            title = stringResource(Res.string.monero_show_address_on_trezor),
            enabled = !uiState.isHardwareOperationInProgress,
            onClick = onDisplayAddress,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmNewAddressBottomSheet(
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontAskAgain by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ConfirmNewAddressSheetContent(
            dontAskAgain = dontAskAgain,
            onDontAskAgainChange = { dontAskAgain = it },
            onConfirm = { onConfirm(dontAskAgain) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun ConfirmNewAddressSheetContent(
    dontAskAgain: Boolean,
    onDontAskAgainChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetHeader(
        iconPainter = painterResource(Res.drawable.ic_attention_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
        title = stringResource(Res.string.receive_create_another_address_title),
        onCloseClick = onDismiss,
    ) {
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = stringResource(Res.string.receive_create_another_address_warning),
        )

        VSpacer(12.dp)

        RowUniversal(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(12.dp)),
            onClick = { onDontAskAgainChange(!dontAskAgain) },
        ) {
            HsRadioButton(
                selected = dontAskAgain,
                onClick = { onDontAskAgainChange(!dontAskAgain) },
            )
            subhead2_leah(
                text = stringResource(Res.string.receive_dont_ask_again),
            )
        }

        VSpacer(32.dp)

        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = stringResource(Res.string.receive_create_new),
            onClick = onConfirm,
        )

        VSpacer(12.dp)

        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = stringResource(Res.string.receive_keep_current),
            onClick = onDismiss,
        )

        Spacer(Modifier.height(32.dp))
    }
}

