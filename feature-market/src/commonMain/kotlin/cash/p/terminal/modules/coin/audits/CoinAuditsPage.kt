package cash.p.terminal.modules.coin.audits

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.CoinPage_Audits_PoweredBy
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SendNft_Title
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellFooter
import cash.p.terminal.ui_compose.components.CellMultilineLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class CoinAuditsPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<CoinAuditsViewModel> { parametersOf(input.audits) }
        CoinAuditsScreen(
            viewModel = viewModel,
            onPressBack = navigation::navigateUpSafely,
            onClickReportUrl = LocalUrlOpener.current::open
        )
    }

    @CommonParcelize
    data class Input(val audits: List<CoinAuditsModule.AuditParcelable>) : CommonParcelable
}

@Composable
private fun CoinAuditsScreen(
    viewModel: CoinAuditsViewModel,
    onPressBack: () -> Unit,
    onClickReportUrl: (url: String) -> Unit
) {
    val uiState = viewModel.uiState

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.SendNft_Title),
                navigationIcon = {
                    HsBackButton(onClick = onPressBack)
                },
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                uiState.auditors.forEach { viewItem ->
                    item {
                        CoinAuditHeader(viewItem.name, viewItem.logoUrl)
                    }
                    item {
                        CellMultilineLawrenceSection(viewItem.auditViewItems) { auditViewItem ->
                            CoinAudit(auditViewItem) {
                                auditViewItem.reportUrl?.let {
                                    onClickReportUrl(
                                        it
                                    )
                                }
                            }
                        }
                        VSpacer(24.dp)
                    }
                }
            }

            CellFooter(text = stringResource(Res.string.CoinPage_Audits_PoweredBy))
        }
    }
}

@Composable
fun CoinAuditHeader(name: String, logoUrl: String) {
    HorizontalDivider(
        thickness = 1.dp,
        color = ComposeAppTheme.colors.borderDivider,
    )
    VSpacer(height = 14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        HsImage(
            url = logoUrl,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
        )
        body_leah(text = name)
    }
    VSpacer(height = 14.dp)
}

@Composable
fun CoinAudit(auditViewItem: CoinAuditsModule.AuditViewItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick, enabled = auditViewItem.reportUrl != null)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = auditViewItem.date ?: "")
            subhead2_grey(
                text = auditViewItem.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        subhead2_grey(text = auditViewItem.issues.getString())

        if (auditViewItem.reportUrl != null) {
            Image(painterResource(Res.drawable.ic_arrow_right), contentDescription = "")
        }
    }
}
