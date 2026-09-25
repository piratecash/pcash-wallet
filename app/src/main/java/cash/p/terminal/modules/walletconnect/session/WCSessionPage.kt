package cash.p.terminal.modules.walletconnect.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.walletconnect.session.ui.StatusCell
import cash.p.terminal.modules.walletconnect.session.ui.TitleValueCell
import cash.p.terminal.modules.walletconnect.request.WCRequestPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback

class WCSessionPage(val input: WCSessionModule.Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = viewModel<WCSessionViewModel>(
            factory = WCSessionModule.Factory(input?.sessionTopic)
        )

        WCSessionScreen(
            navigation,
            viewModel,
        )
        WCSessionEventsEffect(navigation, viewModel)
    }

}

@Composable
private fun WCSessionEventsEffect(navigation: HSNavigation, viewModel: WCSessionViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val closeObserver = Observer<Unit> {
            navigation.navigateUpSafely()
        }
        val errorObserver = Observer<String?> { error ->
            HudHelper.showErrorMessage(view, error ?: view.context.getString(R.string.Error))
        }
        val noInternetObserver = Observer<Unit> {
            HudHelper.showErrorMessage(view, view.context.getString(R.string.Hud_Text_NoInternet))
        }
        viewModel.closeLiveEvent.observe(lifecycleOwner, closeObserver)
        viewModel.showErrorLiveEvent.observe(lifecycleOwner, errorObserver)
        viewModel.showNoInternetErrorLiveEvent.observe(lifecycleOwner, noInternetObserver)
        onDispose {
            viewModel.closeLiveEvent.removeObservers(lifecycleOwner)
            viewModel.showErrorLiveEvent.removeObservers(lifecycleOwner)
            viewModel.showNoInternetErrorLiveEvent.removeObservers(lifecycleOwner)
        }
    }
}

@Composable
fun WCSessionScreen(
    navigation: HSNavigation,
    viewModel: WCSessionViewModel,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val uiState = viewModel.uiState

    Column(
        modifier = Modifier
            .windowInsetsPadding(windowInsets)
            .background(color = ComposeAppTheme.colors.tyler)
    ) {
        AppBar(
            title = stringResource(R.string.WalletConnect_Title),
            showSpinner = uiState.connecting,
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(R.string.Button_Close),
                    icon = R.drawable.ic_close_24,
                    onClick = { navigation.navigateUpSafely() },
                    enabled = uiState.closeEnabled,
                    tint = if (uiState.closeEnabled) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.grey50
                )
            )
        )
        WCSessionListContent(navigation, viewModel)
    }
}

@Composable
private fun ColumnScope.WCSessionListContent(
    navigation: HSNavigation,
    viewModel: WCSessionViewModel
) {
    val uiState = viewModel.uiState

    val view = LocalView.current
    uiState.showError?.let { HudHelper.showErrorMessage(view, it) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .weight(1f)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                top = 12.dp,
                start = 24.dp,
                end = 24.dp,
                bottom = 24.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(15.dp)),
                painter = rememberAsyncImagePainterWithFallback(
                    model = uiState.peerMeta?.icon,
                    error = painterResource(R.drawable.ic_platform_placeholder_24)
                ),
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = uiState.peerMeta?.name ?: "",
                style = ComposeAppTheme.typography.headline1,
                color = ComposeAppTheme.colors.leah
            )
        }
        val composableItems = mutableListOf<@Composable () -> Unit>().apply {
            add { StatusCell(uiState.status) }
            add {
                val url = uiState.peerMeta?.url?.let { TextHelper.getCleanedUrl(it) } ?: ""
                TitleValueCell(stringResource(R.string.WalletConnect_Url), url)
            }
            add {
                TitleValueCell(
                    stringResource(R.string.WalletConnect_ActiveWallet),
                    uiState.peerMeta?.accountName ?: ""
                )
            }
        }

        val pendingRequests = uiState.pendingRequests
        if (pendingRequests.isNotEmpty()) {
            HeaderText(text = stringResource(R.string.WalletConnect_PendingRequests))
            CellUniversalLawrenceSection(pendingRequests) { item ->
                RequestCell(viewItem = item) {
                    viewModel.setRequestToOpen(item.request)
                    navigation.slideFromBottom(WCRequestPage())
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        CellUniversalLawrenceSection(
            composableItems
        )
        uiState.hint?.let {
            Spacer(Modifier.height(12.dp))
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = it
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    uiState.buttonStates?.let { ActionButtons(viewModel, it) }
}

@Composable
private fun ActionButtons(
    viewModel: WCSessionViewModel,
    buttonsStates: WCSessionButtonStates
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        if (buttonsStates.connect.visible) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Connect),
                enabled = buttonsStates.connect.enabled,
                onClick = { viewModel.connect() }
            )
        }
        if (buttonsStates.disconnect.visible) {
            Spacer(Modifier.height(16.dp))
            ButtonPrimaryRed(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Disconnect),
                enabled = buttonsStates.disconnect.enabled,
                onClick = { viewModel.disconnect() }
            )

        }
        if (buttonsStates.cancel.visible) {
            Spacer(Modifier.height(16.dp))
            ButtonPrimaryDefault(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Cancel),
                onClick = { viewModel.rejectProposal() }
            )
        }
        if (buttonsStates.remove.visible) {
            Spacer(Modifier.height(16.dp))
            ButtonPrimaryRed(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.Button_Remove),
                onClick = { viewModel.disconnect() }
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun RequestCell(
    viewItem: WCRequestViewItem,
    onRequestClick: (WCRequestViewItem) -> Unit,
) {
    RowUniversal(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = { onRequestClick.invoke(viewItem) }
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = viewItem.title,
                style = ComposeAppTheme.typography.body,
                color = ComposeAppTheme.colors.leah,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = viewItem.subtitle,
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.grey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Image(
            modifier = Modifier.padding(start = 5.dp),
            painter = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = null,
        )
    }
}
