package cash.p.terminal.modules.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.modules.transactions.poison_status.PoisonStatusBadge
import cash.p.terminal.modules.transactions.poison_status.TransactionStatusesInfoSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ColumnUniversal
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HeaderStick
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.ScrollableTabs
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.placeholderPainter
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.entities.SectionItemPosition
import cash.p.terminal.ui_compose.sectionItemBorder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Transactions_Hide
import cash.p.terminal.resources.ic_arrow_return_20
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.ic_double_spend_20
import cash.p.terminal.resources.ic_eye_off
import cash.p.terminal.resources.ic_lock_20
import cash.p.terminal.resources.ic_unlock_20
import cash.p.terminal.resources.offline_signed_title
import cash.p.terminal.resources.show_all_transactions

fun LazyListScope.transactionsHiddenBlock(
    shortBlock: Boolean,
    onShowAllTransactionsClicked: () -> Unit
) {
    item {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!shortBlock) {
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
                        painter = painterResource(Res.drawable.ic_eye_off),
                        contentDescription = "transactions hidden",
                        tint = ComposeAppTheme.colors.iconSecondary
                    )
                }
                Spacer(Modifier.height(32.dp))
                subhead2_grey(
                    text = stringResource(Res.string.Transactions_Hide),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                subhead2_grey(
                    text = "*****",
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(32.dp))
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                title = stringResource(Res.string.show_all_transactions),
                onClick = onShowAllTransactionsClicked
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.transactionList(
    transactionsMap: Map<String, List<TransactionViewItem>>,
    willShow: (TransactionViewItem) -> Unit,
    isItemBalanceHidden: (TransactionViewItem) -> Boolean,
    onSensitiveValueClick: (TransactionViewItem) -> Unit,
    onClick: (TransactionViewItem) -> Unit,
    onBottomReached: () -> Unit,
    stickyDateHeaders: Boolean = true
) {
    val bottomReachedUid = getBottomReachedUid(transactionsMap)

    transactionsMap.forEach { (dateHeader, transactions) ->
        val dateHeaderContent: @Composable () -> Unit = { HeaderStick(text = dateHeader) }
        if (stickyDateHeaders) {
            stickyHeader { dateHeaderContent() }
        } else {
            item { dateHeaderContent() }
        }

        val itemsCount = transactions.size
        val singleElement = itemsCount == 1

        itemsIndexed(
            items = transactions,
            key = { _, item ->
                item.uid
            }
        ) { index, item ->
            val position: SectionItemPosition = when {
                singleElement -> SectionItemPosition.Single
                index == 0 -> SectionItemPosition.First
                index == itemsCount - 1 -> SectionItemPosition.Last
                else -> SectionItemPosition.Middle
            }

            val shouldShowAmount = !isItemBalanceHidden(item)

            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TransactionCell(
                    item = item,
                    position = position,
                    showAmount = shouldShowAmount,
                    onValueClick = {
                        onSensitiveValueClick(item)
                    },
                    onClick = {
                        onClick(item)
                    }
                )
            }

            willShow.invoke(item)

            if (item.uid == bottomReachedUid) {
                onBottomReached.invoke()
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    item {
        Spacer(modifier = Modifier.height(20.dp))
    }
}

private fun getBottomReachedUid(transactionsMap: Map<String, List<TransactionViewItem>>): String? {
    val txList = transactionsMap.values.flatten()
    //get index not exact bottom but near to the bottom, to make scroll smoother
    val index = if (txList.size > 4) txList.size - 4 else 0

    return txList.getOrNull(index)?.uid
}

@Composable
fun TransactionCell(
    item: TransactionViewItem,
    position: SectionItemPosition,
    showAmount: Boolean = item.showAmount,
    onValueClick: () -> Unit,
    onClick: () -> Unit
) {
    var showStatusesInfo by remember { mutableStateOf(false) }

    if (showStatusesInfo) {
        TransactionStatusesInfoSheet(onDismiss = { showStatusesInfo = false })
    }
    val divider = position == SectionItemPosition.Middle || position == SectionItemPosition.Last
    SectionUniversalItem(
        borderTop = divider,
    ) {
        val clipModifier = when (position) {
            SectionItemPosition.First -> {
                Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            }

            SectionItemPosition.Last -> {
                Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            }

            SectionItemPosition.Single -> {
                Modifier.clip(RoundedCornerShape(12.dp))
            }

            SectionItemPosition.Middle -> {
                Modifier.clipToBounds()
            }
        }

        val borderModifier = if (position != SectionItemPosition.Single) {
            Modifier.sectionItemBorder(1.dp, ComposeAppTheme.colors.borderDefault, 12.dp, position)
        } else {
            Modifier.border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(12.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(clipModifier)
                .then(borderModifier)
                .clickable(onClick = onClick)
        ) {
            // AML status stripe on left edge
            item.amlStatus?.let { status ->
                AmlStatusStripe(status)
            }

            // Loading indicator in top-left corner
            if (item.amlStatus == AmlStatus.Loading) {
                AmlLoadingIndicator()
            }

            val isScam = item.poisonStatus == PoisonStatus.SUSPICIOUS
            val blurModifier = if (isScam && supportsBlur) {
                Modifier.blur(3.dp)
            } else if (isScam) {
                Modifier.alpha(0.15f)
            } else {
                Modifier
            }

            ColumnUniversal(
                modifier = Modifier.fillMaxSize()
            ) {
                Row {
                    TransactionIconBox(item)
                    TransactionContentRow(
                        item = item,
                        showAmount = showAmount,
                        onValueClick = onValueClick,
                        modifier = blurModifier,
                    )
                }
                if (item.addressPoisoningViewMode == AddressPoisoningViewMode.STANDARD) {
                    VSpacer(6.dp)
                    body_leah(
                        text = if (showAmount) item.subtitle else "*****",
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(blurModifier)
                            .padding(horizontal = 12.dp),
                        maxLines = 1,
                    )
                    VSpacer(11.dp)
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = ComposeAppTheme.colors.borderDivider,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    TransactionFooter(
                        item = item,
                        showAmount = showAmount,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 5.dp),
                        onInfoClick = { showStatusesInfo = true },
                    )
                } else {
                    VSpacer(11.dp)
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = ComposeAppTheme.colors.borderDivider,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    VSpacer(6.dp)
                    TransactionFooter(
                        item = item,
                        showAmount = showAmount,
                        poisonText = if (showAmount) item.subtitle else "*****",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        onInfoClick = { showStatusesInfo = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionFooter(
    item: TransactionViewItem,
    showAmount: Boolean,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    poisonText: String? = null,
) {
    val statusValue = item.statusValue
    if (statusValue != null) {
        Text(
            text = if (showAmount) statusValue.value else "*****",
            style = ComposeAppTheme.typography.subhead2,
            color = statusValue.color.compose(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    PoisonStatusBadge(
        poisonStatus = item.poisonStatus,
        text = poisonText,
        modifier = modifier,
        onInfoClick = onInfoClick,
    )
}

@Composable
private fun TransactionIconBox(item: TransactionViewItem) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(42.dp)
            .alpha(if (item.spam) 0.5f else 1f),
        contentAlignment = Alignment.Center
    ) {
        item.progress?.let { progress ->
            HSCircularProgressIndicator(progress)
        }
        when (val icon = item.icon) {
            TransactionViewItem.Icon.Failed -> {
                Icon(
                    painter = painterResource(Res.drawable.ic_attention_24),
                    tint = ComposeAppTheme.colors.statusError,
                    contentDescription = null
                )
            }

            is TransactionViewItem.Icon.Platform -> {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = icon.iconRes?.let { painterResource(it) }
                        ?: placeholderPainter(Res.drawable.coin_placeholder),
                    tint = ComposeAppTheme.colors.iconPrimary,
                    contentDescription = null
                )
            }

            is TransactionViewItem.Icon.Regular -> {
                val shape =
                    if (icon.rectangle) RoundedCornerShape(CornerSize(4.dp)) else CircleShape
                HsImage(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(shape),
                    url = icon.url,
                    alternativeUrl = icon.alternativeUrl,
                    placeholder = icon.placeholder
                )
            }

            is TransactionViewItem.Icon.Double -> {
                val backShape =
                    if (icon.back.rectangle) RoundedCornerShape(CornerSize(4.dp)) else CircleShape
                val frontShape =
                    if (icon.front.rectangle) RoundedCornerShape(CornerSize(4.dp)) else CircleShape
                HsImage(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 4.dp, start = 6.dp)
                        .size(24.dp)
                        .clip(backShape),
                    url = icon.back.url,
                    alternativeUrl = icon.back.alternativeUrl,
                    placeholder = icon.back.placeholder,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.5.dp, end = 6.5.dp)
                        .size(24.dp)
                        .clip(frontShape)
                        .background(ComposeAppTheme.colors.backgroundBase)
                )

                HsImage(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 4.dp, end = 6.dp)
                        .size(24.dp)
                        .clip(frontShape),
                    url = icon.front.url,
                    alternativeUrl = icon.front.alternativeUrl,
                    placeholder = icon.front.placeholder,
                )
            }

            is TransactionViewItem.Icon.ImageResource -> {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(icon.resourceId),
                    tint = ComposeAppTheme.colors.iconPrimary,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun TransactionContentRow(
    item: TransactionViewItem,
    showAmount: Boolean,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(end = 16.dp)
            .alpha(if (item.spam) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            body_leah(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showAmount) item.formattedTime else "*****",
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.textSecondaryDimmed,
                maxLines = 1,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onValueClick,
                )
            )
        }
        Column(
            modifier = Modifier.wrapContentWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.primaryValue?.let { coloredValue ->
                    Text(
                        text = if (showAmount) coloredValue.value else "*****",
                        style = ComposeAppTheme.typography.body,
                        color = coloredValue.color.compose(),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onValueClick,
                        )
                    )
                }

                if (item.doubleSpend) {
                    Image(
                        modifier = Modifier.padding(start = 6.dp),
                        painter = painterResource(Res.drawable.ic_double_spend_20),
                        contentDescription = null
                    )
                }
                item.locked?.let { locked ->
                    Image(
                        modifier = Modifier.padding(start = 6.dp),
                        painter = painterResource(if (locked) Res.drawable.ic_lock_20 else Res.drawable.ic_unlock_20),
                        contentDescription = null
                    )
                }
                if (item.sentToSelf) {
                    Image(
                        modifier = Modifier.padding(start = 6.dp),
                        painter = painterResource(Res.drawable.ic_arrow_return_20),
                        contentDescription = null
                    )
                }
            }
            if (item.primaryValue != null || item.secondaryValue != null) {
                Text(
                    text = if (showAmount) item.secondaryValue?.value.orEmpty() else "*****",
                    style = ComposeAppTheme.typography.subhead2,
                    color = item.secondaryValue?.color?.compose() ?: Color.Unspecified,
                    maxLines = 1,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onValueClick,
                    )
                )
            }
        }
    }
}

@Composable
private fun AmlStatusStripe(
    status: AmlStatus
) {
    if (status == AmlStatus.Loading) return
    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                translationX = -14.dp.toPx()
                translationY = -14.dp.toPx()
                rotationZ = 45f
            }
            .clip(RoundedCornerShape(topStart = 12.dp))
            .background(status.riskColor())
    )
}

@Composable
private fun AmlLoadingIndicator() {
    Box(
        modifier = Modifier
            .padding(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(12.dp),
            color = ComposeAppTheme.colors.textSecondary,
            strokeWidth = 1.5.dp
        )
    }
}

@Composable
internal fun TransactionCellPreviewContent(item: TransactionViewItem) {
    ComposeAppTheme {
        Box(
            modifier = Modifier
                .wrapContentHeight()
                .padding(horizontal = 16.dp)
        ) {
            TransactionCell(
                item = item,
                position = SectionItemPosition.Middle,
                onValueClick = {},
                onClick = {}
            )
        }
    }
}



@Composable
fun FilterTypeTabs(
    filterTypes: List<Filter<FilterTransactionType>>,
    offlineSignedSelected: Boolean,
    onTransactionTypeClick: (FilterTransactionType) -> Unit,
    onOfflineSignedClick: (() -> Unit)? = null,
) {
    val tabItems = buildList<TabItem<TransactionsTab>> {
        filterTypes.forEach {
            add(
                TabItem(
                    title = stringResource(it.item.title),
                    selected = it.selected && !offlineSignedSelected,
                    item = TransactionsTab.Regular(it.item),
                )
            )
        }
        if (onOfflineSignedClick != null) {
            add(
                TabItem(
                    title = stringResource(Res.string.offline_signed_title),
                    selected = offlineSignedSelected,
                    item = TransactionsTab.OfflineSigned,
                )
            )
        }
    }

    ScrollableTabs(tabItems) { tab ->
        when (tab) {
            is TransactionsTab.Regular -> onTransactionTypeClick(tab.type)
            TransactionsTab.OfflineSigned -> onOfflineSignedClick?.invoke()
        }
    }
}

data class Filter<T>(val item: T, val selected: Boolean)

private sealed interface TransactionsTab {
    data class Regular(val type: FilterTransactionType) : TransactionsTab
    data object OfflineSigned : TransactionsTab
}
