package cash.p.terminal.modules.coin.overview.ui

import cash.p.terminal.resources.CoinPage_AiGeneratedText
import cash.p.terminal.resources.CoinPage_Overview
import cash.p.terminal.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui.compose.components.DescriptionMarkdown
import cash.p.terminal.ui_compose.components.CellSingleLineClear
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_brand
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun About(text: String) {
    Column {
        CellSingleLineClear(borderTop = true) {
            body_leah(text = stringResource(Res.string.CoinPage_Overview))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeAppTheme.colors.surfacePrimary)
        ) {
            DescriptionMarkdown(
                text = text,
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                subhead2_brand(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    text = stringResource(Res.string.CoinPage_AiGeneratedText),
                )
            }
            VSpacer(12.dp)
        }
    }
}
