package cash.p.terminal.modules.send.offline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.rememberAsyncImagePainter
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import org.koin.compose.koinInject
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.getShape
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.offline_broadcast_details
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.ic_arrow_big_down_20
import cash.p.terminal.resources.ic_big_forward_20
import cash.p.terminal.resources.ic_blocks_20
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.ic_globe_20
import cash.p.terminal.resources.ic_platform_placeholder_32
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_Done
import cash.p.terminal.resources.Button_Reject
import cash.p.terminal.resources.offline_broadcast_blockchain
import cash.p.terminal.resources.offline_broadcast_check_title
import cash.p.terminal.resources.offline_broadcast_disclaimer
import cash.p.terminal.resources.offline_broadcast_enable_network_button
import cash.p.terminal.resources.offline_broadcast_enable_network_warning
import cash.p.terminal.resources.offline_broadcast_error_title
import cash.p.terminal.resources.offline_broadcast_preparing_network_button
import cash.p.terminal.resources.offline_broadcast_select_network
import cash.p.terminal.resources.offline_broadcast_send_button
import cash.p.terminal.resources.offline_broadcast_sending
import cash.p.terminal.resources.offline_broadcast_success_description
import cash.p.terminal.resources.offline_broadcast_success_title
import cash.p.terminal.resources.offline_broadcast_tx_id
import cash.p.terminal.resources.offline_broadcast_view_in_explorer
import cash.p.terminal.resources.offline_signed_status_pending
import cash.p.terminal.resources.offline_signed_status_unknown
import cash.p.terminal.resources.offline_transaction_network
import cash.p.terminal.resources.send_success_queued
import cash.p.terminal.resources.try_again

@Composable
internal fun OfflineBroadcastScreen(
    uiState: OfflineBroadcastUiState,
    onPickNetwork: () -> Unit,
    onSelectBlockchain: (Blockchain) -> Unit,
    onPrimaryAction: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = onBack,
    )

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(titleResId(uiState.step)),
                navigationIcon = { HsBackButton(onClick = onBack) },
                menuItems = emptyList(),
            )
        },
        bottomBar = {
            OfflineBroadcastBottomBar(
                uiState = uiState,
                onPrimaryAction = onPrimaryAction,
                onRetry = onRetry,
                onClose = onClose,
                windowInsets = windowInsets,
            )
        },
    ) { paddingValues ->
        when (uiState.step) {
            // Transient: prefillAndAdvance moves off this step on first composition.
            OfflineBroadcastStep.Loading -> Unit

            OfflineBroadcastStep.SelectBlockchain -> BlockchainPicker(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                blockchains = uiState.selectableBlockchains,
                selected = uiState.selectedBlockchain,
                onSelectBlockchain = onSelectBlockchain,
            )

            OfflineBroadcastStep.Confirm,
            OfflineBroadcastStep.Result -> Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (uiState.step) {
                    OfflineBroadcastStep.Confirm -> uiState.confirm?.let {
                        ConfirmContent(confirm = it, onPickNetwork = onPickNetwork)
                    }

                    OfflineBroadcastStep.Result -> uiState.result?.let { ResultContent(result = it) }
                    OfflineBroadcastStep.Loading, OfflineBroadcastStep.SelectBlockchain -> Unit
                }
            }
        }
    }
}

private fun titleResId(step: OfflineBroadcastStep): StringResource = when (step) {
    OfflineBroadcastStep.SelectBlockchain -> Res.string.offline_broadcast_blockchain
    OfflineBroadcastStep.Result -> Res.string.Button_Done
    else -> Res.string.offline_broadcast_check_title
}

@Composable
private fun ConfirmContent(confirm: OfflineBroadcastConfirm, onPickNetwork: () -> Unit) {
    VSpacer(12.dp)
    SectionCaption(Res.string.offline_broadcast_details)
    NetworkRow(confirm = confirm, onPickNetwork = onPickNetwork)
    VSpacer(12.dp)
    RawTransactionSection(rawHex = confirm.rawHex)
    InfoText(text = stringResource(Res.string.offline_broadcast_disclaimer))
    confirm.enableNetworkName?.let { networkName ->
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.offline_broadcast_enable_network_warning, networkName),
        )
        VSpacer(12.dp)
    }
}

