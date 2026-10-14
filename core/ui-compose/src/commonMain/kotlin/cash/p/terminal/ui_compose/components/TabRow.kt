package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

// M3 sizes a tab row from the tabs' intrinsic height, so fillMaxHeight would collapse it to the label.
private val TabsHeight = 44.dp
private val TabIndicatorHeight = 2.dp

data class TabItem<T>(
    val title: String,
    val selected: Boolean,
    val item: T,
    val icon: ImageSource? = null,
    val label: String? = null,
    val enabled: Boolean = true
)

@Composable
fun <T> Tabs(tabs: List<TabItem<T>>, modifier: Modifier = Modifier, onClick: (T) -> Unit) {
    val selectedIndex = tabs.indexOfFirst { it.selected }

    Box(
        modifier = modifier
            .background(ComposeAppTheme.colors.backgroundBase)
            .height(TabsHeight)
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        TabRow(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .height(TabsHeight),
            selectedTabIndex = selectedIndex,
            containerColor = ComposeAppTheme.colors.transparent,
            contentColor = ComposeAppTheme.colors.backgroundBase,
            divider = {},
            indicator = @Composable { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedIndex])
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                    height = TabIndicatorHeight,
                    color = ComposeAppTheme.colors.brandDefault
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    modifier = Modifier
                        .height(TabsHeight)
                        .padding(horizontal = 12.dp),
                    selected = tab.selected,
                    onClick = {
                        onClick.invoke(tab.item)
                    },
                    content = {
                        ProvideTextStyle(
                            ComposeAppTheme.typography.subhead1
                        ) {
                            Text(
                                text = tab.title,
                                color = if (selectedIndex == index) {
                                    ComposeAppTheme.colors.textPrimary
                                } else {
                                    ComposeAppTheme.colors.textSecondary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    })
            }
        }
    }
}

@Composable
fun <T> ScrollableTabs(tabs: List<TabItem<T>>, onClick: (T) -> Unit) {
    val selectedIndex = tabs.indexOfFirst { it.selected }

    Box(
        modifier = Modifier
            .background(ComposeAppTheme.colors.backgroundBase)
            .height(TabsHeight)
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider,
        )

        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = ComposeAppTheme.colors.transparent,
            contentColor = ComposeAppTheme.colors.backgroundBase,
            edgePadding = 16.dp,
            divider = {},
            indicator = @Composable { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedIndex])
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                    height = TabIndicatorHeight,
                    color = ComposeAppTheme.colors.brandDefault
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    modifier = Modifier
                        .height(TabsHeight)
                        .padding(horizontal = 12.dp),
                    selected = tab.selected,
                    onClick = {
                        onClick.invoke(tab.item)
                    },
                    content = {
                        ProvideTextStyle(
                            ComposeAppTheme.typography.subhead1
                        ) {
                            Text(
                                text = tab.title,
                                color = if (tab.selected) {
                                    ComposeAppTheme.colors.textPrimary
                                } else {
                                    ComposeAppTheme.colors.textSecondary
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
