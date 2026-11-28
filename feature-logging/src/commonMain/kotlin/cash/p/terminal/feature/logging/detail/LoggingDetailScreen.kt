package cash.p.terminal.feature.logging.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.feature.logging.components.DeleteLogsConfirmationDialog
import cash.p.terminal.feature.logging.history.LoginRecordViewItem
import cash.p.terminal.feature.logging.photo.LoginPhoto
import cash.p.terminal.feature.logging.photo.LoginPhotoImage
import cash.p.terminal.feature.logging.photo.PhotoSaveResult
import cash.p.terminal.feature.logging.photo.ProvideLoginPhotoImageLoader
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Delete
import cash.p.terminal.resources.auth_detail_save_failed
import cash.p.terminal.resources.auth_detail_save_to_gallery
import cash.p.terminal.resources.auth_detail_status_label
import cash.p.terminal.resources.auth_detail_time_label
import cash.p.terminal.resources.auth_info_duress_mode
import cash.p.terminal.resources.auth_info_entrance_to
import cash.p.terminal.resources.auth_info_no_records
import cash.p.terminal.resources.auth_info_successful
import cash.p.terminal.resources.auth_info_unsuccessful
import cash.p.terminal.resources.authorization_information
import cash.p.terminal.resources.ic_download_24
import cash.p.terminal.resources.ic_duress_log
import cash.p.terminal.resources.ic_no_image
import cash.p.terminal.resources.image_saved_to_gallery
import cash.p.terminal.resources.login_logging_delete_record_confirmation
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.captionSB_grey
import cash.p.terminal.ui_compose.components.captionSB_leah
import cash.p.terminal.ui_compose.components.captionSB_lucian
import cash.p.terminal.ui_compose.components.captionSB_remus
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoggingDetailScreen(
    viewModel: LoggingDetailViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hud = rememberHudMessenger()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.closeScreen) {
        if (uiState.closeScreen) {
            onClose()
        }
    }

    ProvideLoginPhotoImageLoader {
        AuthorizationDetailContent(
            uiState = uiState,
            onItemSelected = viewModel::selectItem,
            onDeleteClick = viewModel::deleteCurrentRecord,
            onDownloadClick = { photo ->
                scope.launch {
                    when (viewModel.savePhoto(photo)) {
                        PhotoSaveResult.SAVED -> hud.showSuccess(getString(Res.string.image_saved_to_gallery))
                        PhotoSaveResult.FAILED -> hud.showError(getString(Res.string.auth_detail_save_failed))
                        PhotoSaveResult.CANCELLED -> Unit
                    }
                }
            },
            onClose = onClose
        )
    }
}

