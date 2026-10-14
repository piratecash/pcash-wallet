package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryDefaultPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryDefault(
                title = "Primary Default",
                onClick = {}
            )
            ButtonPrimaryDefault(
                title = "Primary Default Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryDefaultWithIconPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryDefaultWithIcon(
                icon = painterResource(R.drawable.ic_back),
                title = "With Icon",
                onClick = {}
            )
            ButtonPrimaryDefaultWithIcon(
                icon = painterResource(R.drawable.ic_back),
                title = "With Tinted Icon",
                iconTint = ComposeAppTheme.colors.brandDefault,
                onClick = {}
            )
            ButtonPrimaryDefaultWithIcon(
                icon = painterResource(R.drawable.ic_back),
                title = "With Icon Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryTransparentPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryTransparent(
                title = "Transparent",
                onClick = {}
            )
            ButtonPrimaryTransparent(
                title = "Transparent Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryYellowPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryYellow(
                title = "Yellow Button",
                onClick = {}
            )
            ButtonPrimaryYellow(
                title = "Yellow Loading",
                onClick = {},
                loadingIndicator = true
            )
            ButtonPrimaryYellow(
                title = "Yellow Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryYellowWithIconPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryYellowWithIcon(
                icon = painterResource(R.drawable.ic_back),
                title = "Yellow With Icon",
                onClick = {}
            )
            ButtonPrimaryYellowWithIcon(
                icon = painterResource(R.drawable.ic_back),
                title = "Yellow With Tint",
                iconTint = ComposeAppTheme.colors.statusError,
                onClick = {}
            )
            ButtonPrimaryYellowWithIcon(
                icon = painterResource(R.drawable.ic_back),
                title = "Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryRedPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryRed(
                title = "Red Button",
                onClick = {}
            )
            ButtonPrimaryRed(
                title = "Red Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonPrimaryYellowWithSpinnerPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonPrimaryYellowWithSpinner(
                title = "Yellow Spinner",
                onClick = {},
                showSpinner = false
            )
            ButtonPrimaryYellowWithSpinner(
                title = "Showing Spinner",
                onClick = {},
                showSpinner = true
            )
            ButtonPrimaryYellowWithSpinner(
                title = "Spinner Disabled",
                onClick = {},
                enabled = false
            )
        }
    }
}
