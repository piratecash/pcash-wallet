package cash.p.terminal.modules.info.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui.compose.components.*
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.InfoTextBody
import cash.p.terminal.ui_compose.components.body_bran
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun InfoHeader(
    text: StringResource,
) {
    InfoH1(stringResource(text))
}

@Composable
fun InfoSubHeader(
    text: StringResource,
) {
    InfoH3(stringResource(text))
}

@Composable
fun InfoBody(
    text: StringResource,
) {
    InfoTextBody(stringResource(text))
}

@Composable
fun BulletedText(
    text: StringResource,
) {
    BulletedText(stringResource(text))
}

@Composable
private fun BulletedText(
    text: String,
) {
    Row(
        modifier = Modifier.padding(start = 24.dp, end = 32.dp, top = 12.dp, bottom = 12.dp)
    ) {
        body_bran(
            modifier = Modifier.width(24.dp),
            text = "•"
        )
        HSpacer(16.dp)
        body_bran(
            modifier = Modifier.weight(1f),
            text = text
        )
    }
}
