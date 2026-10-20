package cash.p.terminal.modules.btcblockchainsettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusButton
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsModule.BlockchainSettingsIcon
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BtcBlockchainSettings_RestoreSourceSettingsDescription
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Button_Save
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.btc_blockchain_settings_restore_source_change_warning
import cash.p.terminal.resources.custom_peers
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_platform_placeholder_32
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BtcBlockchainSettingsScreen(
    page: HSPage,
    uiState: BtcBlockchainSettingsUIState,
    onSaveClick: () -> Unit,
    onSelectRestoreMode: (BtcBlockchainSettingsModule.ViewItem) -> Unit,
    onCustomPeersChange: (String) -> Unit,
    navigation: HSNavigation,
    onBlockchainStatusClick: (() -> Unit)?,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {

    if (uiState.closeScreen) {
        navigation.navigateUpFrom(page)
    }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column(modifier = Modifier.windowInsetsPadding(windowInsets)) {
            BlockchainSettingsAppBar(uiState.title, uiState.blockchainIconUrl, navigation::navigateUpSafely)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(12.dp))
                RestoreSourceSettings(uiState.restoreSources, onSelectRestoreMode)
                if (uiState.customPeers != null) {
                    Spacer(Modifier.height(16.dp))
                    CustomPeersSettings(
                        customPeers = uiState.customPeers,
                        onCustomPeersChange = onCustomPeersChange
                    )
                }
                VSpacer(16.dp)
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(Res.string.btc_blockchain_settings_restore_source_change_warning)
                )
                Spacer(Modifier.height(32.dp))
                if (onBlockchainStatusClick != null) {
                    BlockchainStatusButton(onClick = onBlockchainStatusClick)
                    Spacer(Modifier.height(32.dp))
                }
            }

            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    title = stringResource(Res.string.Button_Save),
                    enabled = uiState.saveButtonEnabled,
                    onClick = onSaveClick
                )
            }
        }

    }
}

@Composable
internal fun BlockchainSettingsAppBar(title: String, iconUrl: String, onClose: () -> Unit) {
    AppBar(
        title = title,
        navigationIcon = {
            Image(
                painter = rememberAsyncImagePainterWithFallback(
                    model = iconUrl,
                    error = painterResource(Res.drawable.ic_platform_placeholder_32)
                ),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .size(24.dp)
            )
        },
        menuItems = listOf(
            MenuItem(
                title = TranslatableString.ResString(Res.string.Button_Close),
                icon = Res.drawable.ic_close_24,
                onClick = onClose
            )
        )
    )
}

@Composable
private fun CustomPeersSettings(
    customPeers: String,
    onCustomPeersChange: (String) -> Unit
) {
    FormsInput(
        modifier = Modifier
            .padding(horizontal = 16.dp),
        initial = customPeers,
        pasteEnabled = false,
        hint = stringResource(Res.string.custom_peers),
        onValueChange = onCustomPeersChange
    )
}


@Composable
private fun RestoreSourceSettings(
    restoreSources: List<BtcBlockchainSettingsModule.ViewItem>,
    onSelectRestoreMode: (BtcBlockchainSettingsModule.ViewItem) -> Unit
) {
    BlockchainSettingSection(restoreSources, onSelectRestoreMode)
}

@Composable
private fun BlockchainSettingSection(
    restoreSources: List<BtcBlockchainSettingsModule.ViewItem>,
    onItemClick: (BtcBlockchainSettingsModule.ViewItem) -> Unit
) {
    subhead2_grey(
        modifier = Modifier.padding(horizontal = 32.dp),
        text = stringResource(Res.string.BtcBlockchainSettings_RestoreSourceSettingsDescription)
    )
    VSpacer(32.dp)
    CellUniversalLawrenceSection(restoreSources) { item ->
        BlockchainSettingCell(item.title, item.subtitle, item.selected, item.icon) {
            onItemClick(item)
        }
    }

}

@Composable
internal fun BlockchainSettingCell(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: BlockchainSettingsIcon?,
    onClick: () -> Unit
) {
    RowUniversal(
        onClick = onClick
    ) {
        icon?.let {
            HSpacer(width = 16.dp)
            Image(
                modifier = Modifier
                    .size(32.dp),
                painter = when (icon) {
                    is BlockchainSettingsIcon.ApiIcon -> painterResource(icon.resId)
                    is BlockchainSettingsIcon.BlockchainIcon -> rememberAsyncImagePainterWithFallback(
                        model = icon.url,
                        error = painterResource(Res.drawable.ic_platform_placeholder_32)
                    )
                },
                contentDescription = null,
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            body_leah(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            subhead2_grey(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null,
                )
            }
        }
    }
}