private val ThumbnailSizeSelected: Dp = 45.dp
private val ThumbnailSizeDefault: Dp = 39.dp
private val ThumbnailListContentPadding: Dp = 16.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuthorizationDetailContent(
    uiState: LoggingDetailUiState,
    onItemSelected: (Long) -> Unit,
    onDeleteClick: () -> Unit,
    onDownloadClick: (LoginPhoto) -> Unit,
    onClose: () -> Unit
) {
    val records = uiState.records
    val selectedId = uiState.selectedId
    val currentRecord = uiState.currentRecord
    val density = LocalDensity.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Compute index from ID for pager operations
    val selectedIndex = records.indexOfFirst { it.id == selectedId }
        .takeIf { it >= 0 } ?: 0

    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { records.size }
    )

    val thumbnailListState = rememberLazyListState()

    // Sync pager -> selection: use settledPage to only react when page is fully settled
    // Drop first emission to avoid overwriting initial selection when records load
    LaunchedEffect(pagerState, records) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest { pageIndex ->
                if (pageIndex in records.indices) {
                    val idAtPage = records[pageIndex].id
                    if (idAtPage != selectedId) {
                        onItemSelected(idAtPage)
                    }
                }
            }
    }

    // Sync selection -> pager and thumbnail list
    LaunchedEffect(selectedId, records, density) {
        val targetIndex = records.indexOfFirst { it.id == selectedId }
            .takeIf { it >= 0 } ?: 0

        // Only scroll pager if not already at target (prevents feedback loop)
        if (pagerState.settledPage != targetIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetIndex)
        }

        // Scroll thumbnail list to center selected item
        if (records.isNotEmpty()) {
            val viewportWidth = thumbnailListState.layoutInfo.viewportSize.width
            val itemWidthPx = with(density) { ThumbnailSizeSelected.toPx() }
            val contentPaddingPx = with(density) { ThumbnailListContentPadding.toPx() }
            // With scrollOffset=0, item left edge is at contentPadding from viewport left
            // To center: item left edge should be at (viewportWidth - itemWidth) / 2
            val centerOffset = (contentPaddingPx - (viewportWidth - itemWidthPx) / 2).toInt()

            thumbnailListState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = centerOffset
            )
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.authorization_information),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    subhead2_grey(text = stringResource(Res.string.auth_info_no_records))
                }
            } else {
                // Main Image Pager
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val record = records.getOrNull(page)
                        MainImageView(
                            photo = record?.photo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Download FAB
                    currentRecord?.photo?.let { photo ->
                        FloatingActionButton(
                            onClick = { onDownloadClick(photo) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = ComposeAppTheme.colors.textPrimary,
                            shape = CircleShape
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_download_24),
                                contentDescription = stringResource(Res.string.auth_detail_save_to_gallery),
                                tint = ComposeAppTheme.colors.contentInverse
                            )
                        }
                    }
                }

                VSpacer(16.dp)

                // Thumbnail Gallery
                LazyRow(
                    state = thumbnailListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ThumbnailSizeSelected),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = ThumbnailListContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(records, key = { item -> item.id }) { record ->
                        ThumbnailItem(
                            modifier = Modifier.animateItem(),
                            record = record,
                            isSelected = record.id == selectedId,
                            onClick = { onItemSelected(record.id) }
                        )
                    }
                }

                VSpacer(16.dp)

                // Metadata Section
                currentRecord?.let { record ->
                    MetadataSection(record = record)
                }

                VSpacer(39.dp)

                // Delete Button
                ButtonPrimaryRed(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    title = stringResource(Res.string.Button_Delete),
                    onClick = { showDeleteConfirmation = true }
                )

                VSpacer(32.dp)
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        DeleteLogsConfirmationDialog(
            text = stringResource(Res.string.login_logging_delete_record_confirmation),
            onConfirm = {
                onDeleteClick()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

@Composable
private fun MainImageView(
    photo: LoginPhoto?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(ComposeAppTheme.colors.surfacePlaceholder),
        contentAlignment = Alignment.Center
    ) {
        LoginPhotoImage(
            photo = photo,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            placeholder = {
                Image(
                    painter = painterResource(Res.drawable.ic_no_image),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Composable
private fun ThumbnailItem(
    record: LoginRecordViewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = if (isSelected) ThumbnailSizeSelected else ThumbnailSizeDefault
    val statusColor = if (record.isSuccessful) {
        ComposeAppTheme.colors.statusSuccess
    } else {
        ComposeAppTheme.colors.statusError
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
    ) {
        LoginPhotoImage(
            photo = record.photo,
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeAppTheme.colors.surfacePlaceholder),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_no_image),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        )

        // Status color bar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .background(statusColor)
        )

        // Duress mode icon overlay
        if (record.isDuressMode) {
            Icon(
                painter = painterResource(Res.drawable.ic_duress_log),
                contentDescription = null,
                modifier = Modifier
                    .padding(1.dp)
                    .size(10.dp)
                    .background(ComposeAppTheme.colors.surfacePrimary, shape = CircleShape)
                    .align(Alignment.TopEnd),
                tint = ComposeAppTheme.colors.brandDefault
            )
        }
    }
}

@Composable
private fun MetadataSection(record: LoginRecordViewItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Title: "Entrance to the [wallet]"
        headline1_leah(
            text = record.walletName?.let { walletName ->
                stringResource(
                    Res.string.auth_info_entrance_to,
                    walletName
                )
            }.orEmpty()
        )

        VSpacer(32.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                captionSB_grey(stringResource(Res.string.auth_detail_status_label))
                captionSB_grey(stringResource(Res.string.auth_detail_time_label))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val statusText = if (record.isSuccessful) {
                    stringResource(Res.string.auth_info_successful)
                } else {
                    stringResource(Res.string.auth_info_unsuccessful)
                }
                val duressText = if (record.isDuressMode) {
                    " ${stringResource(Res.string.auth_info_duress_mode)}"
                } else {
                    ""
                }
                if (record.isSuccessful) {
                    captionSB_remus(statusText + duressText)
                } else {
                    captionSB_lucian(statusText + duressText)
                }

                captionSB_leah("${record.formattedTime} ${record.relativeTime}")
            }
        }
    }
}

@Preview
@Composable
private fun AuthorizationDetailContentPreview() {
    val previewRecords = listOf(
        LoginRecordViewItem(
            id = 1,
            photo = null,
            isSuccessful = true,
            isDuressMode = false,
            walletName = "Wallet 1",
            formattedTime = "11:15 13.08.2025",
            relativeTime = "(25 minutes ago)"
        ),
        LoginRecordViewItem(
            id = 2,
            photo = null,
            isSuccessful = false,
            isDuressMode = true,
            walletName = "Wallet 1",
            formattedTime = "09:15 13.08.2025",
            relativeTime = "(2 hours ago)"
        ),
        LoginRecordViewItem(
            id = 3,
            photo = null,
            isSuccessful = true,
            isDuressMode = true,
            walletName = "Main Wallet",
            formattedTime = "11:15 12.08.2025",
            relativeTime = "(1 day ago)"
        ),
        LoginRecordViewItem(
            id = 4,
            photo = null,
            isSuccessful = false,
            isDuressMode = false,
            walletName = null,
            formattedTime = "07:30 12.08.2025",
            relativeTime = "(1 day ago)"
        )
    )

    ComposeAppTheme {
        AuthorizationDetailContent(
            uiState = LoggingDetailUiState(
                records = previewRecords,
                selectedId = 1L  // Use actual ID from previewRecords
            ),
            onItemSelected = {},
            onDeleteClick = {},
            onDownloadClick = {},
            onClose = {}
        )
    }
}
