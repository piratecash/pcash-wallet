package cash.p.terminal.ui_compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun getRippleConfiguration(): RippleConfiguration =
    if (isSystemInDarkTheme()) {
        RippleConfiguration(color = Color.White)
    } else {
        RippleConfiguration(color = Color.Black)
    }
