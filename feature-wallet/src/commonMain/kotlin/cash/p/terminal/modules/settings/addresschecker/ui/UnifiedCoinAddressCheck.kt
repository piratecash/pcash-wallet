package cash.p.terminal.modules.settings.addresschecker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.modules.send.address.ui.CheckAddressInput
import cash.p.terminal.modules.settings.addresschecker.CheckState
import cash.p.terminal.modules.settings.addresschecker.IssueType
import cash.p.terminal.modules.settings.addresschecker.UnifiedAddressCheckerViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Error
import cash.p.terminal.resources.Error_AssetNotEnabled
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_EnterAddress
import cash.p.terminal.resources.Send_Hint_Address
import cash.p.terminal.resources.SettingsAddressChecker_ChainalysisCheck
import cash.p.terminal.resources.SettingsAddressChecker_ChainalysisCheckDescription
import cash.p.terminal.resources.SettingsAddressChecker_HashditCheck
import cash.p.terminal.resources.SettingsAddressChecker_HashditCheckDescription
import cash.p.terminal.resources.SettingsAddressChecker_InvalidAddress
import cash.p.terminal.resources.SettingsAddressChecker_OnSubject
import cash.p.terminal.resources.SettingsAddressChecker_PyusdBlacklistDescription
import cash.p.terminal.resources.SettingsAddressChecker_SanctionsCheck
import cash.p.terminal.resources.SettingsAddressChecker_TetherBlacklistDescription
import cash.p.terminal.resources.SettingsAddressChecker_TokenBlacklistCheck
import cash.p.terminal.resources.SettingsAddressChecker_UsdcBlacklistDescription
import cash.p.terminal.resources.SwapSettings_Error_InvalidAddress
import cash.p.terminal.resources.alpha_aml_check_title_description
import cash.p.terminal.resources.alpha_aml_description
import cash.p.terminal.resources.alpha_aml_title
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.resources.ic_alpha_aml
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.resources.ic_chainalysis
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_hashdit
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.resources.ic_lock_20
import cash.p.terminal.resources.ic_star_filled_20
import cash.p.terminal.resources.smart_contract_check
import cash.p.terminal.resources.smart_contract_check_desription
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.placeholderPainter
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.imageUrl
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedAddressCheckScreen(
    navigation: HSNavigation,
    onClose: () -> Unit,
    onPremiumClick: () -> Unit,
    initialAddress: String? = null,
) {
    val viewModel = koinViewModel<UnifiedAddressCheckerViewModel> { parametersOf(initialAddress) }

    val coroutineScope = rememberCoroutineScope()
    val infoModalBottomSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var bottomCheckInfo by remember { mutableStateOf<BottomCheckInfo?>(null) }

    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Send_EnterAddress),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onClose
                    )
                )
            )
        },
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .padding(innerPaddings)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                CheckAddressInput(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    value = uiState.value,
                    hint = stringResource(Res.string.Send_Hint_Address),
                    navigation = navigation,
                    state = uiState.inputState,
                ) {
                    viewModel.onEnterAddress(it)
                }

                if (uiState.inputState is DataState.Error) {
                    ValidationError(uiState.inputState.error)
                } else {
                    SecurityCheckCard(
                        title = stringResource(Res.string.smart_contract_check),
                        icon = painterResource(Res.drawable.ic_star_filled_20),
                        description = stringResource(Res.string.smart_contract_check_desription),
                        onInfoClick = { info ->
                            bottomCheckInfo = info
                            coroutineScope.launch {
                                infoModalBottomSheetState.show()
                            }
                        }
                    ) {
                        NetworkItem(
                            title = stringResource(Res.string.smart_contract_check),
                            status = uiState.checkResults[IssueType.SmartContract],
                            onClick = if (uiState.checkResults[IssueType.SmartContract] == CheckState.Locked) {
                                { onPremiumClick() }
                            } else {
                                null
                            }
                        )
                    }
                    VSpacer(16.dp)

                    SecurityCheckCard(
                        title = stringResource(Res.string.alpha_aml_title),
                        icon = painterResource(Res.drawable.ic_alpha_aml),
                        infoTitle = stringResource(Res.string.alpha_aml_check_title_description),
                        description = stringResource(Res.string.alpha_aml_description),
                        onInfoClick = { info ->
                            bottomCheckInfo = info
                            coroutineScope.launch {
                                infoModalBottomSheetState.show()
                            }
                        }
                    ) {
                        NetworkItem(
                            title = stringResource(Res.string.alpha_aml_check_title_description),
                            status = uiState.checkResults[IssueType.AlphaAml]
                        )
                    }
                    VSpacer(16.dp)

                    SecurityCheckCard(
                        title = stringResource(Res.string.SettingsAddressChecker_ChainalysisCheck),
                        icon = painterResource(Res.drawable.ic_chainalysis),
                        description = stringResource(Res.string.SettingsAddressChecker_ChainalysisCheckDescription),
                        onInfoClick = { info ->
                            bottomCheckInfo = info
                            coroutineScope.launch {
                                infoModalBottomSheetState.show()
                            }
                        }
                    ) {
                        NetworkItem(
                            title = stringResource(Res.string.SettingsAddressChecker_SanctionsCheck),
                            status = uiState.checkResults[IssueType.Chainalysis]
                        )
                    }
                    VSpacer(16.dp)

                    SecurityCheckCard(
                        title = stringResource(Res.string.SettingsAddressChecker_HashditCheck),
                        icon = painterResource(Res.drawable.ic_hashdit),
                        description = stringResource(Res.string.SettingsAddressChecker_HashditCheckDescription),
                        onInfoClick = { info ->
                            bottomCheckInfo = info
                            coroutineScope.launch {
                                infoModalBottomSheetState.show()
                            }
                        }
                    ) {
                        viewModel.hashDitBlockchains.forEach { blockchain ->
                            NetworkItem(
                                title = stringResource(Res.string.SettingsAddressChecker_OnSubject)
                                    .format(blockchain.name),
                                status = uiState.checkResults[IssueType.HashDit(blockchain.type)]
                            )
                        }
                    }
                    VSpacer(16.dp)

                    viewModel.contractFullCoins.forEach { fullCoin ->
                        val description = when (fullCoin.coin.uid) {
                            "tether" -> stringResource(Res.string.SettingsAddressChecker_TetherBlacklistDescription)
                            "usd-coin" -> stringResource(Res.string.SettingsAddressChecker_UsdcBlacklistDescription)
                            "paypal-usd" -> stringResource(Res.string.SettingsAddressChecker_PyusdBlacklistDescription)
                            else -> stringResource(Res.string.Error)
                        }
                        SecurityCheckCard(
                            title = stringResource(Res.string.SettingsAddressChecker_TokenBlacklistCheck)
                                .format(fullCoin.coin.name),
                            icon = rememberAsyncImagePainter(
                                model = fullCoin.coin.imageUrl,
                                error = placeholderPainter(Res.drawable.coin_placeholder)
                            ),
                            description = description,
                            onInfoClick = { info ->
                                bottomCheckInfo = info
                                coroutineScope.launch {
                                    infoModalBottomSheetState.show()
                                }
                            }
                        ) {
                            fullCoin.tokens.forEach { token ->
                                NetworkItem(
                                    title = stringResource(Res.string.SettingsAddressChecker_OnSubject)
                                        .format(token.blockchain.name),
                                    status = uiState.checkResults[IssueType.Contract(token)]
                                )
                            }
                        }
                        VSpacer(16.dp)
                    }

                    VSpacer(32.dp)
                }
            }
        }

        bottomCheckInfo?.let { info ->
            CheckInfoBottomSheet(
                title = info.title,
                description = info.description,
                bottomSheetState = infoModalBottomSheetState,
                hideBottomSheet = {
                    coroutineScope.launch {
                        infoModalBottomSheetState.hide()
                    }
                    bottomCheckInfo = null
                }
            )
        }
    }
}

