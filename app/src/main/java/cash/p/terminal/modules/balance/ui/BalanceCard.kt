package cash.p.terminal.modules.balance.ui

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.balance.BalanceViewItem2
import cash.p.terminal.modules.balance.BalanceViewModel
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.balance.SyncingProgressType
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.syncerror.showSyncErrorDialog
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.CoinIconWithSyncProgress
import cash.p.terminal.ui_compose.components.CellMultilineClear
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.diffColor
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.oneLineHeight
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.balance.DeemedValue
import java.math.BigDecimal

@Composable
fun BalanceCardSwipable(
    viewItem: BalanceViewItem2,
    revealed: Boolean,
    onReveal: (Int) -> Unit,
    onConceal: () -> Unit,
    onClick: () -> Unit,
    onBalanceClick: () -> Unit,
    onClickSyncError: () -> Unit,
    onDisable: () -> Unit,
) {

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        HsIconButton(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .width(88.dp),
            onClick = onDisable,
            content = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_circle_minus_24),
                    tint = ComposeAppTheme.colors.grey,
                    contentDescription = "delete",
                )
            }
        )
        DraggableCardSimple(
            key = viewItem.wallet,
            isRevealed = revealed,
            cardOffset = 72f,
            onReveal = { onReveal(viewItem.wallet.hashCode()) },
            onConceal = onConceal,
            enabled = viewItem.isSwipeToDeleteEnabled,
            content = {
                BalanceCard(
                    onClick = onClick,
                    onClickSyncError = onClickSyncError,
                    viewItem = viewItem,
                    onBalanceClick = onBalanceClick
                )
            }
        )
    }
}

@Composable
fun BalanceCard(
    onClick: () -> Unit,
    onClickSyncError: () -> Unit,
    onBalanceClick: (() -> Unit)? = null,
    viewItem: BalanceViewItem2
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.lawrence)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        BalanceCardInner(
            viewItem = viewItem,
            type = BalanceCardSubtitleType.Rate,
            onClickSyncError = onClickSyncError,
            onBalanceClick = onBalanceClick,
            fullWidth = true,
        )
    }
}

enum class BalanceCardSubtitleType {
    Rate, CoinName
}

@Composable
fun BalanceCardInner(
    viewItem: BalanceViewItem2,
    type: BalanceCardSubtitleType,
    onClickSyncError: (() -> Unit)? = null,
    onBalanceClick: (() -> Unit)? = null,
    fullWidth: Boolean = false,
) {
    val layout = viewItem.cardLayout(fullWidth)
    Box(modifier = if (fullWidth) Modifier else Modifier.height(layout.cardHeight)) {
        CellMultilineClear(
            height = if (fullWidth) null else layout.cardHeight,
            onBalanceClick = onBalanceClick,
        ) {
            Column(
                modifier = if (layout.fullWidth) {
                    Modifier.padding(horizontal = 16.dp, vertical = layout.verticalPadding)
                } else {
                    Modifier
                }
            ) {
                BalanceCardMainRow(viewItem, type, onClickSyncError, layout)
                viewItem.stackingUnpaid?.let { stackingUnpaid ->
                    if (layout.fullWidth) {
                        Spacer(Modifier.height(17.dp))
                        HorizontalDivider(thickness = 1.dp, color = ComposeAppTheme.colors.divider)
                        Spacer(Modifier.height(5.dp))
                    } else {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = ComposeAppTheme.colors.steel10,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    StakingUnpaidRow(stackingUnpaid, layout.fullWidth)
                }
            }
        }
        if (fullWidth) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                thickness = 1.dp,
                color = ComposeAppTheme.colors.divider,
            )
        }
    }
}

