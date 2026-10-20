package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.launcher_main_preview
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun rememberPcashQrLogoPainter(): Painter = painterResource(Res.drawable.launcher_main_preview)
