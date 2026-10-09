package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

internal val LocalOnElevatedSurface = staticCompositionLocalOf { false }

@Composable
fun plateBackground(): Color =
    if (LocalOnElevatedSurface.current) ComposeAppTheme.colors.transparent else ComposeAppTheme.colors.surfacePrimary

/** Outline that replaces the fill of a plate on an elevated surface; skip it for plates already outlined. */
@Composable
fun Modifier.plateOutline(shape: Shape): Modifier =
    if (LocalOnElevatedSurface.current) border(1.dp, ComposeAppTheme.colors.borderDefault, shape) else this
