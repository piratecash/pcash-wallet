package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.premium_title
import cash.p.terminal.resources.star_filled_yellow_16
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HeaderStick(
    borderTop: Boolean = false,
    text: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        if (borderTop) {
            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead1_grey(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = text,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun HeaderText(
    text: String,
    onInfoClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        subhead2_grey(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = text,
            maxLines = 1
        )
        onInfoClick?.let { onClick ->
            Spacer(Modifier.weight(1f))
            Icon(
                modifier = Modifier
                    .padding(end = 24.dp)
                    .size(20.dp)
                    .clickable(
                        onClick = onClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                painter = painterResource(Res.drawable.ic_info_20),
                tint = ComposeAppTheme.colors.iconSecondary,
                contentDescription = stringResource(Res.string.Info_Title),
            )
        }
    }
}

@Composable
fun HeaderSorting(
    borderTop: Boolean = false,
    borderBottom: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        if (borderTop) {
            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (borderBottom) {
            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun PremiumHeader(
    text: String = stringResource(Res.string.premium_title),
    horizontalPadding: Dp = 32.dp,
) {
    PremiumHeader(
        starPainter = painterResource(Res.drawable.star_filled_yellow_16),
        text = text,
        horizontalPadding = horizontalPadding,
    )
}

@Composable
fun SectionHeaderWithIcon(
    iconRes: DrawableResource,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(16.dp),
            painter = painterResource(iconRes),
            tint = ComposeAppTheme.colors.iconSecondary,
            contentDescription = null,
        )
        subhead1_grey(
            text = text,
            maxLines = 1,
        )
    }
}
