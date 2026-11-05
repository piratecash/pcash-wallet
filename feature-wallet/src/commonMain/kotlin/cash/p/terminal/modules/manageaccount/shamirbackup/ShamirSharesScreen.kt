package cash.p.terminal.modules.manageaccount.shamirbackup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.manageaccount.backupconfirmkey.WordGrid
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseModule
import cash.p.terminal.resources.Alert_Copy
import cash.p.terminal.resources.Button_Done
import cash.p.terminal.resources.Button_Share
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.ic_share_up_20
import cash.p.terminal.resources.icon_24_lock
import cash.p.terminal.resources.shamir_backup_title
import cash.p.terminal.resources.shamir_done_banner
import cash.p.terminal.resources.shamir_restore_part
import cash.p.terminal.resources.shamir_share_confirm
import cash.p.terminal.resources.shamir_share_saved
import cash.p.terminal.resources.shamir_share_title
import cash.p.terminal.resources.shamir_share_written
import cash.p.terminal.resources.shamir_shares_banner
import cash.p.terminal.resources.shamir_step_shares
import cash.p.terminal.resources.shamir_write_all
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.Caption
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.TextImportantInfo
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** [onShare] is null where the platform has no share sheet. */
internal class ShamirShareActions(
    val onCopy: () -> Unit,
    val onShare: (() -> Unit)?,
    val onWrittenDownChange: (Boolean) -> Unit,
    val onConfirm: () -> Unit,
)

@Composable
internal fun ShamirSharesScreen(
    uiState: ShamirBackupUiState,
    actions: ShamirShareActions,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    ShamirScaffold(
        title = stringResource(Res.string.shamir_backup_title),
        onBack = onBack,
        buttons = {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(
                    if (uiState.allConfirmed) Res.string.Button_Done else Res.string.shamir_write_all
                ),
                onClick = onFinish,
                enabled = uiState.allConfirmed
            )
        }
    ) {
        ShamirStepIndicator(step = 3, title = stringResource(Res.string.shamir_step_shares))
        VSpacer(8.dp)
        if (uiState.allConfirmed) {
            TextImportantInfo(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.shamir_done_banner)
            )
        } else {
            SchemeBanner(uiState.scheme)
        }
        VSpacer(16.dp)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.shares.forEachIndexed { index, share ->
                val number = index + 1
                when {
                    index < uiState.confirmedCount -> SavedPartRow(number)
                    index == uiState.confirmedCount -> ShareCard(number, share, uiState, actions)
                    else -> LockedPartRow(number, uiState.shares.size)
                }
            }
        }
        VSpacer(32.dp)
    }
}

@Composable
private fun SchemeBanner(scheme: ShamirScheme) {
    val fraction = schemeFraction(scheme)
    val text = pluralStringResource(Res.plurals.shamir_shares_banner, scheme.threshold, fraction, scheme.threshold)
    TextImportantInfo(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = text.withEmphasis(fraction, boldSpan)
    )
}

@Composable
private fun SavedPartRow(number: Int) {
    ShamirPartRow(title = stringResource(Res.string.shamir_restore_part, number)) {
        ShamirCheckIcon()
        HSpacer(4.dp)
        Caption(text = stringResource(Res.string.shamir_share_saved), color = ComposeAppTheme.colors.statusSuccess)
    }
}

@Composable
private fun LockedPartRow(number: Int, total: Int) {
    ShamirPartRow(
        title = stringResource(Res.string.shamir_share_title, number, total),
        modifier = Modifier.alpha(0.5f)
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(Res.drawable.icon_24_lock),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}

@Composable
private fun ShareCard(number: Int, share: List<String>, uiState: ShamirBackupUiState, actions: ShamirShareActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            body_leah(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.shamir_share_title, number, uiState.shares.size)
            )
            ButtonSecondaryCircle(
                icon = Res.drawable.ic_copy_20,
                contentDescription = stringResource(Res.string.Alert_Copy),
                onClick = actions.onCopy
            )
            actions.onShare?.let { onShare ->
                HSpacer(8.dp)
                ButtonSecondaryCircle(
                    icon = Res.drawable.ic_share_up_20,
                    contentDescription = stringResource(Res.string.Button_Share),
                    onClick = onShare
                )
            }
        }
        WordGrid(
            words = share.mapIndexed { index, word -> RecoveryPhraseModule.WordNumbered(word, index + 1) },
            cellColor = ComposeAppTheme.colors.controlActionBorder
        )
        WrittenDownRow(checked = uiState.writtenDown, onCheckedChange = actions.onWrittenDownChange)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.shamir_share_confirm, number),
            onClick = actions.onConfirm,
            enabled = uiState.writtenDown
        )
    }
}

@Composable
private fun WrittenDownRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HsCheckbox(checked = checked, onCheckedChange = null)
        HSpacer(16.dp)
        subhead2_leah(stringResource(Res.string.shamir_share_written))
    }
}

private val previewShares = listOf(
    "academic acid acrobat romp chubby airport client inmate both quarter crisis dismiss dress " +
        "amount hearing dish wrist hunting blimp wisdom",
    "academic acid beard romp clinic brother grownup taxi dish thunder nylon elevator express " +
        "duration dress yield amazing ruler grumpy alpha",
    "academic acid ceramic romp acid cradle dragon inherit capacity loyalty lunar quiet crowd " +
        "pistol emerald slice seafood merit agency boring",
).map { it.split(" ") }

@Composable
private fun SharesPreview(confirmedCount: Int, writtenDown: Boolean = false) {
    ComposeAppTheme {
        ShamirSharesScreen(
            uiState = ShamirBackupUiState(
                scheme = ShamirScheme(threshold = 2, total = 3),
                showPassphraseWarning = false,
                shares = previewShares,
                confirmedCount = confirmedCount,
                writtenDown = writtenDown,
                check = null,
            ),
            actions = ShamirShareActions(onCopy = {}, onShare = {}, onWrittenDownChange = {}, onConfirm = {}),
            onFinish = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ShamirSharesScreenFirstPartPreview() {
    SharesPreview(confirmedCount = 0)
}

@Preview
@Composable
private fun ShamirSharesScreenSecondPartPreview() {
    SharesPreview(confirmedCount = 1, writtenDown = true)
}

@Preview
@Composable
private fun ShamirSharesScreenDonePreview() {
    SharesPreview(confirmedCount = 3)
}
