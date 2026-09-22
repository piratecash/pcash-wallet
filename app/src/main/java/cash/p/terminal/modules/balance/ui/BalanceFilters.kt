package cash.p.terminal.modules.balance.ui

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.ui.compose.components.AlertGroup
import cash.p.terminal.ui.compose.components.SelectorDialogCompose
import cash.p.terminal.ui.compose.components.SelectorItem
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.components.balanceSurfaceIndication
import cash.p.terminal.ui_compose.components.Subhead1Filter
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.BalanceSortType

@Composable
internal fun BalanceFilters(
    sort: Select<BalanceSortType>,
    displayDiffOptionType: DisplayDiffOptionType,
    displayPricePeriod: DisplayPricePeriod,
    isWatchAccount: Boolean,
    onSelectSortType: (BalanceSortType) -> Unit,
    onDisplayPricePeriod: (DisplayPricePeriod) -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.tyler)
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BalanceSortingSelector(
            sort = sort,
            onSelectSortType = onSelectSortType,
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isWatchAccount) {
                Image(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.icon_binocule_24),
                    contentDescription = null,
                )
                Spacer(
                    Modifier.size(
                        if (displayDiffOptionType == DisplayDiffOptionType.NONE) 4.dp else 8.dp
                    )
                )
            }
            if (displayDiffOptionType != DisplayDiffOptionType.NONE) {
                PricePeriodSelector(
                    displayPricePeriod = displayPricePeriod,
                    onDisplayPricePeriod = onDisplayPricePeriod,
                )
                Spacer(Modifier.size(4.dp))
            }
            FilterSettingsButton(onClick = onSettingsClick)
        }
    }
}

@Composable
private fun BalanceSortingSelector(
    sort: Select<BalanceSortType>,
    onSelectSortType: (BalanceSortType) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    FilterButton(
        label = stringResource(sort.selected.getTitleRes()),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        SelectorDialogCompose(
            title = stringResource(R.string.Balance_Sort_PopupTitle),
            items = sort.options.map {
                SelectorItem(stringResource(it.getTitleRes()), it == sort.selected, it)
            },
            onDismissRequest = { showDialog = false },
            onSelectItem = onSelectSortType,
        )
    }
}

@Composable
private fun PricePeriodSelector(
    displayPricePeriod: DisplayPricePeriod,
    onDisplayPricePeriod: (DisplayPricePeriod) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    FilterButton(
        label = displayPricePeriod.shortForm.getString().lowercase(locale),
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AlertGroup(
            title = R.string.display_options_price_period,
            select = Select(displayPricePeriod, DisplayPricePeriod.entries),
            onSelect = { selected ->
                onDisplayPricePeriod(selected)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun FilterButton(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(ComposeAppTheme.colors.filterBackground)
                .border(
                    width = 1.dp,
                    color = ComposeAppTheme.colors.filterBorder,
                    shape = RoundedCornerShape(100.dp),
                )
                .balanceSurfaceIndication(interactionSource)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Subhead1Filter(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_balance_chevron_down_16),
                contentDescription = null,
                tint = ComposeAppTheme.colors.filterText,
            )
        }
    }
}

@Composable
private fun FilterSettingsButton(onClick: () -> Unit) {
    FilterIconButton(
        icon = R.drawable.ic_manage_2,
        contentDescription = stringResource(R.string.ManageCoins_title),
        onClick = onClick,
    )
}

@Composable
private fun FilterIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(start = 4.dp, end = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(ComposeAppTheme.colors.filterBackground)
                .border(1.dp, ComposeAppTheme.colors.filterBorder, RoundedCornerShape(9.dp))
                .balanceSurfaceIndication(interactionSource),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = ComposeAppTheme.colors.filterText,
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceFiltersPreview() {
    ComposeAppTheme {
        val sort = Select(
            BalanceSortType.Value,
            listOf(BalanceSortType.Value, BalanceSortType.Name, BalanceSortType.PercentGrowth),
        )
        Column(modifier = Modifier.background(ComposeAppTheme.colors.tyler)) {
            listOf(
                DisplayDiffOptionType.BOTH to false,
                DisplayDiffOptionType.NONE to false,
                DisplayDiffOptionType.BOTH to true,
            ).forEach { (displayDiffOptionType, isWatchAccount) ->
                BalanceFilters(
                    sort = sort,
                    displayDiffOptionType = displayDiffOptionType,
                    displayPricePeriod = DisplayPricePeriod.ONE_DAY,
                    isWatchAccount = isWatchAccount,
                    onSelectSortType = {},
                    onDisplayPricePeriod = {},
                    onSettingsClick = {},
                )
            }
        }
    }
}
