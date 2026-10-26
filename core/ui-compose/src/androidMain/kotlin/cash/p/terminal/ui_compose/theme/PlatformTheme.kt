package cash.p.terminal.ui_compose.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material3.MaterialTheme as MaterialTheme3
import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme3(colorScheme = ComposeAppTheme.colors.toMaterialColorScheme()) {
        MaterialTheme(content = content)
    }
}