@Composable
private fun BalanceCardMainRow(
    viewItem: BalanceViewItem2,
    type: BalanceCardSubtitleType,
    onClickSyncError: (() -> Unit)?,
    layout: BalanceCardLayout,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (layout.fullWidth) {
            Modifier.heightIn(min = layout.mainBlockHeight)
        } else {
            Modifier.height(layout.cardHeight - layout.stackingBlockHeight)
        }
    ) {
        WalletIcon(viewItem, onClickSyncError, compact = layout.fullWidth)
        if (layout.fullWidth) Spacer(Modifier.width(16.dp))
        Column(
            modifier = if (layout.fullWidth) {
                Modifier.weight(1f)
            } else {
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
            },
            verticalArrangement = Arrangement.Center
        ) {
            BalanceCardPrimaryRow(viewItem, layout.fullWidth)
            Spacer(modifier = Modifier.height(if (layout.fullWidth) 1.dp else 3.dp))
            BalanceCardSecondaryRow(viewItem, type)
        }
        if (!layout.fullWidth) Spacer(modifier = Modifier.width(16.dp))
    }
}

@Composable
private fun BalanceCardPrimaryRow(viewItem: BalanceViewItem2, fullWidth: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            body_leah(
                text = viewItem.wallet.coin.code,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!viewItem.badge.isNullOrBlank()) {
                Badge(
                    modifier = Modifier.padding(start = if (fullWidth) 8.dp else 6.dp),
                    text = viewItem.badge,
                )
            }
            if (viewItem.offline) {
                Badge(
                    modifier = Modifier.padding(start = if (fullWidth) 8.dp else 6.dp),
                    text = stringResource(R.string.offline_mode_badge),
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        headline2_leah(
            text = viewItem.primaryValue.visibleValue(),
            dimmed = viewItem.primaryValue.dimmed,
            maxLines = 1,
            textAlign = TextAlign.End,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BalanceCardSecondaryRow(
    viewItem: BalanceViewItem2,
    type: BalanceCardSubtitleType,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            BalanceCardSubtitle(viewItem, type)
        }
        Box(modifier = Modifier.padding(start = 16.dp)) {
            if (viewItem.syncedUntilTextValue != null) {
                subhead2_grey(text = viewItem.syncedUntilTextValue, maxLines = 1)
            } else {
                subhead2_grey(
                    text = viewItem.secondaryValue.visibleValue(),
                    dimmed = viewItem.secondaryValue.dimmed,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BalanceCardSubtitle(
    viewItem: BalanceViewItem2,
    type: BalanceCardSubtitleType,
) {
    viewItem.syncingTextValue?.let {
        subhead2_grey(text = it, maxLines = 1)
        return
    }
    when (type) {
        BalanceCardSubtitleType.Rate -> if (viewItem.exchangeValue.visible) {
            Column {
                subhead2_grey(
                    text = viewItem.exchangeValue.value,
                    dimmed = viewItem.exchangeValue.dimmed,
                    modifier = Modifier.oneLineHeight(ComposeAppTheme.typography.subhead2),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                if (viewItem.displayDiffOptionType != DisplayDiffOptionType.NONE) {
                    subhead2(
                        text = viewItem.fullDiff,
                        color = diffColor(viewItem.diff),
                        modifier = Modifier.oneLineHeight(ComposeAppTheme.typography.subhead2),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }

        BalanceCardSubtitleType.CoinName -> subhead2_grey(text = viewItem.wallet.coin.name)
    }
}

@Composable
private fun StakingUnpaidRow(stackingUnpaid: DeemedValue<String>, fullWidth: Boolean) {
    Row(
        modifier = if (fullWidth) {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        } else {
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fullWidth) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                subhead2_grey(
                    text = stringResource(R.string.staking_unpaid),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    modifier = Modifier.size(15.dp),
                    painter = painterResource(R.drawable.ic_info_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.textSecondary,
                )
            }
        } else {
            subhead2_grey(
                text = stringResource(R.string.staking_unpaid),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        subhead2_grey(
            text = stackingUnpaid.visibleValue(),
            maxLines = 1,
        )
    }
}

private data class BalanceCardLayout(
    val fullWidth: Boolean,
    val verticalPadding: Dp,
    val mainBlockHeight: Dp,
    val stackingBlockHeight: Dp,
    val cardHeight: Dp,
)

private fun BalanceViewItem2.cardLayout(fullWidth: Boolean): BalanceCardLayout {
    val hasDiff = displayDiffOptionType != DisplayDiffOptionType.NONE
    val verticalPadding = if (hasDiff) 12.dp else 16.dp
    val mainBlockHeight = if (hasDiff) 61.dp else 40.dp
    val stackingBlockHeight = if (stackingUnpaid == null) 0.dp else 46.dp
    return BalanceCardLayout(
        fullWidth = fullWidth,
        verticalPadding = verticalPadding,
        mainBlockHeight = mainBlockHeight,
        stackingBlockHeight = stackingBlockHeight,
        cardHeight = mainBlockHeight + stackingBlockHeight + verticalPadding + verticalPadding,
    )
}

private fun DeemedValue<String>.visibleValue(): String = if (visible) value else "*****"

@Composable
private fun WalletIcon(
    viewItem: BalanceViewItem2,
    onClickSyncError: (() -> Unit)?,
    compact: Boolean,
) {
    val syncingProgress = viewItem.syncingProgress

    Box(
        modifier = Modifier
            .width(if (compact) 32.dp else 64.dp)
            .then(if (compact) Modifier else Modifier.fillMaxHeight()),
        contentAlignment = Alignment.Center
    ) {
        CoinIconWithSyncProgress(
            token = viewItem.wallet.token,
            syncingProgress = syncingProgress,
            failedIconVisible = viewItem.failedIconVisible,
            onClickSyncError = onClickSyncError,
            boxSize = if (compact) 32.dp else 40.dp,
            iconSize = 32.dp,
        )
    }
}

fun onSyncErrorClicked(
    viewItem: BalanceViewItem2,
    viewModel: BalanceViewModel,
    navController: NavController,
    view: View
) {
    when (val syncErrorDetails = viewModel.getSyncErrorDetails(viewItem)) {
        is BalanceViewModel.SyncError.Dialog -> {
            val wallet = syncErrorDetails.wallet
            val errorMessage = syncErrorDetails.errorMessage

            navController.showSyncErrorDialog(wallet, errorMessage)
        }

        is BalanceViewModel.SyncError.NetworkNotAvailable -> {
            HudHelper.showErrorMessage(view, R.string.Hud_Text_NoInternet)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BalanceCardSwipablePreview() {
    ComposeAppTheme {
        BalanceCard(
            onClick = {},
            onClickSyncError = {},
            viewItem = BalanceViewItem2(
                wallet = WalletFactory.previewWallet(),
                primaryValue = DeemedValue("1.2345678739847587349875938475345345435345345345", false, true),
                secondaryValue = DeemedValue("0.123456 BTC", false, true),
                exchangeValue = DeemedValue("1234.56 USD", false, true),
                fullDiff = "+5.67%",
                diff = BigDecimal("5.67"),
                displayDiffOptionType = DisplayDiffOptionType.BOTH,
                syncingProgress = SyncingProgress(
                    type = SyncingProgressType.ProgressWithRing,
                    progress = 10.0
                ),
                syncingTextValue = null,
                syncedUntilTextValue = null,
                failedIconVisible = false,
                badge = "HOT",
                stackingUnpaid = DeemedValue("12.34 BTC", false, false),
                errorMessage = null,
                isWatchAccount = false,
                isSwipeToDeleteEnabled = false
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BalanceCardOfflinePreview() {
    ComposeAppTheme {
        BalanceCard(
            onClick = {},
            onClickSyncError = {},
            viewItem = BalanceViewItem2(
                wallet = WalletFactory.previewWallet(),
                primaryValue = DeemedValue("1.23456", false, true),
                secondaryValue = DeemedValue("0.123456 BTC", false, true),
                exchangeValue = DeemedValue("1234.56 USD", false, true),
                fullDiff = "+5.67%",
                diff = BigDecimal("5.67"),
                displayDiffOptionType = DisplayDiffOptionType.BOTH,
                syncingProgress = SyncingProgress(null, null),
                syncingTextValue = null,
                syncedUntilTextValue = null,
                failedIconVisible = false,
                badge = "HOT",
                stackingUnpaid = null,
                errorMessage = null,
                isWatchAccount = false,
                isSwipeToDeleteEnabled = false,
                offline = true,
            )
        )
    }
}
