package cash.p.terminal.modules.settings.security.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.settings.displaytransactions.title
import cash.p.terminal.modules.settings.security.SecurityCenterCell
import cash.p.terminal.resources.Appearance_TransactionAutoHide
import cash.p.terminal.resources.Appearance_TransactionAutoHide_Description
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_DeleteSeparatePasscode
import cash.p.terminal.resources.SettingsSecurity_EditPin
import cash.p.terminal.resources.display
import cash.p.terminal.resources.ic_big_forward_20
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_off_24
import cash.p.terminal.resources.ic_passcode
import cash.p.terminal.resources.icon_paper_contract_24
import cash.p.terminal.resources.set_separate_passcode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceMutableSection
import cash.p.terminal.ui_compose.components.body_brand
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.managers.TransactionDisplayLevel

data class TransactionAutoHideTexts(
    val setCode: StringResource = Res.string.set_separate_passcode,
    val editCode: StringResource = Res.string.SettingsSecurity_EditPin,
    val deleteCode: StringResource = Res.string.SettingsSecurity_DeleteSeparatePasscode,
    val description: StringResource = Res.string.Appearance_TransactionAutoHide_Description,
)

@Composable
fun TransactionAutoHideBlock(
    transactionAutoHideEnabled: Boolean,
    displayLevel: TransactionDisplayLevel,
    transactionAutoHideSeparatePinExists: Boolean,
    onTransactionAutoHideEnabledChange: (Boolean) -> Unit,
    onPinClick: () -> Unit,
    onDisablePinClick: () -> Unit,
    onChangeDisplayClick: () -> Unit,
    modifier: Modifier = Modifier,
    texts: TransactionAutoHideTexts = TransactionAutoHideTexts(),
) {
    Column(modifier) {
        CellUniversalLawrenceMutableSection(
            mutableListOf<@Composable () -> Unit>().apply {
                add { AutoHideSwitchCell(transactionAutoHideEnabled, onTransactionAutoHideEnabledChange) }
                if (transactionAutoHideEnabled) {
                    add { DisplayLevelCell(displayLevel, onChangeDisplayClick) }
                    add {
                        PinCell(
                            text = if (transactionAutoHideSeparatePinExists) texts.editCode else texts.setCode,
                            onClick = onPinClick,
                        )
                    }
                    if (transactionAutoHideSeparatePinExists) {
                        add { DeletePinCell(texts.deleteCode, onDisablePinClick) }
                    }
                }
            }
        )
        InfoText(
            text = stringResource(texts.description),
            paddingBottom = 32.dp
        )
    }
}

@Composable
private fun AutoHideSwitchCell(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    SecurityCenterCell(
        start = { CellIcon(Res.drawable.ic_off_24, ComposeAppTheme.colors.iconSecondary) },
        center = {
            body_leah(
                text = stringResource(Res.string.Appearance_TransactionAutoHide),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        end = { HsSwitch(checked = enabled, onCheckedChange = onEnabledChange) }
    )
}

@Composable
private fun DisplayLevelCell(displayLevel: TransactionDisplayLevel, onClick: () -> Unit) {
    SecurityCenterCell(
        start = { CellIcon(Res.drawable.icon_paper_contract_24, ComposeAppTheme.colors.iconSecondary) },
        center = {
            body_leah(
                text = stringResource(Res.string.display),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        end = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                subhead1_grey(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(displayLevel.title)
                )
                CellIcon(Res.drawable.ic_big_forward_20, ComposeAppTheme.colors.iconSecondary)
            }
        },
        onClick = onClick
    )
}

@Composable
private fun PinCell(text: StringResource, onClick: () -> Unit) {
    SecurityCenterCell(
        start = { CellIcon(Res.drawable.ic_passcode, ComposeAppTheme.colors.brandDefault) },
        center = {
            body_brand(
                text = stringResource(text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick
    )
}

@Composable
private fun DeletePinCell(text: StringResource, onClick: () -> Unit) {
    SecurityCenterCell(
        start = { CellIcon(Res.drawable.ic_delete_20, ComposeAppTheme.colors.statusError) },
        center = {
            body_lucian(
                text = stringResource(text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick
    )
}

@Composable
private fun CellIcon(icon: DrawableResource, tint: Color) {
    Icon(
        painter = painterResource(icon),
        tint = tint,
        modifier = Modifier.size(24.dp),
        contentDescription = null,
    )
}

@Preview
@Composable
private fun TransactionAutoHideBlockPreview() {
    var transactionAutoHideEnabled by remember { mutableStateOf(true) }
    ComposeAppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            TransactionAutoHideBlock(
                transactionAutoHideEnabled = transactionAutoHideEnabled,
                displayLevel = TransactionDisplayLevel.LAST_2_TRANSACTIONS,
                transactionAutoHideSeparatePinExists = false,
                onTransactionAutoHideEnabledChange = {
                    transactionAutoHideEnabled = it
                },
                onPinClick = {},
                onDisablePinClick = {},
                onChangeDisplayClick = {},
            )
        }
    }
}
