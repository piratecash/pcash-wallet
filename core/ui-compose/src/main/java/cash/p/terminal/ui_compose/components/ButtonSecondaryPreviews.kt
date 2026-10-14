package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_20
import org.jetbrains.compose.resources.painterResource

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ButtonSecondaryDefaultPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonSecondaryDefault(
                title = "Secondary Default",
                onClick = {}
            )
            ButtonSecondaryDefault(
                title = "Secondary Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ButtonSecondaryCustomPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonSecondaryCustom(
                title = "Custom Color",
                textColor = ComposeAppTheme.colors.brandDefault,
                onClick = {}
            )
            ButtonSecondaryCustom(
                title = "Custom Red",
                textColor = ComposeAppTheme.colors.statusError,
                onClick = {}
            )
            ButtonSecondaryCustom(
                title = "Custom Disabled",
                textColor = ComposeAppTheme.colors.brandDefault,
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ButtonSecondaryYellowPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonSecondaryYellow(
                title = "Yellow Button",
                onClick = {}
            )
            ButtonSecondaryYellow(
                title = "Yellow Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ButtonSecondaryWithIconPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonSecondaryWithIcon(
                title = "With Icon",
                iconRight = painterResource(Res.drawable.ic_info_20),
                onClick = {}
            )
            ButtonSecondaryWithIcon(
                title = "With Icon Disabled",
                iconRight = painterResource(Res.drawable.ic_info_20),
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ButtonSecondaryTransparentPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonSecondaryTransparent(
                title = "Transparent",
                onClick = {}
            )
            ButtonSecondaryTransparent(
                title = "With Icon",
                iconRight = Res.drawable.ic_info_20,
                onClick = {}
            )
            ButtonSecondaryTransparent(
                title = "Transparent Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ButtonSecondaryTogglePreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonSecondaryToggle(
                select = Select(
                    selected = PreviewToggleOption.Option1,
                    options = PreviewToggleOption.entries
                ),
                onSelect = {}
            )
            ButtonSecondaryToggle(
                select = Select(
                    selected = PreviewToggleOption.Option2,
                    options = PreviewToggleOption.entries
                ),
                onSelect = {}
            )
            ButtonSecondaryToggle(
                select = Select(
                    selected = PreviewToggleOption.Option1,
                    options = PreviewToggleOption.entries
                ),
                onSelect = {},
                enabled = false
            )
        }
    }
}

private enum class PreviewToggleOption : WithTranslatableTitle {
    Option1, Option2, Option3;

    override val title: TranslatableString
        get() = TranslatableString.PlainString(name)
}
