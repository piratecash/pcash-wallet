package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.featureStacking.ui.entities.PayoutViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.payouts_history
import cash.p.terminal.ui_compose.components.HeaderStick
import cash.p.terminal.ui_compose.entities.SectionItemPosition
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.payoutList(
    payoutItemsMap: Map<String, List<PayoutViewItem>>
) {
    if(payoutItemsMap.isEmpty()) {
        return
    }
    item {
        Text(
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
            text = stringResource(Res.string.payouts_history),
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp)
        )
    }
    payoutItemsMap.forEach { (dateHeader, transactions) ->
        item {
            HeaderStick(text = dateHeader)
        }

        val itemsCount = transactions.size
        val singleElement = itemsCount == 1

        itemsIndexed(
            items = transactions,
            key = { _, item ->
                item.id
            }
        ) { index, item ->
            val position: SectionItemPosition = when {
                singleElement -> SectionItemPosition.Single
                index == 0 -> SectionItemPosition.First
                index == itemsCount - 1 -> SectionItemPosition.Last
                else -> SectionItemPosition.Middle
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                PayoutCell(item, position)
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
