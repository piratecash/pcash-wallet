package cash.p.terminal.modules.coin.reports

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.CoinPage_Reports
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SyncError
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui.compose.components.CellNews
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CoinReportsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        CoinReportsScreen(
            viewModel = koinViewModel { parametersOf(input.coinUid) },
            onClickNavigation = navigation::navigateUpSafely,
            onClickReportUrl = LocalUrlOpener.current::open
        )
    }

    @CommonParcelize
    data class Input(val coinUid: String) : CommonParcelable
}

@Composable
private fun CoinReportsScreen(
    viewModel: CoinReportsViewModel,
    onClickNavigation: () -> Unit,
    onClickReportUrl: (url: String) -> Unit
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val reportViewItems by viewModel.reportViewItems.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.CoinPage_Reports),
                navigationIcon = {
                    HsBackButton(onClick = onClickNavigation)
                }
            )
        }
    ) { padding ->
        HSSwipeRefresh(
            refreshing = isRefreshing,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            onRefresh = viewModel::refresh,
            content = {
                Crossfade(viewState, label = "") { viewState ->
                    when (viewState) {
                        ViewState.Loading -> {
                            Loading()
                        }

                        is ViewState.Error -> {
                            ListErrorView(
                                stringResource(Res.string.SyncError),
                                viewModel::onErrorClick
                            )
                        }

                        ViewState.Success -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                reportViewItems?.let {
                                    items(it) { report ->
                                        Spacer(modifier = Modifier.height(12.dp))
                                        CellNews(
                                            source = report.author,
                                            title = report.title,
                                            body = report.body,
                                            date = report.date,
                                        ) {
                                            onClickReportUrl(report.url)
                                        }
                                    }
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }

                    }
                }
            }
        )
    }
}