// Fixed network (pcash payload) renders as a plain label/value cell; a plain RAW HEX lets the user
// pick the broadcasting network, so it becomes a tappable row with a chevron.
@Composable
private fun NetworkRow(confirm: OfflineBroadcastConfirm, onPickNetwork: () -> Unit) {
    SectionUniversalLawrence {
        if (confirm.selectable) {
            CellUniversal(borderTop = false, onClick = onPickNetwork) {
                Icon(
                    painter = painterResource(Res.drawable.ic_blocks_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary,
                )
                HSpacer(16.dp)
                body_leah(text = stringResource(Res.string.offline_broadcast_blockchain))
                Spacer(modifier = Modifier.weight(1f))
                confirm.blockchainName?.let { subhead2_leah(text = it) }
                    ?: subhead2_grey(text = stringResource(Res.string.offline_broadcast_select_network))
                HSpacer(8.dp)
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_big_down_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary,
                )
            }
        } else {
            OfflineSummaryCell(
                title = stringResource(Res.string.offline_transaction_network),
                value = confirm.blockchainName.orEmpty(),
                borderTop = false,
            )
        }
    }
}

@Composable
private fun BlockchainPicker(
    modifier: Modifier = Modifier,
    blockchains: List<Blockchain>,
    selected: Blockchain?,
    onSelectBlockchain: (Blockchain) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
    ) {
        itemsIndexed(
            items = blockchains,
            key = { _, blockchain -> blockchain.uid },
        ) { index, blockchain ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(getShape(blockchains.size, index))
                    .background(ComposeAppTheme.colors.surfacePrimary),
            ) {
                SectionUniversalItem(borderTop = index != 0) {
                    BlockchainCell(
                        blockchain = blockchain,
                        selected = blockchain.type == selected?.type,
                        onClick = { onSelectBlockchain(blockchain) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockchainCell(
    blockchain: Blockchain,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics { this.selected = selected },
        onClick = onClick,
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = blockchain.type.imageUrl,
                error = painterResource(Res.drawable.ic_platform_placeholder_32),
            ),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        body_leah(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            text = blockchain.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                painter = painterResource(Res.drawable.ic_checkmark_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.brandDefault,
            )
        }
    }
}

@Composable
private fun ResultContent(result: OfflineBroadcastResult) {
    VSpacer(12.dp)
    when (result) {
        is OfflineBroadcastResult.Success -> SuccessResult(result)
        is OfflineBroadcastResult.Error -> ErrorResult(result)
    }
}

@Composable
private fun SuccessResult(result: OfflineBroadcastResult.Success) {
    val titleRes = if (result.queued) {
        Res.string.offline_signed_status_pending
    } else {
        Res.string.offline_broadcast_success_title
    }
    val descriptionRes = if (result.queued) {
        Res.string.send_success_queued
    } else {
        Res.string.offline_broadcast_success_description
    }
    VSpacer(44.dp)
    OfflineStatusBlock(
        icon = if (result.queued) Res.drawable.ic_info_20 else Res.drawable.ic_checkmark_20,
        iconTint = if (result.queued) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.statusSuccess,
        style = if (result.queued) OfflineStatusBlockStyle.Neutral else OfflineStatusBlockStyle.Success,
        title = stringResource(titleRes),
    ) {
        VSpacer(8.dp)
        ResultDescription(stringResource(descriptionRes))
    }
    VSpacer(24.dp)
    SectionCaption(Res.string.offline_broadcast_details)
    SectionUniversalLawrence {
        OfflineSummaryCell(
            title = stringResource(Res.string.offline_transaction_network),
            value = result.networkName,
            borderTop = false,
        )
        TransactionIdCell(txHash = result.txHash)
    }
    result.explorerUrl?.takeIf { !result.queued }?.let {
        VSpacer(12.dp)
        SectionUniversalLawrence {
            ExplorerCell(url = it)
        }
    }
    VSpacer(24.dp)
}

@Composable
private fun ErrorResult(result: OfflineBroadcastResult.Error) {
    VSpacer(44.dp)
    OfflineStatusBlock(
        icon = Res.drawable.ic_close_24,
        iconTint = ComposeAppTheme.colors.statusError,
        style = OfflineStatusBlockStyle.Error,
        title = stringResource(
            if (result.acceptanceUnknown) Res.string.offline_signed_status_unknown
            else Res.string.offline_broadcast_error_title
        ),
    ) {
        VSpacer(8.dp)
        ResultDescription(result.message)
    }
    VSpacer(24.dp)
    SectionCaption(Res.string.offline_broadcast_details)
    SectionUniversalLawrence {
        OfflineSummaryCell(
            title = stringResource(Res.string.offline_transaction_network),
            value = result.networkName,
            borderTop = false,
        )
    }
    VSpacer(12.dp)
    RawTransactionSection(rawHex = result.rawHex)
    VSpacer(24.dp)
}

@Composable
private fun ResultDescription(text: String) {
    subhead2_grey(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        text = text,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TransactionIdCell(txHash: String) {
    val hud = rememberHudMessenger()
    val clipboardManager: IClipboardManager = koinInject()
    val copy: () -> Unit = {
        clipboardManager.copyText(txHash)
        hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
    }
    CellUniversal(borderTop = true) {
        subhead2_grey(
            text = stringResource(Res.string.offline_broadcast_tx_id),
            modifier = Modifier.padding(end = 16.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonSecondaryDefault(
                title = txHash.shorten(),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(28.dp),
                overflow = TextOverflow.MiddleEllipsis,
                onClick = copy,
            )
            HSpacer(8.dp)
            ButtonSecondaryCircle(
                icon = Res.drawable.ic_copy_20,
                onClick = copy,
            )
        }
    }
}

@Composable
private fun ExplorerCell(url: String) {
    val urlOpener = LocalUrlOpener.current
    CellUniversal(
        borderTop = false,
        onClick = { urlOpener.open(url) },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_globe_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
        )
        HSpacer(16.dp)
        body_leah(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.offline_broadcast_view_in_explorer),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(Res.drawable.ic_big_forward_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
        )
    }
}

@Composable
private fun OfflineBroadcastBottomBar(
    uiState: OfflineBroadcastUiState,
    onPrimaryAction: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    windowInsets: WindowInsets,
) {
    val hasActions = uiState.step == OfflineBroadcastStep.Confirm ||
        uiState.step == OfflineBroadcastStep.Result
    if (!hasActions) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        when (uiState.step) {
            OfflineBroadcastStep.Confirm -> ConfirmActions(uiState, onPrimaryAction, onClose)
            OfflineBroadcastStep.Result -> ResultActions(uiState.result, onRetry, onClose)
            OfflineBroadcastStep.Loading, OfflineBroadcastStep.SelectBlockchain -> Unit
        }
    }
}

@Composable
private fun ColumnScope.ConfirmActions(
    uiState: OfflineBroadcastUiState,
    onPrimaryAction: () -> Unit,
    onClose: () -> Unit,
) {
    val confirm = uiState.confirm
    val broadcasting = uiState.broadcasting
    val preparing = confirm?.action is OfflineBroadcastConfirmAction.PreparingNetwork
    PrimaryConfirmButton(confirm, broadcasting, onPrimaryAction)
    VSpacer(12.dp)
    ButtonPrimaryTransparent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(Res.string.Button_Reject),
        enabled = !broadcasting && !preparing,
        onClick = onClose,
    )
}

// The primary button mirrors the confirm action: enable a missing network, show preparing while the
// wallet/adapter start up, or send once everything is ready. The ViewModel decides what the single
// action does, so the UI forwards every tap to onPrimaryAction.
@Composable
private fun PrimaryConfirmButton(
    confirm: OfflineBroadcastConfirm?,
    broadcasting: Boolean,
    onPrimaryAction: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    when (val action = confirm?.action) {
        is OfflineBroadcastConfirmAction.EnableNetwork -> ButtonPrimaryYellow(
            modifier = modifier,
            title = stringResource(Res.string.offline_broadcast_enable_network_button, action.blockchainName),
            onClick = onPrimaryAction,
        )

        is OfflineBroadcastConfirmAction.PreparingNetwork -> ButtonPrimaryYellow(
            modifier = modifier,
            title = stringResource(Res.string.offline_broadcast_preparing_network_button, action.blockchainName),
            enabled = false,
            loadingIndicator = true,
            onClick = {},
        )

        else -> ButtonPrimaryYellow(
            modifier = modifier,
            title = stringResource(
                if (broadcasting) Res.string.offline_broadcast_sending
                else Res.string.offline_broadcast_send_button
            ),
            enabled = confirm?.canBroadcast == true && !broadcasting,
            loadingIndicator = broadcasting,
            onClick = onPrimaryAction,
        )
    }
}

@Composable
private fun ColumnScope.ResultActions(
    result: OfflineBroadcastResult?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    when (result) {
        is OfflineBroadcastResult.Success -> ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Button_Done),
            onClick = onClose,
        )

        is OfflineBroadcastResult.Error -> {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.try_again),
                onClick = onRetry,
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Cancel),
                onClick = onClose,
            )
        }

        null -> Unit
    }
}
