package cash.p.terminal.modules.signmessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Balance_Address
import cash.p.terminal.resources.Balance_Receive_ChangeAddresses
import cash.p.terminal.resources.Balance_Receive_ReceiveAddresses
import cash.p.terminal.resources.Button_Copy
import cash.p.terminal.resources.Button_Done
import cash.p.terminal.resources.Button_Share
import cash.p.terminal.resources.Button_Sign
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_copy_24px
import cash.p.terminal.resources.ic_share_24px
import cash.p.terminal.resources.sign_message_current_receive_address
import cash.p.terminal.resources.sign_message_details
import cash.p.terminal.resources.sign_message_details_format
import cash.p.terminal.resources.sign_message_format
import cash.p.terminal.resources.sign_message_format_bip322
import cash.p.terminal.resources.sign_message_format_bip322_description
import cash.p.terminal.resources.sign_message_format_info
import cash.p.terminal.resources.sign_message_format_standard
import cash.p.terminal.resources.sign_message_format_standard_description
import cash.p.terminal.resources.sign_message_hardware_taproot
import cash.p.terminal.resources.sign_message_hint
import cash.p.terminal.resources.sign_message_intro
import cash.p.terminal.resources.sign_message_message
import cash.p.terminal.resources.sign_message_result_intro
import cash.p.terminal.resources.sign_message_select_address_hint
import cash.p.terminal.resources.sign_message_signature
import cash.p.terminal.resources.sign_message_taproot_only
import cash.p.terminal.resources.sign_message_title
import cash.p.terminal.resources.sign_message_used
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui.compose.components.FormsInputMultiline
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryCircle
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.Tabs
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.UsedAddress
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SignMessageScreen(
    uiState: SignMessageUiState,
    navigation: HSNavigation,
    onAddressClick: () -> Unit,
    onMessageChange: (String) -> Unit,
    onFormatSelect: (MessageSignatureFormat) -> Unit,
    onSignClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var showFormatInfo by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.sign_message_title),
                navigationIcon = { HsBackButton(onClick = onBackClick) },
            )
        },
        bottomBar = {
            ButtonsGroupWithShade(modifier = Modifier.navigationBarsPadding()) {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = stringResource(Res.string.Button_Sign),
                    enabled = uiState.signEnabled,
                    loadingIndicator = uiState.signing,
                    onClick = onSignClick,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            InfoText(text = stringResource(Res.string.sign_message_intro))
            VSpacer(12.dp)

            HeaderText(text = stringResource(Res.string.Balance_Address))
            CellUniversalLawrenceSection {
                AddressCell(address = uiState.address, onClick = onAddressClick)
            }
            VSpacer(24.dp)

            HeaderText(text = stringResource(Res.string.sign_message_message))
            FormsInputMultiline(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = uiState.message,
                hint = stringResource(Res.string.sign_message_hint),
                qrScannerNavigation = navigation,
                onValueChange = onMessageChange,
            )
            VSpacer(24.dp)

            HeaderText(text = stringResource(Res.string.sign_message_format), onInfoClick = { showFormatInfo = true })
            FormatSection(uiState, onFormatSelect)
            VSpacer(32.dp)
        }
    }
    if (showFormatInfo) {
        InfoBottomSheet(
            title = stringResource(Res.string.sign_message_format),
            text = stringResource(Res.string.sign_message_format_info),
            onDismiss = { showFormatInfo = false },
        )
    }
}

@Composable
private fun FormatSection(uiState: SignMessageUiState, onFormatSelect: (MessageSignatureFormat) -> Unit) {
    if (uiState.formats.isEmpty()) {
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.sign_message_hardware_taproot),
        )
        return
    }
    CellUniversalLawrenceSection(uiState.formats) { format ->
        FormatCell(
            format = format,
            selected = format == uiState.selectedFormat,
            onClick = { onFormatSelect(format) },
        )
    }
    if (uiState.formats == listOf(MessageSignatureFormat.Bip322)) {
        InfoText(text = stringResource(Res.string.sign_message_taproot_only))
    }
}

