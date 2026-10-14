package cash.p.terminal.ui.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

/** Overloads for app screens that still pass Android drawable ids. */
@Composable
fun ListEmptyView(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
) {
    ScreenMessageWithAction(text, painterResource(icon), modifier, paddingValues)
}

@Composable
fun ScreenMessageWithAction(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
    actionsComposable: (@Composable () -> Unit)? = null
) {
    ScreenMessageWithAction(text, painterResource(icon), modifier, paddingValues, actionsComposable)
}
