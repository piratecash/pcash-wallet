package cash.p.terminal.modules.receive.ui

import cash.p.terminal.resources.receive_address_badge_new
import cash.p.terminal.resources.receive_address_badge_unused
import cash.p.terminal.resources.receive_address_badge_used
import cash.p.terminal.resources.receive_create_new_address
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.receive.ReceiveModule
import cash.p.terminal.modules.receive.ReceiveModule.UiState
import cash.p.terminal.modules.receive.UsedAddressesRow
import cash.p.terminal.modules.receive.viewmodels.AddressBadge
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.UsedAddress

@Suppress("UnusedPrivateMember", "LongMethod")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReceiveAddressScreenPreview() {
    val addressBadge = AddressBadge.USED
    ComposeAppTheme {
        ReceiveAddressScreen(
            title = "Receive BTC",
            uiState = UiState(
                viewState = ViewState.Success,
                address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                mainNet = true,
                usedAddresses = listOf(
                    UsedAddress(
                        0,
                        "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                        "https:/some-url.com",
                    )
                ),
                usedChangeAddresses = listOf(
                    UsedAddress(
                        0,
                        "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                        "https:/some-url.com",
                    )
                ),
                isAddressHistorySupported = true,
                showTronAlert = false,
                beamReceiveType = null,
                uri = "bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                blockchainName = "Bitcoin",
                addressFormat = null,
                watchAccount = false,
                additionalItems = emptyList(),
                amount = null,
                alertText = null,
            ),
            topContent = {
                UsedAddressesRow(
                    enabled = true,
                    onClick = {},
                )
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = ComposeAppTheme.colors.borderDivider,
                )
            },
            addressBadge = {
                val badgeText = when (addressBadge) {
                    AddressBadge.NEW -> stringResource(Res.string.receive_address_badge_new)
                    AddressBadge.USED -> stringResource(Res.string.receive_address_badge_used)
                    AddressBadge.UNUSED -> stringResource(Res.string.receive_address_badge_unused)
                }
                val badgeColor = when (addressBadge) {
                    AddressBadge.NEW -> ComposeAppTheme.colors.statusSuccess
                    AddressBadge.USED -> ComposeAppTheme.colors.statusWarning
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
            },
            bottomContent = {
                ButtonPrimaryTransparent(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    title = stringResource(Res.string.receive_create_new_address),
                    onClick = {},
                )
            },
            setAmount = {},
            onBackPress = {},
            closeModule = {},
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReceiveAddressScreenBeamSbbsPreview() {
    ComposeAppTheme {
        ReceiveAddressScreen(
            title = "Receive BEAM",
            uiState = UiState(
                viewState = ViewState.Success,
                address = "e17cc06481d9ae88e1e0181efee407fa8c36a861b9df723845eddc8fb1ba552048",
                mainNet = true,
                usedAddresses = emptyList(),
                usedChangeAddresses = emptyList(),
                isAddressHistorySupported = false,
                showTronAlert = false,
                beamReceiveType = ReceiveModule.BeamReceiveType.Sbbs,
                uri = "e17cc06481d9ae88e1e0181efee407fa8c36a861b9df723845eddc8fb1ba552048",
                blockchainName = "Beam",
                addressFormat = null,
                watchAccount = false,
                additionalItems = emptyList(),
                amount = null,
                alertText = ReceiveModule.AlertText.Normal("The receiver must be online to receive this payment"),
            ),
            setAmount = {},
            onBackPress = {},
            closeModule = {},
        )
    }
}
