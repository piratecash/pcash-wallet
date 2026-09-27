package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.premium_title
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun RowUniversal(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    minHeight: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val clickableModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .then(clickableModifier)
            .then(modifier)
            .padding(vertical = verticalPadding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
fun CellUniversalLawrenceSection(content: @Composable () -> Unit) {
    CellUniversalLawrenceSection(listOf(content))
}

@Composable
fun CellUniversalLawrenceSection(
    composableItems: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence),
    ) {
        composableItems.forEachIndexed { index, composable ->
            SectionUniversalItem(borderTop = index != 0, content = composable)
        }
    }
}

@Composable
fun SectionUniversalItem(
    borderTop: Boolean = false,
    borderBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (borderTop) {
            HsDivider(
                modifier = Modifier.align(Alignment.TopCenter),
                color = ComposeAppTheme.colors.steel10,
                thickness = 1.dp,
            )
        }
        if (borderBottom) {
            HsDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = ComposeAppTheme.colors.steel10,
                thickness = 1.dp,
            )
        }
        content()
    }
}

@Composable
fun SectionPremiumUniversalLawrence(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val brush = Brush.horizontalGradient(
        0f to Color(0xFFFFD000),
        1f to Color(0xFFFFA800),
    )
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, brush, RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence),
        content = content,
    )
}

@Composable
fun SectionUniversalLawrence(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence),
        content = content,
    )
}

@Composable
fun HsDivider(
    modifier: Modifier = Modifier,
    color: Color = ComposeAppTheme.colors.blade,
    thickness: Dp = 0.5.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

@Composable
fun HSpacer(width: Dp) {
    Spacer(modifier = Modifier.width(width))
}

@Composable
fun RowScope.HFillSpacer(minWidth: Dp) {
    Spacer(modifier = Modifier.width(minWidth))
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
fun VSpacer(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}

@Composable
fun ColumnScope.VFillSpacer(minHeight: Dp) {
    Spacer(modifier = Modifier.height(minHeight))
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
fun BadgeBase(
    background: Color,
    content: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
fun PremiumHeader(
    starPainter: Painter,
    text: String = stringResource(Res.string.premium_title),
    horizontalPadding: Dp = 32.dp,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .height(32.dp)
            .padding(top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(16.dp),
            painter = starPainter,
            tint = ComposeAppTheme.colors.jacob,
            contentDescription = null,
        )
        subhead1_jacob(text = text, maxLines = 1)
    }
}
