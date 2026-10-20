package cash.p.terminal.modules.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.ui_compose.components.AlertGroup
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Appearance_PriceChangeInterval
import cash.p.terminal.resources.Appearance_BalanceValue
import cash.p.terminal.resources.Settings_Theme
import cash.p.terminal.resources.Settings_LaunchScreen
import cash.p.terminal.resources.Appearance_AppIcon
import cash.p.terminal.resources.Appearance_BalanceTab
import cash.p.terminal.resources.Appearance_HideBalanceTabButtons
import cash.p.terminal.resources.Appearance_HideMarketsTab
import cash.p.terminal.resources.Appearance_MarketsTab
import cash.p.terminal.resources.Settings_Appearance
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_down_arrow_20
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal class AppearanceActions(
    val onThemeSelect: (ThemeType) -> Unit,
    val onLaunchScreenSelect: (LaunchPage) -> Unit,
    val onBalanceViewTypeSelect: (BalanceViewType) -> Unit,
    val onPriceChangeIntervalSelect: (PriceChangeInterval) -> Unit,
    val onMarketTabsHiddenChange: (Boolean) -> Unit,
    val onBalanceTabButtonsHiddenChange: (Boolean) -> Unit,
)

private enum class AppearanceSelector { Theme, LaunchScreen, BalanceValue, PriceChangeInterval }

@Composable
internal fun AppearanceScreen(
    uiState: AppearanceUIState,
    actions: AppearanceActions,
    onClose: () -> Unit,
    onAppIconClick: (() -> Unit)? = null,
) {
    var openSelector by rememberSaveable { mutableStateOf<AppearanceSelector?>(null) }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_Appearance),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
                menuItems = listOf()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
        ) {
            VSpacer(height = 12.dp)
            CellUniversalLawrenceSection(
                listOf {
                    MenuItemWithDialog(
                        Res.string.Settings_Theme,
                        value = uiState.selectedTheme.title.getString(),
                        onClick = { openSelector = AppearanceSelector.Theme }
                    )
                }
            )

            VSpacer(24.dp)
            MarketsTabSection(
                uiState = uiState,
                onHiddenChange = actions.onMarketTabsHiddenChange,
                onPriceChangeIntervalClick = { openSelector = AppearanceSelector.PriceChangeInterval },
                onLaunchScreenClick = { openSelector = AppearanceSelector.LaunchScreen }
            )

            VSpacer(24.dp)
            BalanceTabSection(
                uiState = uiState,
                onButtonsHiddenChange = actions.onBalanceTabButtonsHiddenChange,
                onBalanceValueClick = { openSelector = AppearanceSelector.BalanceValue }
            )

            onAppIconClick?.let { onClick ->
                VSpacer(22.dp)
                CellUniversalLawrenceSection(listOf { AppIconRow(onClick) })
            }

            VSpacer(32.dp)
        }
    }

    AppearanceSelectorDialog(openSelector, uiState, actions) { openSelector = null }
}

@Composable
private fun AppearanceSelectorDialog(
    selector: AppearanceSelector?,
    uiState: AppearanceUIState,
    actions: AppearanceActions,
    onDismiss: () -> Unit,
) {
    when (selector) {
        AppearanceSelector.Theme ->
            SelectorDialog(Res.string.Settings_Theme, uiState.themeOptions, actions.onThemeSelect, onDismiss)

        AppearanceSelector.LaunchScreen -> SelectorDialog(
            Res.string.Settings_LaunchScreen, uiState.launchScreenOptions, actions.onLaunchScreenSelect, onDismiss
        )

        AppearanceSelector.BalanceValue -> SelectorDialog(
            Res.string.Appearance_BalanceValue,
            uiState.balanceViewTypeOptions,
            actions.onBalanceViewTypeSelect,
            onDismiss
        )

        AppearanceSelector.PriceChangeInterval -> SelectorDialog(
            Res.string.Appearance_PriceChangeInterval,
            uiState.priceChangeIntervalOptions,
            actions.onPriceChangeIntervalSelect,
            onDismiss
        )

        null -> Unit
    }
}

@Composable
private fun MarketsTabSection(
    uiState: AppearanceUIState,
    onHiddenChange: (Boolean) -> Unit,
    onPriceChangeIntervalClick: () -> Unit,
    onLaunchScreenClick: () -> Unit,
) {
    HeaderText(text = stringResource(Res.string.Appearance_MarketsTab))
    CellUniversalLawrenceSection(
        listOf(
            {
                SwitchRow(
                    title = Res.string.Appearance_HideMarketsTab,
                    checked = uiState.marketsTabHidden,
                    onCheckedChange = onHiddenChange
                )
            },
            {
                MenuItemWithDialog(
                    Res.string.Appearance_PriceChangeInterval,
                    value = uiState.priceChangeInterval.title.getString(),
                    onClick = onPriceChangeIntervalClick
                )
            }
        )
    )

    AnimatedVisibility(visible = !uiState.marketsTabHidden) {
        Column {
            VSpacer(32.dp)
            CellUniversalLawrenceSection(
                listOf {
                    MenuItemWithDialog(
                        Res.string.Settings_LaunchScreen,
                        value = uiState.selectedLaunchScreen.title.getString(),
                        onClick = onLaunchScreenClick
                    )
                }
            )
        }
    }
}

@Composable
private fun BalanceTabSection(
    uiState: AppearanceUIState,
    onButtonsHiddenChange: (Boolean) -> Unit,
    onBalanceValueClick: () -> Unit,
) {
    HeaderText(text = stringResource(Res.string.Appearance_BalanceTab))
    CellUniversalLawrenceSection(
        listOf(
            {
                SwitchRow(
                    title = Res.string.Appearance_HideBalanceTabButtons,
                    checked = uiState.balanceTabButtonsHidden,
                    onCheckedChange = onButtonsHiddenChange
                )
            },
            {
                MenuItemWithDialog(
                    Res.string.Appearance_BalanceValue,
                    value = uiState.selectedBalanceViewType.title.getString(),
                    onClick = onBalanceValueClick
                )
            }
        )
    )
}

@Composable
private fun <T : WithTranslatableTitle> SelectorDialog(
    title: StringResource,
    options: Select<T>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertGroup(
        title,
        options,
        { selected ->
            onSelect(selected)
            onDismiss()
        },
        onDismiss
    )
}

@Composable
private fun SwitchRow(
    title: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        body_leah(
            text = stringResource(title),
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
        HsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AppIconRow(onClick: () -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        body_leah(
            text = stringResource(Res.string.Appearance_AppIcon),
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}

@Composable
internal fun MenuItemWithDialog(
    title: StringResource,
    value: String,
    onClick: () -> Unit
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        body_leah(
            text = stringResource(title),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        subhead1_grey(
            text = value,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Image(
            modifier = Modifier.size(20.dp),
            painter = painterResource(Res.drawable.ic_down_arrow_20),
            contentDescription = null
        )
    }
}
