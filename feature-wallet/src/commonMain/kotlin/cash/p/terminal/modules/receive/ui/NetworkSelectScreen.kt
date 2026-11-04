package cash.p.terminal.modules.receive.ui

import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.description
import cash.p.terminal.core.title
import cash.p.terminal.modules.receive.viewmodels.NetworkSelectViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.showNotSupportedOnPlatform
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Balance_Network
import cash.p.terminal.resources.Balance_NetworkSelectDescription
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_platform_placeholder_32

@Composable
fun NetworkSelectScreen(
    navigation: HSNavigation,
    activeAccount: Account,
    fullCoin: FullCoin,
    onSelect: (Wallet) -> Unit
) {
    val viewModel = koinViewModel<NetworkSelectViewModel> { parametersOf(activeAccount, fullCoin) }
    val coroutineScope = rememberCoroutineScope()
    val hud = rememberHudMessenger()

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Balance_Network),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = listOf()
            )
        }
    ) {
        Column(Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                InfoText(
                    text = stringResource(Res.string.Balance_NetworkSelectDescription)
                )
                VSpacer(20.dp)
                CellUniversalLawrenceSection(viewModel.eligibleTokens) { token ->
                    val blockchain = token.blockchain
                    val description = if (token.title.isNotBlank()) {
                        "${blockchain.description} ${token.title}"
                    } else {
                        blockchain.description
                    }
                    SectionUniversalItem {
                        NetworkCell(
                            title = blockchain.name,
                            subtitle = description,
                            imageUrl = blockchain.type.imageUrl,
                            onClick = {
                                if (viewModel.isSupported(token)) {
                                    coroutineScope.launch {
                                        viewModel.getOrCreateWallet(token)?.let(onSelect)
                                    }
                                } else {
                                    hud.showNotSupportedOnPlatform()
                                }
                            }
                        )
                    }
                }
                VSpacer(32.dp)
            }
        }
    }
}

@Composable
fun NetworkCell(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: (() -> Unit)? = null
) {
    RowUniversal(
        onClick = onClick
    ) {
        Image(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(32.dp),
            painter = rememberAsyncImagePainterWithFallback(
                model = imageUrl,
                error = painterResource(Res.drawable.ic_platform_placeholder_32)
            ),
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            body_leah(text = title)
            subhead2_grey(text = subtitle)
        }
        Icon(
            modifier = Modifier.padding(horizontal = 16.dp),
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}
