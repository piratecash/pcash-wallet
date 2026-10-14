package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ListEmptyView(
    text: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
) {
    ScreenMessageWithAction(
        paddingValues = paddingValues,
        text = text,
        modifier = modifier,
        icon = icon,
    )
}

@Composable
fun ScreenMessageWithAction(
    text: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
    actionsComposable: (@Composable () -> Unit)? = null
) {
    ScreenMessageWithAction(text, painterResource(icon), modifier, paddingValues, actionsComposable)
}

@Composable
fun ScreenMessageWithAction(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
    actionsComposable: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .padding(paddingValues)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
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
                painter = icon,
                contentDescription = text,
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
        Spacer(Modifier.height(32.dp))
        subhead2_grey(
            modifier = Modifier.padding(horizontal = 48.dp),
            text = text,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        actionsComposable?.let { composable ->
            Spacer(Modifier.height(32.dp))
            composable.invoke()
        }
    }
}
