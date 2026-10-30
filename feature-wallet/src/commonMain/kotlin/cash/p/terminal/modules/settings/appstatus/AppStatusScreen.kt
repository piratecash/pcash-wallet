package cash.p.terminal.modules.settings.appstatus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.modules.blockchainstatus.StatusReportExporter
import cash.p.terminal.modules.blockchainstatus.StatusSectionBlock
import cash.p.terminal.modules.blockchainstatus.logSection
import cash.p.terminal.modules.settings.appstatus.AppStatusModule.BlockContent
import cash.p.terminal.resources.Button_Copy
import cash.p.terminal.resources.Button_Share
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_AppStatus
import cash.p.terminal.resources.blockchain_status_file_saved
import cash.p.terminal.resources.blockchain_status_save_to_file
import cash.p.terminal.resources.error_cannot_create_log_file
import cash.p.terminal.resources.ic_download_20
import cash.p.terminal.resources.settings_app_cache_title
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaultWithIcon
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun AppStatusScreen(
    viewModel: AppStatusViewModel,
    onBack: () -> Unit,
    onCacheClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val hudMessenger = rememberHudMessenger()
    val coroutineScope = rememberCoroutineScope()
    val exporter: StatusReportExporter = koinInject()
    val shareReport = exporter.rememberShareReport()
    val fileSaved = stringResource(Res.string.blockchain_status_file_saved)
    val cannotCreateFile = stringResource(Res.string.error_cannot_create_log_file)
    val copied = stringResource(Res.string.Hud_Text_Copied)
    val saveReport = exporter.rememberSaveReport(
        fileName = uiState.report?.fileName,
        buildReport = { checkNotNull(uiState.report) },
    ) { saved ->
        if (saved) hudMessenger.showSuccess(fileSaved) else hudMessenger.showError(cannotCreateFile)
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_AppStatus),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { paddingValues ->
        if (uiState.loading) {
            LoadingIndicator(Modifier.padding(paddingValues))
        } else {
            AppStatusList(
                uiState = uiState,
                onCopy = {
                    uiState.appStatusAsText?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        hudMessenger.showSuccess(copied)
                    }
                },
                onShare = shareReport?.let { share ->
                    {
                        coroutineScope.launch {
                            try {
                                viewModel.share(share)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                hudMessenger.showError(cannotCreateFile)
                            }
                        }
                    }
                },
                onSave = saveReport,
                onCacheClick = onCacheClick,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = ComposeAppTheme.colors.textSecondary,
            strokeWidth = 4.dp
        )
    }
}

@Composable
private fun AppStatusList(
    uiState: AppStatusModule.UiState,
    onCopy: () -> Unit,
    onShare: (() -> Unit)?,
    onSave: () -> Unit,
    onCacheClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            ExportButtons(
                shareEnabled = !uiState.shareInProgress,
                onCopy = onCopy,
                onShare = onShare,
                onSave = onSave,
            )
        }
        item {
            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                title = stringResource(Res.string.settings_app_cache_title),
                onClick = onCacheClick
            )
        }
        items(uiState.blockViewItems) { blockData ->
            StatusBlock(
                sectionTitle = blockData.title,
                contentItems = blockData.content,
            )
        }
        logSection(uiState.logGroups)
        if (uiState.blockchainStatusSections.isNotEmpty()) {
            item { InfoText(text = "BLOCKCHAIN STATUS") }
            items(uiState.blockchainStatusSections) { section ->
                StatusSectionBlock(section)
            }
        }
        item {
            VSpacer(32.dp)
        }
    }
}

/** Share where the platform has a share sheet ([onShare] is not `null`), otherwise save to a file. */
@Composable
private fun ExportButtons(
    shareEnabled: Boolean,
    onCopy: () -> Unit,
    onShare: (() -> Unit)?,
    onSave: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
        ) {
            ButtonPrimaryYellow(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.Button_Copy),
                onClick = onCopy
            )
            if (onShare != null) {
                HSpacer(8.dp)
                ButtonPrimaryDefault(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.Button_Share),
                    enabled = shareEnabled,
                    onClick = onShare
                )
            }
        }
        if (onShare == null) {
            ButtonPrimaryDefaultWithIcon(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                icon = painterResource(Res.drawable.ic_download_20),
                title = stringResource(Res.string.blockchain_status_save_to_file),
                onClick = onSave
            )
        }
    }
}

@Composable
private fun StatusBlock(
    sectionTitle: String?,
    contentItems: List<BlockContent>,
) {
    VSpacer(12.dp)
    sectionTitle?.let {
        InfoText(text = it.uppercase())
    }
    CellUniversalLawrenceSection(contentItems) { item ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            when (item) {
                is BlockContent.TitleValue -> {
                    subhead2_grey(
                        modifier = Modifier.weight(1f),
                        text = item.title,
                        maxLines = 1,
                    )
                    subhead1_leah(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        text = item.value,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
    VSpacer(12.dp)
}
