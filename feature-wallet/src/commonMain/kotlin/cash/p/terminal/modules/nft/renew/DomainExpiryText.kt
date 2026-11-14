package cash.p.terminal.modules.nft.renew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import cash.p.terminal.core.nft.DomainExpiryLevel
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.daysLeft
import cash.p.terminal.core.nft.domainExpiryLevel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_domain_expires_in
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.pluralStringResource

private const val MILLIS_PER_SECOND = 1000L

@Composable
internal fun NftDomainExpiry.rememberDaysLeft(): Int {
    var nowSeconds by remember { mutableLongStateOf(currentSeconds()) }
    // Not PageResumeEffect: it needs LocalHostLifecycleOwner, which previews lack; an extra refresh is harmless.
    LifecycleResumeEffect(Unit) {
        nowSeconds = currentSeconds()
        onPauseOrDispose {}
    }
    return daysLeft(nowSeconds)
}

private fun currentSeconds(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND

@Composable
internal fun daysLeftText(daysLeft: Int): String =
    pluralStringResource(Res.plurals.nft_domain_expires_in, daysLeft, daysLeft)

@Composable
internal fun DomainExpiryLevel.color(): Color = when (this) {
    DomainExpiryLevel.Normal -> ComposeAppTheme.colors.textPrimary
    DomainExpiryLevel.Soon -> ComposeAppTheme.colors.statusWarning
    DomainExpiryLevel.Urgent -> ComposeAppTheme.colors.statusError
}

/** Title on the left, "in N days" in the tier color on the right. */
@Composable
internal fun DomainExpiryRow(
    title: String,
    daysLeft: Int,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
) {
    RowUniversal(modifier = modifier.padding(horizontal = 16.dp), minHeight = minHeight) {
        subhead2_grey(text = title)
        Spacer(Modifier.width(16.dp))
        Spacer(Modifier.weight(1f))
        Text(
            text = daysLeftText(daysLeft),
            style = ComposeAppTheme.typography.subhead1,
            color = domainExpiryLevel(daysLeft).color(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun DomainExpiryRowPreview() {
    ComposeAppTheme {
        Column {
            DomainExpiryRow(title = "Expires", daysLeft = 203)
            DomainExpiryRow(title = "Expires", daysLeft = 12)
            DomainExpiryRow(title = "Expires", daysLeft = 5)
        }
    }
}
