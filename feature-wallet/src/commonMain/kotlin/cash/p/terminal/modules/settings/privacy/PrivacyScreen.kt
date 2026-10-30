package cash.p.terminal.modules.settings.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Privacy_BulletedText1
import cash.p.terminal.resources.Privacy_BulletedText2
import cash.p.terminal.resources.Privacy_BulletedText3
import cash.p.terminal.resources.Privacy_Information
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_Privacy
import cash.p.terminal.resources.ShareCrashData
import cash.p.terminal.resources.footer_text
import cash.p.terminal.resources.ic_share_24px
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.InfoTextBody
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.currentYear
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** [shareCrashData] is `null` on a platform that collects no crash data: the intro, bullet 3 and the toggle are hidden. */
@Composable
fun PrivacyScreen(
    shareCrashData: Boolean?,
    onToggleShareCrashData: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.backgroundBase)
    ) {
        AppBar(
            title = stringResource(Res.string.Settings_Privacy),
            navigationIcon = { HsBackButton(onClick = onClose) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            val collectsCrashData = shareCrashData != null
            if (collectsCrashData) {
                InfoTextBody(text = stringResource(Res.string.Privacy_Information))
            }
            BulletedText(Res.string.Privacy_BulletedText1)
            BulletedText(Res.string.Privacy_BulletedText2)
            if (collectsCrashData) {
                BulletedText(Res.string.Privacy_BulletedText3)
            }
            VSpacer(height = 16.dp)
            shareCrashData?.let { ShareCrashDataRow(it, onToggleShareCrashData) }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(Res.string.footer_text, currentYear()),
            style = ComposeAppTheme.typography.caption,
            color = ComposeAppTheme.colors.textSecondary,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ShareCrashDataRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SectionUniversalLawrence {
        CellUniversal {
            Icon(
                painter = painterResource(Res.drawable.ic_share_24px),
                contentDescription = "Share",
                tint = ComposeAppTheme.colors.iconSecondary
            )
            HSpacer(width = 16.dp)
            body_leah(text = stringResource(Res.string.ShareCrashData))
            HFillSpacer(minWidth = 8.dp)
            HsSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun BulletedText(text: StringResource) {
    Row(
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 32.dp, bottom = 12.dp)
    ) {
        Text(
            text = "• ",
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
            modifier = Modifier.width(15.dp),
            textAlign = TextAlign.Center
        )
        HSpacer(width = 8.dp)
        Text(
            text = stringResource(text),
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
            modifier = Modifier.padding(end = 32.dp)
        )
    }
}
