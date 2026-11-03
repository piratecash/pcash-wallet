package cash.p.terminal.modules.send.offline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.ui_compose.annotatedHtmlStringResource
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.offline_transaction_amount
import cash.p.terminal.resources.offline_transaction_button_sign
import cash.p.terminal.resources.offline_transaction_fee
import cash.p.terminal.resources.offline_transaction_format
import cash.p.terminal.resources.offline_transaction_network
import cash.p.terminal.resources.offline_transaction_pcash_format
import cash.p.terminal.resources.offline_transaction_pcash_info_description
import cash.p.terminal.resources.offline_transaction_pcash_info_title
import cash.p.terminal.resources.offline_transaction_raw_format
import cash.p.terminal.resources.offline_transaction_raw_info_description
import cash.p.terminal.resources.offline_transaction_raw_info_title
import cash.p.terminal.resources.offline_transaction_recipient
import cash.p.terminal.resources.offline_transaction_sign_title
import cash.p.terminal.resources.offline_transaction_signing
import cash.p.terminal.resources.offline_transaction_what_you_sign
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun OfflineSignScreen(
    confirmationData: SendConfirmationData,
    blockchainName: String,
    coinMaxAllowedDecimals: Int,
    feeCoinMaxAllowedDecimals: Int,
    rate: CurrencyValue?,
    signState: OfflineSignState,
    callbacks: OfflineSignCallbacks,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    var selectedFormat by rememberSaveable { mutableStateOf(OfflineTransactionFormat.Pcash) }
    var infoFormat by remember { mutableStateOf<OfflineTransactionFormat?>(null) }
    val contentState = OfflineSignContentState(
        summaryState = confirmationData.toOfflineSignSummaryState(
            blockchainName = blockchainName,
            coinMaxAllowedDecimals = coinMaxAllowedDecimals,
            feeCoinMaxAllowedDecimals = feeCoinMaxAllowedDecimals,
            rate = rate,
        ),
        signState = signState,
        selectedFormat = selectedFormat,
    )

    OfflineSignEffect(
        signState = signState,
        callbacks = callbacks,
    )
    OfflineSignLayout(
        contentState = contentState,
        onFormatSelect = { selectedFormat = it },
        onInfoClick = { infoFormat = it },
        callbacks = callbacks,
        windowInsets = windowInsets,
    )
    infoFormat?.let { format ->
        val urlOpener = LocalUrlOpener.current
        val uriHandler = remember(urlOpener) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    urlOpener.open(uri)
                }
            }
        }
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            InfoBottomSheet(
                title = stringResource(format.infoTitleRes),
                text = annotatedHtmlStringResource(
                    resource = format.infoDescriptionRes,
                    linkStyle = SpanStyle(
                        color = ComposeAppTheme.colors.brandDefault,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
                onDismiss = { infoFormat = null },
            )
        }
    }
}

internal data class OfflineSignCallbacks(
    val onBackClick: () -> Unit,
    val onCancelClick: () -> Unit,
    val onSignClick: (OfflineTransactionFormat) -> Unit,
    val onSignStateConsumed: () -> Unit,
    val onSigned: (OfflineTransactionFormat) -> Unit,
)

internal data class OfflineSignContentState(
    val summaryState: OfflineSignSummaryState,
    val signState: OfflineSignState,
    val selectedFormat: OfflineTransactionFormat,
)

internal data class OfflineSignSummaryState(
    val blockchainName: String,
    val amount: String,
    val amountFiat: String,
    val recipient: String,
    val fee: String,
)

@Composable
private fun OfflineSignEffect(
    signState: OfflineSignState,
    callbacks: OfflineSignCallbacks,
) {
    val hud = rememberHudMessenger()
    var signingShown by remember { mutableStateOf(false) }
    val currentCallbacks by rememberUpdatedState(callbacks)

    LaunchedEffect(signState) {
        if (signingShown) hud.dismiss()
        signingShown = false

        when (signState) {
            OfflineSignState.Idle -> Unit

            OfflineSignState.Signing -> {
                hud.showInProgress(
                    Translator.getString(Res.string.offline_transaction_signing),
                    SnackbarDuration.INDEFINITE,
                )
                signingShown = true
            }

            is OfflineSignState.Failed -> {
                hud.showError(
                    signState.caution.description?.toString() ?: signState.caution.s.toString(),
                )
                currentCallbacks.onSignStateConsumed()
            }

            is OfflineSignState.Signed -> {
                currentCallbacks.onSigned(signState.format)
                currentCallbacks.onSignStateConsumed()
            }
        }
    }
}

@Composable
internal fun OfflineSignLayout(
    contentState: OfflineSignContentState,
    onFormatSelect: (OfflineTransactionFormat) -> Unit,
    onInfoClick: (OfflineTransactionFormat) -> Unit,
    callbacks: OfflineSignCallbacks,
    windowInsets: WindowInsets,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.offline_transaction_sign_title),
                navigationIcon = {
                    HsBackButton(onClick = callbacks.onBackClick)
                },
                menuItems = listOf(),
            )
        },
        bottomBar = {
            OfflineSignBottomActions(
                signState = contentState.signState,
                selectedFormat = contentState.selectedFormat,
                callbacks = callbacks,
                windowInsets = windowInsets,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            OfflineSignScrollableContent(
                contentState = contentState,
                onFormatSelect = onFormatSelect,
                onInfoClick = onInfoClick,
            )
        }
    }
}

