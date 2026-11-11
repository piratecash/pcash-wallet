package cash.p.terminal.modules.displayoptions

import org.jetbrains.compose.resources.painterResource
import cash.p.terminal.resources.ic_arrow_right
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.nft.gram.rememberNftGramInfoOpener
import cash.p.terminal.resources.display_options_nft_hint
import cash.p.terminal.resources.display_options_nft_no_gram_off_hint
import cash.p.terminal.resources.display_options_nft_no_gram_on_hint
import cash.p.terminal.resources.display_options_nft_section
import cash.p.terminal.resources.display_options_show_nft_collections
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.nft_gram_info_title
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.managewallets.ManageWalletsPage
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.ManageCoins_title
import cash.p.terminal.resources.Market_FilterSection_PriceParameters
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.display_options
import cash.p.terminal.resources.display_options_percent_change
import cash.p.terminal.resources.display_options_price_change
import cash.p.terminal.resources.display_options_price_period
import cash.p.terminal.resources.sum_rounding
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DisplayOptionsScreen(
    navigation: HSNavigation,
    uiState: DisplayOptionsUiState,
    onPricePeriodChanged: (DisplayPricePeriod) -> Unit,
    onPercentChangeToggled: (Boolean) -> Unit,
    onPriceChangeToggled: (Boolean) -> Unit,
    onRoundingAmountMainPageToggled: (Boolean) -> Unit,
    onShowNftCollectionsToggle: (Boolean) -> Unit,
) {
    var showPeriodSelector by remember { mutableStateOf(false) }

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                title = stringResource(Res.string.display_options),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.isCoinManagerEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CellUniversalLawrenceSection(
                        listOf {
                            HsSettingCell(
                                title = stringResource(Res.string.ManageCoins_title),
                                arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                                onClick = {
                                    navigation.slideFromRight(ManageWalletsPage())
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                PriceParametersSection(
                    pricePeriod = uiState.pricePeriod,
                    displayDiffOptionType = uiState.displayDiffOptionType,
                    isRoundingAmount = uiState.isRoundingAmountMainPage,
                    onPricePeriodClick = { showPeriodSelector = true },
                    onPercentChangeToggled = onPercentChangeToggled,
                    onPriceChangeToggled = onPriceChangeToggled,
                    onRoundingAmountToggled = onRoundingAmountMainPageToggled,
                )

                uiState.nftSection?.let {
                    NftSection(it, onShowNftCollectionsToggle, navigation.rememberNftGramInfoOpener())
                }
            }
        }
    }

    // Period Selector Dialog
    if (showPeriodSelector) {
        AlertGroup(
            title = Res.string.display_options_price_period,
            select = Select(uiState.pricePeriod, DisplayPricePeriod.entries),
            onSelect = { selected ->
                onPricePeriodChanged(selected)
                showPeriodSelector = false
            },
            onDismiss = { showPeriodSelector = false }
        )
    }
}

@Composable
fun PriceParametersSection(
    pricePeriod: DisplayPricePeriod,
    displayDiffOptionType: DisplayDiffOptionType,
    isRoundingAmount: Boolean,
    onPricePeriodClick: () -> Unit,
    onPercentChangeToggled: (Boolean) -> Unit,
    onPriceChangeToggled: (Boolean) -> Unit,
    onRoundingAmountToggled: (Boolean) -> Unit,
) {
    InfoText(
        text = stringResource(Res.string.Market_FilterSection_PriceParameters).uppercase(),
        paddingBottom = 8.dp
    )

    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    title = stringResource(Res.string.display_options_price_period),
                    arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                    value = pricePeriod.shortForm.getString(),
                    onClick = onPricePeriodClick
                )
            },
            {
                SwitchWithText(
                    text = stringResource(Res.string.display_options_percent_change),
                    checked = displayDiffOptionType.hasPercentChange,
                    onCheckedChange = onPercentChangeToggled
                )
            },
            {
                SwitchWithText(
                    text = stringResource(Res.string.display_options_price_change),
                    checked = displayDiffOptionType.hasPriceChange,
                    onCheckedChange = onPriceChangeToggled
                )
            }
        )
    )

    CellUniversalLawrenceSection(
        listOf {
            SwitchWithText(
                text = stringResource(Res.string.sum_rounding),
                checked = isRoundingAmount,
                onCheckedChange = onRoundingAmountToggled
            )
        },
        Modifier.padding(top = 32.dp)
    )
}

@Composable
private fun NftSection(section: NftDisplaySection, onToggle: (Boolean) -> Unit, onInfo: () -> Unit) {
    Spacer(modifier = Modifier.height(32.dp))
    InfoText(text = stringResource(Res.string.display_options_nft_section), paddingBottom = 8.dp)
    CellUniversalLawrenceSection(
        listOf {
            SwitchWithText(
                text = stringResource(Res.string.display_options_show_nft_collections),
                checked = section.show,
                onCheckedChange = onToggle,
                extraIcon = if (section.hasGram) null else { { NftInfoIcon(onInfo) } },
            )
        }
    )
    InfoText(text = stringResource(section.hint))
}

private val NftDisplaySection.hint: StringResource
    get() = when {
        hasGram -> Res.string.display_options_nft_hint
        show -> Res.string.display_options_nft_no_gram_on_hint
        else -> Res.string.display_options_nft_no_gram_off_hint
    }

@Composable
private fun NftInfoIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_info_20),
            contentDescription = stringResource(Res.string.nft_gram_info_title),
            tint = ComposeAppTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NftSectionPreview(section: NftDisplaySection) {
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            NftSection(section = section, onToggle = {}, onInfo = {})
        }
    }
}

@Preview
@Composable
private fun NftSectionWithGramPreview() = NftSectionPreview(NftDisplaySection(show = true, hasGram = true))

@Preview
@Composable
private fun NftSectionNoGramOnPreview() = NftSectionPreview(NftDisplaySection(show = true, hasGram = false))

@Preview
@Composable
private fun NftSectionNoGramOffPreview() = NftSectionPreview(NftDisplaySection(show = false, hasGram = false))
