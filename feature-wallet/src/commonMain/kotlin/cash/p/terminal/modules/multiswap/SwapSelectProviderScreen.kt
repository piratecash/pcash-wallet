package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Done
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_manage_2_24
import cash.p.terminal.resources.swap_providers_title
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryWithIcon
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.micro_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Balance_Sort_PopupTitle
import cash.p.terminal.resources.Swap_FiatPriceImpact
import cash.p.terminal.resources.Swap_Providers
import cash.p.terminal.resources.ic_arrow_swap3_20
import cash.p.terminal.resources.ic_circle_minus_24
import cash.p.terminal.resources.ic_down_arrow_20

@Composable
fun SwapSelectProviderScreen(
    onClickClose: () -> Unit,
    onClickSettings: () -> Unit,
    quotes: List<QuoteViewItem>,
    currentQuote: SwapProviderQuote?,
    mandatoryProviderIds: Set<String>,
    disabledProviderIds: Set<String>,
    sortType: ProviderSortType,
    onSortTypeChange: (ProviderSortType) -> Unit,
    onToggleProvider: (providerId: String, disabled: Boolean) -> Unit,
    swapRates: () -> Unit,
    onSelectQuote: (SwapProviderQuote) -> Unit,
) {
    var revealedProviderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(quotes, disabledProviderIds) {
        val noEnabledProvidersLeft = quotes.isNotEmpty() &&
            quotes.all { it.quote.provider.id in disabledProviderIds }
        if (noEnabledProvidersLeft) {
            onClickClose()
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(Res.string.Swap_Providers),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.swap_providers_title),
                        icon = Res.drawable.ic_manage_2_24,
                        onClick = onClickSettings,
                    ),
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Done),
                        onClick = onClickClose
                    )
                ),
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) {
        LazyColumn(
            modifier = Modifier.padding(it),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                VSpacer(height = 4.dp)
                ProviderSortingSelector(
                    sortType = sortType,
                    sortTypes = ProviderSortType.entries,
                    onSelectSortType = onSortTypeChange,
                )
                VSpacer(height = 4.dp)
            }
            items(quotes, key = { it.quote.provider.id }) { viewItem ->
                val provider = viewItem.quote.provider
                val isDisabled = provider.id in disabledProviderIds
                val borderColor = if (provider == currentQuote?.provider) {
                    ComposeAppTheme.colors.statusWarning50
                } else {
                    ComposeAppTheme.colors.borderDefault
                }
                val isMandatory = provider.id in mandatoryProviderIds

                SwipableProviderItem(
                    viewItem = viewItem,
                    borderColor = borderColor,
                    swipeEnabled = !isMandatory,
                    disabled = isDisabled,
                    revealed = revealedProviderId == provider.id,
                    onReveal = { revealedProviderId = provider.id },
                    onConceal = { revealedProviderId = null },
                    onSelectQuote = onSelectQuote,
                    swapRates = swapRates,
                    onToggle = {
                        revealedProviderId = null
                        onToggleProvider(provider.id, !isDisabled)
                    },
                )
            }

            item {
                VSpacer(height = 24.dp)
            }

        }
    }
}

@Composable
private fun SwipableProviderItem(
    viewItem: QuoteViewItem,
    borderColor: Color,
    swipeEnabled: Boolean,
    disabled: Boolean,
    revealed: Boolean,
    onReveal: () -> Unit,
    onConceal: () -> Unit,
    onSelectQuote: (SwapProviderQuote) -> Unit,
    swapRates: () -> Unit,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HsIconButton(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .width(72.dp)
                .background(ComposeAppTheme.colors.backgroundBase),
            onClick = onToggle,
            content = {
                Icon(
                    painter = painterResource(Res.drawable.ic_circle_minus_24),
                    tint = ComposeAppTheme.colors.iconSecondary,
                    contentDescription = "toggle provider",
                )
            },
        )
        DraggableCardSimple(
            key = viewItem.quote.provider.id,
            isRevealed = revealed,
            cardOffset = 72f,
            onReveal = onReveal,
            onConceal = onConceal,
            enabled = swipeEnabled,
            content = {
                ProviderItem(
                    borderColor = borderColor,
                    onSelectQuote = onSelectQuote,
                    viewItem = viewItem,
                    swapRates = swapRates,
                    disabled = disabled,
                )
            },
        )
    }
}

