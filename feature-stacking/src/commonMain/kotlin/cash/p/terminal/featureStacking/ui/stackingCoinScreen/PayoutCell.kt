package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.featureStacking.ui.entities.PayoutViewItem
import cash.p.terminal.network.pirate.domain.enity.PayoutType
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.accruals
import cash.p.terminal.resources.ic_accruals
import cash.p.terminal.resources.ic_payouts
import cash.p.terminal.resources.payouts
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.entities.SectionItemPosition
import cash.p.terminal.ui_compose.sectionItemBorder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PayoutCell(item: PayoutViewItem, position: SectionItemPosition) {
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

            else -> Modifier
        }

        val borderModifier = if (position != SectionItemPosition.Single) {
            Modifier.sectionItemBorder(1.dp, ComposeAppTheme.colors.borderDefault, 12.dp, position)
        } else {
            Modifier.border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(12.dp))
        }

        RowUniversal(
            modifier = Modifier
                .fillMaxSize()
                .then(clipModifier)
                .then(borderModifier)
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (item.payoutType == PayoutType.INCOME) {
                    Res.drawable.ic_accruals
                } else {
                    Res.drawable.ic_payouts
                }
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(icon),
                    tint = ComposeAppTheme.colors.iconPrimary,
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier
                    .padding(end = 16.dp)
            ) {
                val titleRes = if (item.payoutType == PayoutType.INCOME) {
                    Res.string.accruals
                } else {
                    Res.string.payouts
                }
                val color = if (item.payoutType == PayoutType.INCOME) {
                    ComposeAppTheme.colors.textPrimary
                } else {
                    ComposeAppTheme.colors.statusSuccess
                }
                Row {
                    Text(
                        text = stringResource(titleRes),
                        style = ComposeAppTheme.typography.body,
                        color = color,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = item.amount.toPlainString(),
                        style = ComposeAppTheme.typography.body,
                        color = color,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(1.dp))
                Row {
                    subhead2_grey(
                        text = item.time,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        maxLines = 1,
                    )
                    subhead2_grey(
                        text = item.amountSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