@Composable
private fun AddressCell(address: String, onClick: () -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        subhead1_leah(
            modifier = Modifier.weight(1f),
            text = address.shorten(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HSpacer(8.dp)
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
        )
    }
}

@Composable
private fun FormatCell(
    format: MessageSignatureFormat,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = format.title())
            subhead2_grey(text = format.description())
        }
        CheckmarkSlot(selected)
    }
}

@Composable
private fun MessageSignatureFormat.title(): String = stringResource(
    when (this) {
        MessageSignatureFormat.Standard -> Res.string.sign_message_format_standard
        MessageSignatureFormat.Bip322 -> Res.string.sign_message_format_bip322
    }
)

@Composable
private fun MessageSignatureFormat.description(): String = stringResource(
    when (this) {
        MessageSignatureFormat.Standard -> Res.string.sign_message_format_standard_description
        MessageSignatureFormat.Bip322 -> Res.string.sign_message_format_bip322_description
    }
)

@Composable
private fun CheckmarkSlot(selected: Boolean) {
    if (selected) {
        Icon(
            painter = painterResource(Res.drawable.ic_checkmark_20),
            tint = ComposeAppTheme.colors.brandDefault,
            contentDescription = null,
        )
    } else {
        HSpacer(20.dp)
    }
}

private enum class AddressTab { Receive, Change }