@Composable
private fun ProviderItem(
    borderColor: Color,
    onSelectQuote: (SwapProviderQuote) -> Unit,
    viewItem: QuoteViewItem,
    swapRates: () -> Unit,
    disabled: Boolean ,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(ComposeAppTheme.colors.backgroundBase)
            .clickable(enabled = !disabled) { onSelectQuote(viewItem.quote) }
            .border(1.dp, borderColor, shape)
    ) {
        RowUniversal(
            modifier = Modifier
                .alpha(if (disabled) 0.4f else 1f)
                .padding(horizontal = 16.dp),
        ) {
            val provider = viewItem.quote.provider
            HsImage(
                url = provider.iconUrl,
                placeholder = provider.icon,
                modifier = Modifier.size(32.dp),
            )
            HSpacer(width = 16.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    body_leah(text = provider.title, modifier = Modifier.weight(1f))
                    HSpacer(width = 8.dp)
                    subhead2_leah(text = viewItem.tokenAmount, textAlign = TextAlign.End)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderRiskBadge(riskType = provider.riskType)
                    AccuracyBadge(viewItem.quote.amountOutAccuracy)
                    HFillSpacer(minWidth = 8.dp)
                    viewItem.fiatAmount?.let { fiatAmount ->
                        subhead2_grey(text = fiatAmount, textAlign = TextAlign.End)
                        viewItem.diffWithFirst?.let { diff ->
                            HSpacer(width = 4.dp)
                            Text(
                                text = stringResource(
                                    Res.string.Swap_FiatPriceImpact,
                                    diff.toPlainString()
                                ),
                                style = ComposeAppTheme.typography.subhead2,
                                color = getPriceImpactColor(PriceImpactLevel.Warning),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    viewItem.estimationTime?.let { estimationTime ->
                        EstimationTimeBadge(seconds = estimationTime)
                    }
                    HFillSpacer(minWidth = 8.dp)
                    ExchangeBlock(
                        from = viewItem.rateFrom,
                        to = viewItem.rateTo,
                        swapRates = {
                            onSelectQuote.invoke(viewItem.quote)
                            swapRates()
                        },
                        modifier = Modifier,
                        enabled = !disabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccuracyBadge(accuracy: SwapAmountAccuracy) {
    val symbol = when (accuracy) {
        SwapAmountAccuracy.Exact -> return
        SwapAmountAccuracy.AtLeast -> "≥"
        SwapAmountAccuracy.Estimated -> "≈"
    }
    HSpacer(width = 6.dp)
    micro_grey(text = symbol)
}

@Composable
private fun ExchangeBlock(
    from: String,
    to: String,
    swapRates: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    val rowModifier = if (enabled) {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = swapRates
        )
    } else {
        modifier
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        subhead2_grey(
            text = from
        )
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_swap3_20),
            contentDescription = "invert price",
            tint = ComposeAppTheme.colors.brandDefault
        )
        subhead2_grey(
            text = to
        )
    }
}

@Composable
private fun ProviderSortingSelector(
    sortType: ProviderSortType,
    sortTypes: List<ProviderSortType>,
    onSelectSortType: (ProviderSortType) -> Unit,
) {
    var showSortTypeSelectorDialog by remember { mutableStateOf(false) }

    ButtonSecondaryWithIcon(
        title = stringResource(sortType.titleRes),
        iconRight = painterResource(Res.drawable.ic_down_arrow_20),
        onClick = { showSortTypeSelectorDialog = true }
    )

    if (showSortTypeSelectorDialog) {
        AppSelectorDialog(
            title = stringResource(Res.string.Balance_Sort_PopupTitle),
            items = sortTypes.map {
                AppSelectorItem(stringResource(it.titleRes), it == sortType, it)
            },
            onDismiss = { showSortTypeSelectorDialog = false },
            onSelect = onSelectSortType
        )
    }
}
