package cash.p.terminal.modules.balance.token

import cash.p.terminal.resources.ic_arrow_right
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.displayoptions.PriceParametersSection
import cash.p.terminal.modules.offline.OfflineModeConfirmationBottomSheet
import cash.p.terminal.modules.offline.OfflineModeInfoBottomSheet
import cash.p.terminal.modules.offline.OfflineModeToggleUiState
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.transactions.AmlCheckInfoBottomSheet
import cash.p.terminal.modules.transactions.AmlCheckRow
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_BirthdayHeight
import cash.p.terminal.resources.Settings_Title
import cash.p.terminal.resources.address_poisoning_view
import cash.p.terminal.resources.display_options_price_period
import cash.p.terminal.resources.ic_flask_20
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.offline_mode_title
import cash.p.terminal.resources.transaction_filter
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AssetSettingsScreen(
    amlCheckEnabled: Boolean,
    onAmlCheckChange: (Boolean) -> Unit,
    pricePeriod: DisplayPricePeriod,
    displayDiffOptionType: DisplayDiffOptionType,
    isRoundingAmount: Boolean,
    onPricePeriodChange: (DisplayPricePeriod) -> Unit,
    onDisplayDiffOptionTypeChange: (DisplayDiffOptionType) -> Unit,
    onRoundingAmountChange: (Boolean) -> Unit,
    onAddressPoisoningViewClick: () -> Unit,
    transactionFiltersEnabled: Boolean,
    onTransactionFiltersChange: (Boolean) -> Unit,
    offlineUiState: OfflineModeToggleUiState,
    onConfirmOffline: () -> Unit,
    onGoOnline: () -> Unit,
    onOfflineSheetDismiss: () -> Unit,
    navigation: HSNavigation,
    onBack: () -> Unit,
    creationBlockVisible: Boolean,
    currentHeightText: String?,
    onCreationBlockClick: () -> Unit,
) {
    var showAmlInfoSheet by remember { mutableStateOf(false) }
    var showPeriodSelector by remember { mutableStateOf(false) }
    var showOfflineInfoSheet by remember { mutableStateOf(false) }
    var showOfflineConfirmSheet by remember { mutableStateOf(false) }

    val sheetDismiss by rememberUpdatedState(onOfflineSheetDismiss)

    LaunchedEffect(offlineUiState.closeSheet) {
        if (offlineUiState.closeSheet) {
            showOfflineConfirmSheet = false
            sheetDismiss()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_Title),
                navigationIcon = { HsBackButton(onClick = onBack) }
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(12.dp)
            AmlCheckRow(
                enabled = amlCheckEnabled,
                onToggleChange = onAmlCheckChange,
                onInfoClick = { showAmlInfoSheet = true }
            )
            VSpacer(16.dp)

            PriceParametersSection(
                pricePeriod = pricePeriod,
                displayDiffOptionType = displayDiffOptionType,
                isRoundingAmount = isRoundingAmount,
                onPricePeriodClick = { showPeriodSelector = true },
                onPercentChangeToggled = { enabled ->
                    onDisplayDiffOptionTypeChange(
                        DisplayDiffOptionType.fromFlags(
                            priceChange = displayDiffOptionType.hasPriceChange,
                            percentChange = enabled
                        )
                    )
                },
                onPriceChangeToggled = { enabled ->
                    onDisplayDiffOptionTypeChange(
                        DisplayDiffOptionType.fromFlags(
                            priceChange = enabled,
                            percentChange = displayDiffOptionType.hasPercentChange
                        )
                    )
                },
                onRoundingAmountToggled = onRoundingAmountChange,
            )
            VSpacer(24.dp)
            CellUniversalLawrenceSection(
                listOf {
                    SwitchWithText(
                        text = stringResource(Res.string.transaction_filter),
                        checked = transactionFiltersEnabled,
                        onCheckedChange = onTransactionFiltersChange
                    )
                }
            )
            VSpacer(24.dp)
            CellUniversalLawrenceSection(
                listOf {
                    SwitchWithText(
                        text = stringResource(Res.string.offline_mode_title),
                        checked = offlineUiState.offline,
                        onCheckedChange = { checked ->
                            when {
                                !checked -> onGoOnline()
                                offlineUiState.confirmationRequired -> showOfflineConfirmSheet = true
                                else -> onConfirmOffline()
                            }
                        },
                        extraIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_info_20),
                                contentDescription = null,
                                tint = ComposeAppTheme.colors.iconSecondary,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 20.dp),
                                        onClick = { showOfflineInfoSheet = true }
                                    )
                            )
                        }
                    )
                }
            )
            InfoText(text = offlineUiState.description)
            VSpacer(24.dp)
            CellUniversalLawrenceSection(
                listOf {
                    HsSettingCell(
                        title = stringResource(Res.string.address_poisoning_view),
                        arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                        leadingPainter = painterResource(Res.drawable.ic_flask_20),
                        onClick = onAddressPoisoningViewClick
                    )
                }
            )
            if (creationBlockVisible) {
                VSpacer(24.dp)
                CellUniversalLawrenceSection(
                    listOf {
                        HsSettingCell(
                            title = stringResource(Res.string.Restore_BirthdayHeight),
                            arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                            value = currentHeightText,
                            onClick = onCreationBlockClick
                        )
                    }
                )
            }
        }
    }

    if (showAmlInfoSheet) {
        AmlCheckInfoBottomSheet(
            onPremiumSettingsClick = {
                showAmlInfoSheet = false
                navigation.slideFromRight(PremiumSettingsPage())
            },
            onLaterClick = { showAmlInfoSheet = false },
            onDismiss = { showAmlInfoSheet = false }
        )
    }

    if (showPeriodSelector) {
        AlertGroup(
            title = Res.string.display_options_price_period,
            select = Select(pricePeriod, DisplayPricePeriod.entries),
            onSelect = { selected ->
                onPricePeriodChange(selected)
                showPeriodSelector = false
            },
            onDismiss = { showPeriodSelector = false }
        )
    }

    if (showOfflineInfoSheet) {
        OfflineModeInfoBottomSheet(onDismiss = { showOfflineInfoSheet = false })
    }

    if (showOfflineConfirmSheet) {
        OfflineModeConfirmationBottomSheet(
            blockchainName = offlineUiState.blockchainName,
            isZcash = offlineUiState.isZcash,
            members = offlineUiState.members,
            inProgress = offlineUiState.inProgress,
            onConfirm = onConfirmOffline,
            onDismiss = { showOfflineConfirmSheet = false }
        )
    }
}