@Composable
private fun ValidationError(addressValidationError: Throwable) {
    TextImportantError(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = Res.drawable.ic_attention_20,
        title = stringResource(Res.string.SwapSettings_Error_InvalidAddress),
        text = addressValidationError.getErrorMessage()
            ?: stringResource(Res.string.SettingsAddressChecker_InvalidAddress)
    )
    VSpacer(32.dp)
}

@Composable
private fun Throwable.getErrorMessage() = when (this) {
    is StellarAssetAdapter.NoTrustlineError -> {
        stringResource(Res.string.Error_AssetNotEnabled).format(code)
    }

    else -> this.message
}

@Composable
fun SecurityCheckCard(
    title: String,
    icon: Painter,
    infoTitle: String = title,
    description: String,
    onInfoClick: (BottomCheckInfo) -> Unit = {},
    results: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        backgroundColor = ComposeAppTheme.colors.backgroundBase,
        border = BorderStroke(0.5.dp, ComposeAppTheme.colors.borderDivider),
        shape = RoundedCornerShape(12.dp),
        elevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = "Check Icon",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            color = ComposeAppTheme.colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = "Info",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = {
                            onInfoClick.invoke(
                                BottomCheckInfo(infoTitle, description)
                            )
                        })
                )
            }

            HsDivider(
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.fillMaxWidth()
            )

            results()
        }
    }
}

@Composable
fun NetworkItem(
    title: String,
    status: CheckState?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null) base.clickable(onClick = onClick) else base
            }
            .height(40.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = ComposeAppTheme.colors.textPrimary,
            fontSize = 14.sp
        )

        status?.let {
            when (status) {
                CheckState.Checking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ComposeAppTheme.colors.textSecondary
                    )
                }

                CheckState.Locked -> {
                    Icon(
                        painter = painterResource(Res.drawable.ic_lock_20),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.iconSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                else -> {
                    val color = when (status) {
                        CheckState.Clear -> ComposeAppTheme.colors.statusSuccess
                        CheckState.Detected -> ComposeAppTheme.colors.statusError
                        CheckState.AlphaAmlVeryLow -> ComposeAppTheme.colors.statusSuccess
                        CheckState.AlphaAmlLow -> ComposeAppTheme.colors.statusWarning
                        CheckState.AlphaAmlHigh -> ComposeAppTheme.colors.statusWarning
                        CheckState.AlphaAmlVeryHigh -> ComposeAppTheme.colors.statusError
                        else -> ComposeAppTheme.colors.textSecondary
                    }
                    Text(
                        text = stringResource(status.title),
                        color = color,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInfoBottomSheet(
    title: String,
    description: String,
    hideBottomSheet: () -> Unit,
    bottomSheetState: SheetState
) {

    TransparentModalBottomSheet(
        onDismissRequest = hideBottomSheet,
        sheetState = bottomSheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_info_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.iconSecondary),
            title = title,
            titleColor = ComposeAppTheme.colors.textPrimary,
            onCloseClick = hideBottomSheet
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                body_leah(
                    text = description,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                VSpacer(36.dp)
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.Button_Close),
                    onClick = hideBottomSheet
                )
                VSpacer(32.dp)
            }
        }
    }
}

data class BottomCheckInfo(
    val title: String,
    val description: String,
)
