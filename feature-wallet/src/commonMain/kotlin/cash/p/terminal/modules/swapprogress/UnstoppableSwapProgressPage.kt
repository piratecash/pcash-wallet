package cash.p.terminal.modules.swapprogress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.transactionInfo.TransactionInfoSection
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.swap_progress_title
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class UnstoppableSwapProgressPage(
    private val swapDate: Long,
    private val transactionUid: String,
    private val walletUid: String?,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<UnstoppableSwapProgressViewModel> { parametersOf(swapDate, transactionUid, walletUid) }

        PageResumeEffect(onResume = viewModel::onResume, onPause = viewModel::onPause)

        UnstoppableSwapProgressScreen(
            uiState = viewModel.uiState,
            onSensitiveValueClick = viewModel::toggleBalanceVisibility,
            navigation = navigation,
        )
    }
}

@Composable
private fun UnstoppableSwapProgressScreen(
    uiState: UnstoppableSwapProgressUiState,
    onSensitiveValueClick: () -> Unit,
    navigation: HSNavigation,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.swap_progress_title),
                navigationIcon = { HsBackButton(onClick = navigation::navigateUpSafely) },
                menuItems = listOf(),
            )
        }
    ) { paddingValues ->
        if (uiState.loading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                HSCircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues).navigationBarsPadding(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            ) {
                items(uiState.sections) { section ->
                    val stepHeader = section.singleOrNull() as? TransactionInfoViewItem.Description
                    Column {
                        if (stepHeader != null) {
                            HeaderText(stepHeader.text)
                        } else {
                            TransactionInfoSection(
                                section = section,
                                navigation = navigation,
                                onSensitiveValueClick = onSensitiveValueClick,
                                getRawTransaction = { null },
                                hideSensitiveInfo = uiState.hideAmount,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun UnstoppableSwapProgressScreenPreview() {
    val noOpUrlOpener = object : UrlOpener {
        override fun open(url: String) = Unit
    }
    ComposeAppTheme {
        CompositionLocalProvider(LocalUrlOpener provides noOpUrlOpener) {
            UnstoppableSwapProgressScreen(
                uiState = UnstoppableSwapProgressUiState(
                    loading = false,
                    sections = listOf(
                        listOf(TransactionInfoViewItem.Description("Step 1 · Sending ZEC")),
                        listOf(
                            TransactionInfoViewItem.Status(TransactionStatus.Completed),
                            TransactionInfoViewItem.ValueClickable("Amount", "0.004011 ZEC"),
                            TransactionInfoViewItem.TransactionHash("738a93acc1f0c2e6c187af87d9"),
                            TransactionInfoViewItem.Explorer("View on blockchair.com", "https://blockchair.com"),
                        ),
                        listOf(TransactionInfoViewItem.Description("Step 2 · Swap ZEC → ETH")),
                        listOf(
                            TransactionInfoViewItem.Status(TransactionStatus.Processing(0.5f)),
                            TransactionInfoViewItem.ValueClickable("You Send", "≈ 0.004011 ZEC"),
                            TransactionInfoViewItem.ValueClickable("You Get", "≈ 0.002166 ETH"),
                        ),
                        listOf(TransactionInfoViewItem.Description("Step 3 · Receiving ETH")),
                        listOf(
                            TransactionInfoViewItem.Status(TransactionStatus.Pending),
                            TransactionInfoViewItem.ValueClickable("Amount", "≈ 0.002166 ETH"),
                        ),
                    ),
                ),
                onSensitiveValueClick = {},
                navigation = HSNavigation(NavBackStack()),
            )
        }
    }
}
