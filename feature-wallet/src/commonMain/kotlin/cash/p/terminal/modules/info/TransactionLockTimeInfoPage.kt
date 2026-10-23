package cash.p.terminal.modules.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.info.ui.InfoHeader
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Info_LockTime_Description
import cash.p.terminal.resources.Info_LockTime_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.InfoTextBody
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.stringResource

class TransactionLockTimeInfoPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        InfoScreen(
            input.lockTime,
            onClose = { navigation.navigateUpSafely() }
        )
    }

    @CommonParcelize
    data class Input(val lockTime: String) : CommonParcelable
}

@Composable
private fun InfoScreen(
    lockDate: String,
    onClose: () -> Unit
) {

    val description = stringResource(Res.string.Info_LockTime_Description).format(lockDate)

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onClose
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoHeader(Res.string.Info_LockTime_Title)
                InfoTextBody(description)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
