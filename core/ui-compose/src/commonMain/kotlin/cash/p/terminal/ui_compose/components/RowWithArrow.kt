package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_attention_red_20
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun RowWithArrow(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showAlert: Boolean = false,
    showSpinner: Boolean = false,
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        onClick = if (showSpinner) null else onClick
    ) {
        body_leah(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showAlert) {
            Image(
                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                painter = painterResource(Res.drawable.ic_attention_red_20),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ComposeAppTheme.colors.statusError),
            )
        }
        if (showSpinner) {
            HSCircularProgressIndicator(progress = 0.15f, size = 20.dp)
        } else {
            Image(
                modifier = Modifier.size(20.dp),
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
            )
        }
    }
}
