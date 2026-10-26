@file:Suppress("PackageNaming")

package cash.p.terminal.modules.transactions.poison_status

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.address_poisoning_view_address_book
import cash.p.terminal.resources.address_poisoning_view_blockchain
import cash.p.terminal.resources.address_poisoning_view_created_by_wallet
import cash.p.terminal.resources.address_poisoning_view_suspicious
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_trust_address_book_20
import cash.p.terminal.resources.ic_trust_blockchain_20
import cash.p.terminal.resources.ic_trust_suspicious_20
import cash.p.terminal.resources.ic_trust_wallet_20
import cash.p.terminal.resources.transaction_status_address_book_description
import cash.p.terminal.resources.transaction_status_blockchain_description
import cash.p.terminal.resources.transaction_status_created_description
import cash.p.terminal.resources.transaction_status_suspicious_description

val PoisonStatus.iconRes: DrawableResource
    get() = when (this) {
        PoisonStatus.SUSPICIOUS -> Res.drawable.ic_trust_suspicious_20
        PoisonStatus.CREATED -> Res.drawable.ic_trust_wallet_20
        PoisonStatus.ADDRESS_BOOK -> Res.drawable.ic_trust_address_book_20
        PoisonStatus.BLOCKCHAIN -> Res.drawable.ic_trust_blockchain_20
    }

val PoisonStatus.labelRes: StringResource
    get() = when (this) {
        PoisonStatus.SUSPICIOUS -> Res.string.address_poisoning_view_suspicious
        PoisonStatus.CREATED -> Res.string.address_poisoning_view_created_by_wallet
        PoisonStatus.ADDRESS_BOOK -> Res.string.address_poisoning_view_address_book
        PoisonStatus.BLOCKCHAIN -> Res.string.address_poisoning_view_blockchain
    }

val PoisonStatus.descriptionRes: StringResource
    get() = when (this) {
        PoisonStatus.SUSPICIOUS -> Res.string.transaction_status_suspicious_description
        PoisonStatus.CREATED -> Res.string.transaction_status_created_description
        PoisonStatus.ADDRESS_BOOK -> Res.string.transaction_status_address_book_description
        PoisonStatus.BLOCKCHAIN -> Res.string.transaction_status_blockchain_description
    }

@Composable
fun PoisonStatusBadge(
    poisonStatus: PoisonStatus,
    modifier: Modifier = Modifier,
    text: String? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(poisonStatus.iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        HSpacer(6.dp)
        subhead2_grey(
            text = text ?: stringResource(poisonStatus.labelRes),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HSpacer(6.dp)
        onInfoClick?.let {
            HsIconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary,
                )
            }
        }
    }
}

@Composable
fun PoisonStatusInfoEntry(
    status: PoisonStatus,
    titleContent: @Composable (String) -> Unit,
    descriptionContent: @Composable (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(status.iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            titleContent(stringResource(status.labelRes).replaceFirstChar { it.uppercase() })
            VSpacer(4.dp)
            descriptionContent(stringResource(status.descriptionRes))
        }
    }
}

