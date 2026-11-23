package cash.p.terminal.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource

open class HSCaution(
    val s: TranslatableString,
    val type: Type = Type.Error,
    val description: TranslatableString? = null,
    val icon: DrawableResource? = null,
) {
    @Composable
    fun getString() = s.getString()

    @Composable
    fun getDescription() = description?.getString()

    enum class Type {
        Error, Warning
    }

    fun isError() = type == Type.Error
    fun isWarning() = type == Type.Warning
}

/** [HSCaution.s] alone, or as the title over [HSCaution.description]. */
@Composable
fun CautionErrorBlock(caution: HSCaution, modifier: Modifier = Modifier) {
    val description = caution.getDescription()
    TextImportantError(
        modifier = modifier,
        text = description ?: caution.getString(),
        title = caution.getString().takeIf { description != null },
        icon = caution.icon,
    )
}

@Preview
@Composable
private fun CautionErrorBlockPreview() {
    ComposeAppTheme {
        CautionErrorBlock(
            HSCaution(
                s = TranslatableString.PlainString("This NFT transfer won't go through"),
                description = TranslatableString.PlainString(
                    "We checked the transaction before sending. This NFT's contract will not pass it to the " +
                        "recipient and will keep the attached 0.05 TON."
                ),
                icon = Res.drawable.ic_attention_24,
            )
        )
    }
}
