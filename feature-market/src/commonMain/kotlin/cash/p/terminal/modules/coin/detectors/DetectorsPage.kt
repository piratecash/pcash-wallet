package cash.p.terminal.modules.coin.detectors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Detectors_IssuesCount
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_big_down_20
import cash.p.terminal.resources.ic_arrow_big_up_20
import cash.p.terminal.resources.ic_check_24
import cash.p.terminal.resources.ic_warning_24
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.coin.detectors.DetectorsModule.DetectorsTab
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.Tabs
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class DetectorsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<DetectorsViewModel> { parametersOf(input.title, input.issues) }
        DetectorsScreen(
            viewModel = viewModel,
            onBackClick = navigation::navigateUpSafely,
        )
    }

    @CommonParcelize
    data class Input(val title: String, val issues: List<IssueParcelable>) : CommonParcelable
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetectorsScreen(
    viewModel: DetectorsViewModel,
    onBackClick: () -> Unit,
) {

    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = uiState.title,
                navigationIcon = {
                    HsBackButton(onClick = onBackClick)
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            val tabs = DetectorsTab.values()
            var selectedTab by remember { mutableStateOf(DetectorsTab.Token) }
            val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
            LaunchedEffect(key1 = selectedTab, block = {
                pagerState.scrollToPage(selectedTab.ordinal)
            })
            val tabItems = tabs.map {
                TabItem(stringResource(it.title), it == selectedTab, it)
            }
            Tabs(tabItems, onClick = { selectedTab = it })

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false
            ) { page ->
                when (tabs[page]) {
                    DetectorsTab.Token -> IssueList(uiState.coreIssues) { id ->
                        viewModel.toggleExpandCore(id)
                    }

                    DetectorsTab.General -> IssueList(uiState.generalIssues) { id ->
                        viewModel.toggleExpandGeneral(id)
                    }
                }
            }
        }
    }
}

@Composable
fun IssueList(
    issues: List<DetectorsModule.IssueViewItem>,
    toggleExpand: (Int) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            VSpacer(12.dp)
        }
        itemsIndexed(issues) { index, issue ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ComposeAppTheme.colors.borderDivider,
                )
            }
            DetectorCell(
                issueViewItem = issue,
            ) {
                toggleExpand(it)
            }
        }
    }
}

@Composable
fun DetectorCell(
    issueViewItem: DetectorsModule.IssueViewItem,
    toggleExpand: (Int) -> Unit
) {
    val issue = issueViewItem.issue
    val issues = issue.issues ?: emptyList()
    var iconResource = Res.drawable.ic_check_24
    var iconTint = ComposeAppTheme.colors.iconPrimary

    issues.firstOrNull()?.let {
        when (it.impact) {
            "Critical" -> {
                iconResource = Res.drawable.ic_warning_24
                iconTint = ComposeAppTheme.colors.statusError
            }

            "High" -> {
                iconResource = Res.drawable.ic_warning_24
                iconTint = ComposeAppTheme.colors.statusWarning
            }

            "Low" -> {
                iconResource = Res.drawable.ic_warning_24
                iconTint = ComposeAppTheme.colors.statusSuccess
            }

            "Informational",
            "Optimization" -> {
                if (issues.isNotEmpty()) {
                    iconResource = Res.drawable.ic_warning_24
                    iconTint = ComposeAppTheme.colors.brandDefault
                }
            }

            else -> {}
        }
    }

    Column(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            toggleExpand.invoke(issueViewItem.id)
        }
    ) {
        RowUniversal(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposeAppTheme.colors.surfacePrimary)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = iconTint
            )
            HSpacer(width = 16.dp)
            if (issue.title != null) {
                Column(modifier = Modifier.weight(1f)) {
                    body_leah(
                        text = issue.title,
                    )
                    VSpacer(1.dp)
                    subhead2_grey(
                        text = issue.description,
                    )
                }
            } else {
                subhead2_leah(
                    text = issue.description,
                    modifier = Modifier.weight(1f)
                )
            }

            if (issues.isNotEmpty()) {
                val painter = if (issueViewItem.expanded) {
                    painterResource(Res.drawable.ic_arrow_big_up_20)
                } else {
                    painterResource(Res.drawable.ic_arrow_big_down_20)
                }

                HSpacer(width = 8.dp)
                subhead1_grey(
                    text = TranslatableString.ResString(
                        Res.string.Detectors_IssuesCount,
                        issues.size
                    ).getString()
                )
                Icon(
                    modifier = Modifier.padding(start = 8.dp),
                    painter = painter,
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
        }

        AnimatedVisibility(
            visible = issueViewItem.expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                issues.forEachIndexed { index, text ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = ComposeAppTheme.colors.borderDivider,
                        )
                    }
                    InfoText(
                        text = text.description,
                        paddingBottom = 32.dp
                    )
                }
            }
        }
    }
}
