package cash.p.terminal.modules.send.offline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import cash.p.terminal.resources.ic_arrow_big_up_20
import cash.p.terminal.resources.offline_transaction_raw_hex

// Shared building blocks for the offline sign/broadcast summary sections: a section caption
// and a title/value row. Kept in one place so both flows render identical cells.
@Composable
fun SectionCaption(titleResId: StringResource, modifier: Modifier = Modifier) {
    caption_grey(
        modifier = modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        text = stringResource(titleResId),
    )
}

@Composable
fun OfflineSummaryCell(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    secondaryValue: String? = null,
    valueMaxLines: Int = 1,
    borderTop: Boolean = true,
) = Box(modifier) {
    QuoteInfoRow(
        borderTop = borderTop,
        title = {
            subhead2_grey(text = title)
        },
        value = {
            Column(horizontalAlignment = Alignment.End) {
                subhead2_leah(
                    text = value,
                    textAlign = TextAlign.End,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                secondaryValue?.let {
                    VSpacer(height = 1.dp)
                    subhead2_grey(
                        text = it,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

// Collapsible panel that reveals the full raw transaction hex on demand. Shared by the transfer
// and broadcast flows so both expose the hex with the same affordance.
@Composable
fun RawTransactionSection(rawHex: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
            .animateContentSize(),
    ) {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { expanded = !expanded },
        ) {
            subhead2_grey(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.offline_transaction_raw_hex),
            )
            Icon(
                modifier = Modifier.rotate(if (expanded) 0f else 180f),
                painter = painterResource(Res.drawable.ic_arrow_big_up_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary,
            )
        }
        if (expanded) {
            SelectionContainer {
                body_leah(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    text = rawHex
                )
            }
        }
    }
}

// Centered circular status icon with a title and free-form content slot. Shared by the offline
// sync-error and broadcast-result screens so terminal states look the same across flows.
enum class OfflineStatusBlockStyle { Success, Error, Neutral }

@Composable
fun OfflineStatusBlock(
    icon: DrawableResource,
    iconTint: Color,
    style: OfflineStatusBlockStyle,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(style.backgroundColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(icon),
                tint = iconTint,
                contentDescription = null,
            )
        }
        Spacer(Modifier.height(24.dp))
        headline2_leah(
            text = title,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        content()
    }
}

@Composable
private fun OfflineStatusBlockStyle.backgroundColor(): Color =
    when (this) {
        OfflineStatusBlockStyle.Success -> ComposeAppTheme.colors.statusSuccess.copy(alpha = 0.1f)
        OfflineStatusBlockStyle.Error -> ComposeAppTheme.colors.statusError.copy(alpha = 0.1f)
        OfflineStatusBlockStyle.Neutral -> ComposeAppTheme.colors.surfacePrimary
    }
