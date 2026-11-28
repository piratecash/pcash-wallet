package cash.p.terminal.feature.logging.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cash.p.terminal.feature.logging.components.DeleteLogsConfirmationDialog
import cash.p.terminal.feature.logging.photo.LoginPhoto
import cash.p.terminal.feature.logging.photo.LoginPhotoImage
import cash.p.terminal.feature.logging.photo.ProvideLoginPhotoImageLoader
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.auth_info_duress_mode
import cash.p.terminal.resources.auth_info_entrance_to
import cash.p.terminal.resources.auth_info_no_records
import cash.p.terminal.resources.auth_info_successful
import cash.p.terminal.resources.auth_info_unsuccessful
import cash.p.terminal.resources.authorization_information
import cash.p.terminal.resources.delete_all
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_no_image
import cash.p.terminal.resources.ic_user_24
import cash.p.terminal.resources.login_logging_delete_record_confirmation
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoggingListScreen(
    loginRecords: LazyPagingItems<LoginRecordViewItem>,
    onDeleteAllClick: () -> Unit,
    onDeleteClick: (Long) -> Unit,
    onItemClick: (Long) -> Unit,
    onClose: () -> Unit
) {
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var deleteRecordId by remember { mutableStateOf<Long?>(null) }
    var revealedCardId by remember { mutableStateOf<Long?>(null) }

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
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (loginRecords.itemCount == 0) {
                EmptyState()
            } else {
                ProvideLoginPhotoImageLoader {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            bottom = 116.dp
                        )
                    ) {
                        items(
                            count = loginRecords.itemCount,
                            key = { loginRecords.peek(it)?.id ?: it }
                        ) { index ->
                            loginRecords[index]?.let { item ->
                                SwipableLoginRecordItem(
                                    modifier = Modifier.animateItem(),
                                    item = item,
                                    revealed = revealedCardId == item.id,
                                    onReveal = { id -> revealedCardId = id },
                                    onConceal = { revealedCardId = null },
                                    onDelete = { deleteRecordId = item.id },
                                    onClick = { onItemClick(item.id) }
                                )
                            }
                        }
                    }
                }

                ButtonPrimaryRed(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    title = stringResource(Res.string.delete_all),
                    onClick = {
                        showDeleteAllConfirmation = true
                    }
                )
            }
        }
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllConfirmation) {
        DeleteLogsConfirmationDialog(
            onConfirm = {
                onDeleteAllClick()
                showDeleteAllConfirmation = false
            },
            onDismiss = { showDeleteAllConfirmation = false }
        )
    }

    // Delete Single Record Confirmation Dialog
    deleteRecordId?.let { recordId ->
        DeleteLogsConfirmationDialog(
            text = stringResource(Res.string.login_logging_delete_record_confirmation),
            onConfirm = {
                onDeleteClick(recordId)
                deleteRecordId = null
                revealedCardId = null
            },
            onDismiss = { deleteRecordId = null }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = ComposeAppTheme.colors.surfacePlaceholder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(Res.drawable.ic_user_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
        VSpacer(32.dp)
        subhead2_grey(
            text = stringResource(Res.string.auth_info_no_records),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SwipableLoginRecordItem(
    item: LoginRecordViewItem,
    revealed: Boolean,
    onReveal: (Long) -> Unit,
    onConceal: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        HsIconButton(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .width(88.dp),
            onClick = onDelete,
            content = {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(Res.drawable.ic_delete_20),
                    tint = ComposeAppTheme.colors.statusError,
                    contentDescription = "delete",
                )
            }
        )
        DraggableCardSimple(
            key = item.id,
            isRevealed = revealed,
            cardOffset = 88f,
            onReveal = { onReveal(item.id) },
            onConceal = onConceal,
            content = {
                LoginRecordItemContent(
                    item = item,
                    onClick = onClick
                )
            }
        )
    }
}

@Composable
private fun LoginRecordItemContent(
    item: LoginRecordViewItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Photo/placeholder
        LoginRecordPhoto(
            photo = item.photo,
            modifier = Modifier.size(60.dp)
        )

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            // Status with different colors for status and duress mode
            val statusColor = if (item.isSuccessful) {
                ComposeAppTheme.colors.statusSuccess
            } else {
                ComposeAppTheme.colors.statusError
            }
            val statusText = if (item.isSuccessful) {
                stringResource(Res.string.auth_info_successful)
            } else {
                stringResource(Res.string.auth_info_unsuccessful)
            }
            val duressText = stringResource(Res.string.auth_info_duress_mode)

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = statusColor)) {
                        append(statusText)
                    }
                    if (item.isDuressMode) {
                        withStyle(SpanStyle(color = ComposeAppTheme.colors.textSecondary)) {
                            append(" ")
                            append(duressText)
                        }
                    }
                },
                style = ComposeAppTheme.typography.body
            )

            // Wallet name - only show if known
            item.walletName?.let { name ->
                VSpacer(4.dp)
                body_leah(
                    text = stringResource(Res.string.auth_info_entrance_to, name)
                )
            }

            VSpacer(4.dp)

            // Timestamp
            subhead2_grey(
                text = "${item.formattedTime} ${item.relativeTime}"
            )
        }
    }
}

@Composable
private fun LoginRecordPhoto(
    photo: LoginPhoto?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        LoginPhotoImage(
            photo = photo,
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Image(
                    painter = painterResource(Res.drawable.ic_no_image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Preview
@Composable
private fun LoggingListScreenPreview() {
    val previewItems = listOf(
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
            isSuccessful = false,
            isDuressMode = false,
            walletName = null,
            formattedTime = "08:00 13.08.2025",
            relativeTime = "(3 hours ago)"
        )
    )

    ComposeAppTheme {
        LoggingListScreen(
            loginRecords = flowOf(PagingData.from(previewItems)).collectAsLazyPagingItems(),
            onDeleteAllClick = {},
            onDeleteClick = {},
            onItemClick = {},
            onClose = {}
        )
    }
}
