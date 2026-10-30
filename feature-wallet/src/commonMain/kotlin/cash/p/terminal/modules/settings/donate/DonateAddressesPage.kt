package cash.p.terminal.modules.settings.donate

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.title
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.imageUrl
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Copy
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Settings_Donate_Addresses
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.ic_platform_placeholder_32

class DonateAddressesPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        DonateScreen(
            onBackPress = navigation::navigateUpSafely
        )
    }

}

@Composable
fun DonateScreen(
    onBackPress: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_Donate_Addresses),
                navigationIcon = {
                    HsBackButton(onClick = onBackPress)
                },
            )
        }
    ) {
        Column(Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)
                SharedAppConfig.donateAddresses.forEach { (blockchainType, address) ->
                    DonateAddress(
                        coinImageUrl = blockchainType.imageUrl,
                        coinName = blockchainType.title,
                        address = address,
                        chainUid = blockchainType.uid
                    )
                    VSpacer(24.dp)
                }

                VSpacer(8.dp)
            }
        }
    }
}

@Composable
private fun DonateAddress(
    coinImageUrl: String,
    coinName: String,
    address: String,
    chainUid: String
) {
    val hud = rememberHudMessenger()

    InfoText(text = coinName.uppercase())
    CellUniversalLawrenceSection() {
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = {
                getKoinInstance<IClipboardManager>().copyText(address)
                hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
            }
        ) {
            Image(
                modifier = Modifier.size(32.dp),
                painter = rememberAsyncImagePainterWithFallback(
                    model = coinImageUrl,
                    error = painterResource(Res.drawable.ic_platform_placeholder_32)
                ),
                contentDescription = "platform"
            )
            HSpacer(16.dp)
            subhead2_leah(
                modifier = Modifier.weight(1f),
                text = address,
            )

            HSpacer(16.dp)
            ButtonSecondaryCircle(
                icon = Res.drawable.ic_copy_20,
                contentDescription = stringResource(Res.string.Button_Copy),
                onClick = {
                    getKoinInstance<IClipboardManager>().copyText(address)
                    hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
                }
            )
        }
    }
}