@Composable
private fun OfflineSignScrollableContent(
    contentState: OfflineSignContentState,
    onFormatSelect: (OfflineTransactionFormat) -> Unit,
    onInfoClick: (OfflineTransactionFormat) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        VSpacer(8.dp)
        OfflineSummarySection(
            summaryState = contentState.summaryState,
        )
        VSpacer(24.dp)
        OfflineFormatSection(
            selectedFormat = contentState.selectedFormat,
            onFormatSelect = onFormatSelect,
            onInfoClick = onInfoClick,
        )
    }
}

@Composable
private fun OfflineSummarySection(
    summaryState: OfflineSignSummaryState,
) {
    SectionCaption(Res.string.offline_transaction_what_you_sign)
    SectionUniversalLawrence {
        OfflineSummaryCell(
            title = stringResource(Res.string.offline_transaction_network),
            value = summaryState.blockchainName,
            borderTop = false,
        )
        OfflineSummaryCell(
            title = stringResource(Res.string.offline_transaction_amount),
            value = summaryState.amount,
            secondaryValue = summaryState.amountFiat,
        )
        OfflineSummaryCell(
            title = stringResource(Res.string.offline_transaction_recipient),
            value = summaryState.recipient,
            valueMaxLines = 2,
        )
        OfflineSummaryCell(
            title = stringResource(Res.string.offline_transaction_fee),
            value = summaryState.fee,
        )
    }
}

@Composable
private fun OfflineFormatSection(
    selectedFormat: OfflineTransactionFormat,
    onFormatSelect: (OfflineTransactionFormat) -> Unit,
    onInfoClick: (OfflineTransactionFormat) -> Unit,
) {
    SectionCaption(Res.string.offline_transaction_format)
    CellUniversalLawrenceSection(
        OfflineTransactionFormat.entries.map { format ->
            {
                OfflineFormatCell(
                    format = format,
                    selected = selectedFormat == format,
                    onClick = { onFormatSelect(format) },
                    onInfoClick = { onInfoClick(format) },
                )
            }
        }
    )
}

@Composable
private fun OfflineSignBottomActions(
    signState: OfflineSignState,
    selectedFormat: OfflineTransactionFormat,
    callbacks: OfflineSignCallbacks,
    windowInsets: WindowInsets,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            title = if (signState is OfflineSignState.Signing) {
                stringResource(Res.string.offline_transaction_signing)
            } else {
                stringResource(Res.string.offline_transaction_button_sign)
            },
            enabled = signState !is OfflineSignState.Signing,
            onClick = { callbacks.onSignClick(selectedFormat) },
        )
        ButtonPrimaryDefault(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.Button_Cancel),
            enabled = signState !is OfflineSignState.Signing,
            onClick = callbacks.onCancelClick,
        )
    }
}

@Composable
private fun OfflineFormatCell(
    format: OfflineTransactionFormat,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        HsCheckbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        subhead2_leah(
            modifier = Modifier
                .padding(start = 16.dp),
            text = stringResource(format.titleRes),
        )
        HsIconButton(
            onClick = onInfoClick,
            minWidth = 36.dp
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(Res.drawable.ic_info_20),
                contentDescription = stringResource(Res.string.Info_Title),
                tint = ComposeAppTheme.colors.iconSecondary,
            )
        }
    }
}

private val OfflineTransactionFormat.titleRes: StringResource
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> Res.string.offline_transaction_pcash_format
        OfflineTransactionFormat.Raw -> Res.string.offline_transaction_raw_format
    }

private val OfflineTransactionFormat.infoTitleRes: StringResource
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> Res.string.offline_transaction_pcash_info_title
        OfflineTransactionFormat.Raw -> Res.string.offline_transaction_raw_info_title
    }

private val OfflineTransactionFormat.infoDescriptionRes: StringResource
    get() = when (this) {
        OfflineTransactionFormat.Pcash -> Res.string.offline_transaction_pcash_info_description
        OfflineTransactionFormat.Raw -> Res.string.offline_transaction_raw_info_description
    }

private fun SendConfirmationData.toOfflineSignSummaryState(
    blockchainName: String,
    coinMaxAllowedDecimals: Int,
    feeCoinMaxAllowedDecimals: Int,
    rate: CurrencyValue?,
): OfflineSignSummaryState =
    OfflineSignSummaryState(
        blockchainName = blockchainName,
        amount = getKoinInstance<IAppNumberFormatter>().formatCoinFull(
            amount,
            coin.code,
            coinMaxAllowedDecimals,
        ),
        amountFiat = rate?.copy(value = amount.times(rate.value))?.getFormattedFull().orEmpty(),
        recipient = address.hex,
        fee = fee?.let {
            getKoinInstance<IAppNumberFormatter>().formatCoinFull(
                it,
                feeCoin.code,
                feeCoinMaxAllowedDecimals,
            )
        } ?: "---",
    )
