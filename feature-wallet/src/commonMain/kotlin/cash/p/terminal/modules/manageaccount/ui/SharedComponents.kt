package cash.p.terminal.modules.manageaccount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseModule
import cash.p.terminal.ui.compose.components.PcashQrCodeImage
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.C2
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.D2
import cash.p.terminal.ui_compose.components.D7
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.RecoveryPhrase_CopyWarning_Title
import cash.p.terminal.resources.RecoveryPhrase_ShowPhrase
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ShowKey_Passphrase
import cash.p.terminal.resources.ShowKey_PrivateKeyCopyWarning_Cancel
import cash.p.terminal.resources.ShowKey_PrivateKeyCopyWarning_Proceed
import cash.p.terminal.resources.ShowKey_PrivateKeyCopyWarning_Text
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.ic_key_phrase_20
import cash.p.terminal.resources.tap_to_show_qr_code

/** Copies a secret and confirms it with the standard "Copied" HUD. */
@Composable
fun rememberCopyWithHud(): (String) -> Unit {
    // No Koin graph in @Preview, so skip the clipboard lookup there.
    if (LocalInspectionMode.current) return {}
    val clipboardManager = koinInject<IClipboardManager>()
    val hudMessenger = rememberHudMessenger()
    val copiedText = stringResource(Res.string.Hud_Text_Copied)
    return remember(clipboardManager, hudMessenger, copiedText) {
        { text ->
            clipboardManager.copyText(text)
            hudMessenger.showSuccess(copiedText)
        }
    }
}

@Composable
fun ActionButton(title: StringResource, onClick: () -> Unit) {
    ButtonsGroupWithShade {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            title = stringResource(title),
            onClick = onClick,
        )
    }
}

@Composable
fun HidableContent(
    content: String,
    hideScreenText: String? = null,
    onToggleHidden: (() -> Unit)? = null
) {
    var hidden by remember { mutableStateOf(hideScreenText != null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(24.dp))
            .clickable(enabled = hideScreenText != null, onClick = {
                hidden = !hidden
                onToggleHidden?.invoke()
            })
    ) {

        D2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .hiddenFromSemantics(hideScreenText != null && hidden),
            text = content
        )

        if (hideScreenText != null && hidden) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ComposeAppTheme.colors.backgroundBase),
                contentAlignment = Alignment.Center
            ) {
                subhead2_grey(hideScreenText)
            }
        }
    }
}

@Composable
fun KeyActionItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                onClick = onClick
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                body_leah(
                    modifier = Modifier.weight(1f),
                    text = title,
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
        })
    InfoText(text = description)
    Spacer(Modifier.height(20.dp))
}

@Composable
fun ConfirmCopyBottomSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    BottomSheetHeader(
        iconPainter = painterResource(Res.drawable.ic_attention_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
        title = stringResource(Res.string.RecoveryPhrase_CopyWarning_Title),
        onCloseClick = onCancel
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.ShowKey_PrivateKeyCopyWarning_Text)
        )

        Spacer(modifier = Modifier.height(32.dp))

        ButtonPrimaryRed(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = stringResource(Res.string.ShowKey_PrivateKeyCopyWarning_Proceed),
            onClick = onConfirm
        )

        Spacer(modifier = Modifier.height(12.dp))

        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            title = stringResource(Res.string.ShowKey_PrivateKeyCopyWarning_Cancel),
            onClick = onCancel
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PassphraseCell(passphrase: String, hidden: Boolean) {
    if (passphrase.isNotBlank()) {
        CellSingleLineLawrenceSection(
            listOf {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_key_phrase_20),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.iconSecondary
                    )
                    D1(
                        text = stringResource(Res.string.ShowKey_Passphrase),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    C2(text = if (hidden) "*****" else passphrase)
                }
            })
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeedPhraseList(
    wordsNumbered: List<RecoveryPhraseModule.WordNumbered>,
    hidden: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(24.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .hiddenFromSemantics(hidden),
            maxItemsInEachRow = 4,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalArrangement = Arrangement.Center
        ) {
            wordsNumbered.forEach { word ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    D7(text = word.number.toString())
                    HSpacer(8.dp)
                    B2(text = word.word)
                }
            }
        }

        if (hidden) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ComposeAppTheme.colors.backgroundBase),
                contentAlignment = Alignment.Center
            ) {
                subhead2_grey(text = stringResource(Res.string.RecoveryPhrase_ShowPhrase))
            }
        }
    }
}

@Composable
fun SeedPhraseQrCard(
    encryptedContent: String,
    hidden: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(24.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(ComposeAppTheme.colors.qrBackground)
                .padding(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .hiddenFromSemantics(hidden),
            contentAlignment = Alignment.Center
        ) {
            SeedQrCodeImage(encryptedContent)
        }

        if (hidden) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ComposeAppTheme.colors.backgroundBase),
                contentAlignment = Alignment.Center
            ) {
                subhead2_grey(text = stringResource(Res.string.tap_to_show_qr_code))
            }
        }
    }
}

// The overlay only paints over the secret; accessibility services would still read it.
private fun Modifier.hiddenFromSemantics(hidden: Boolean): Modifier =
    if (hidden) clearAndSetSemantics {} else this

@Composable
private fun SeedQrCodeImage(content: String) {
    PcashQrCodeImage(
        content = content,
        modifier = Modifier.fillMaxSize(),
    )
}