@Composable
fun SignAddressSelectScreen(
    input: SignAddressSelectInput,
    onSelect: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    var tab by remember { mutableStateOf(AddressTab.Receive) }
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Balance_Address),
                navigationIcon = { HsBackButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            InfoText(text = stringResource(Res.string.sign_message_select_address_hint))
            VSpacer(12.dp)
            Tabs(
                listOf(
                    TabItem(
                        stringResource(Res.string.Balance_Receive_ReceiveAddresses),
                        tab == AddressTab.Receive,
                        AddressTab.Receive,
                    ),
                    TabItem(
                        stringResource(Res.string.Balance_Receive_ChangeAddresses),
                        tab == AddressTab.Change,
                        AddressTab.Change,
                    ),
                ),
                onClick = { tab = it },
            )
            VSpacer(12.dp)
            if (tab == AddressTab.Receive) {
                HeaderText(text = stringResource(Res.string.sign_message_current_receive_address))
                CellUniversalLawrenceSection {
                    AddressSelectCell(null, input.currentAddress, input.currentAddress == input.selectedAddress) {
                        onSelect(input.currentAddress)
                    }
                }
                VSpacer(24.dp)
                HeaderText(text = stringResource(Res.string.sign_message_used))
            }
            val items = if (tab == AddressTab.Receive) input.receiveAddresses else input.changeAddresses
            CellUniversalLawrenceSection(items) { item ->
                AddressSelectCell(item.index + 1, item.address, item.address == input.selectedAddress) {
                    onSelect(item.address)
                }
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun AddressSelectCell(
    index: Int?,
    address: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        index?.let {
            subhead2_grey(text = it.toString())
            HSpacer(16.dp)
        }
        subhead2_leah(modifier = Modifier.weight(1f), text = address)
        HSpacer(16.dp)
        CheckmarkSlot(selected)
    }
}

@Composable
fun SignatureResultScreen(
    signedMessage: SignedMessage,
    shareAvailable: Boolean,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    onDoneClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.sign_message_signature),
                navigationIcon = { HsBackButton(onClick = onBackClick) },
            )
        },
        bottomBar = {
            ButtonsGroupWithShade(modifier = Modifier.navigationBarsPadding()) {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = stringResource(Res.string.Button_Done),
                    onClick = onDoneClick,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            InfoText(text = stringResource(Res.string.sign_message_result_intro))
            VSpacer(12.dp)
            CellUniversalLawrenceSection {
                subhead2_leah(
                    modifier = Modifier.padding(16.dp),
                    text = signedMessage.signature,
                )
            }
            VSpacer(24.dp)
            ResultActions(shareAvailable, onCopyClick, onShareClick)
            VSpacer(24.dp)
            HeaderText(text = stringResource(Res.string.sign_message_details))
            CellUniversalLawrenceSection(
                listOf(
                    stringResource(Res.string.Balance_Address) to signedMessage.address.shorten(),
                    stringResource(Res.string.sign_message_details_format) to signedMessage.format.title(),
                    stringResource(Res.string.sign_message_message) to signedMessage.message,
                )
            ) { (title, value) ->
                TitleAndValueCell(title = title, value = value)
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun ResultActions(shareAvailable: Boolean, onCopyClick: () -> Unit, onShareClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ResultActionButton(Res.drawable.ic_copy_24px, stringResource(Res.string.Button_Copy), onCopyClick)
        if (shareAvailable) {
            ResultActionButton(Res.drawable.ic_share_24px, stringResource(Res.string.Button_Share), onShareClick)
        }
    }
}

@Composable
private fun ResultActionButton(
    icon: DrawableResource,
    text: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ButtonPrimaryCircle(icon = icon, onClick = onClick)
        caption_grey(
            modifier = Modifier.padding(top = 8.dp),
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

private const val PreviewAddress = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
private const val PreviewTaprootAddress = "bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297"
private const val PreviewSignature =
    "H9L5yLFjti0QTHhPyFrZCT1V/MMnBtXKmoiKDZ78NDBjERki6ZTQZdSMCtkgoNmp17By9ItJr8o7ChX0XxY91nk="

@Composable
private fun SignMessageFormPreview(state: SignMessageUiState, darkTheme: Boolean = true) {
    ComposeAppTheme(darkTheme = darkTheme) {
        SignMessageScreen(
            uiState = state,
            navigation = HSNavigation(NavBackStack()),
            onAddressClick = {},
            onMessageChange = {},
            onFormatSelect = {},
            onSignClick = {},
            onBackClick = {},
        )
    }
}

private val previewSegwitState = SignMessageUiState(
    address = PreviewAddress,
    formats = listOf(MessageSignatureFormat.Standard, MessageSignatureFormat.Bip322),
    selectedFormat = MessageSignatureFormat.Standard,
)

@Preview
@Composable
private fun SignMessageScreenEmptyPreview() {
    SignMessageFormPreview(previewSegwitState)
}

@Preview
@Composable
private fun SignMessageScreenFilledLightPreview() {
    SignMessageFormPreview(
        previewSegwitState.copy(message = PreviewAddress, selectedFormat = MessageSignatureFormat.Bip322),
        darkTheme = false,
    )
}

@Preview
@Composable
private fun SignMessageScreenTaprootPreview() {
    SignMessageFormPreview(
        SignMessageUiState(
            address = PreviewTaprootAddress,
            formats = listOf(MessageSignatureFormat.Bip322),
            selectedFormat = MessageSignatureFormat.Bip322,
            message = "Ich bestätige, dass diese Adresse mir gehört.",
        )
    )
}

@Preview
@Composable
private fun SignMessageScreenHardwareTaprootPreview() {
    SignMessageFormPreview(
        SignMessageUiState(address = PreviewTaprootAddress, formats = emptyList(), selectedFormat = null)
    )
}

@Preview
@Composable
private fun SignAddressSelectScreenPreview() {
    ComposeAppTheme(darkTheme = true) {
        SignAddressSelectScreen(
            input = SignAddressSelectInput(
                currentAddress = "bc1q9h7garjnc5w2ue9p3wlkq4dz8hdlhpc5a0rmm6",
                receiveAddresses = listOf(
                    UsedAddress(0, PreviewAddress, ""),
                    UsedAddress(1, "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", ""),
                ),
                changeAddresses = emptyList(),
                selectedAddress = PreviewAddress,
            ),
            onSelect = {},
            onBackClick = {},
        )
    }
}

@Preview
@Composable
private fun SignatureResultScreenPreview() {
    ComposeAppTheme(darkTheme = true) {
        SignatureResultScreen(
            signedMessage = SignedMessage(
                PreviewSignature,
                PreviewAddress,
                MessageSignatureFormat.Standard,
                PreviewAddress,
            ),
            shareAvailable = true,
            onCopyClick = {},
            onShareClick = {},
            onDoneClick = {},
            onBackClick = {},
        )
    }
}
