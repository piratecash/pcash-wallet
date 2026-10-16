package cash.p.terminal.modules.blockchainstatus

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.blockchain_status
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun BlockchainStatusButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = onClick
            ) {
                body_leah(
                    modifier = Modifier.weight(1f),
                    text = stringResource(Res.string.blockchain_status)
                )
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    tint = ComposeAppTheme.colors.iconSecondary,
                    contentDescription = null,
                )
            }
        },
        modifier,
    )
}

/** Opens the platform's Blockchain Status page; `null` hides the Status button. */
@Composable
fun blockchainStatusOpener(navigation: HSNavigation): ((Blockchain) -> Unit)? {
    val platformPages = koinInject<WalletPlatformPages>()
    if (!platformPages.isBlockchainStatusAvailable) return null
    return { blockchain -> platformPages.blockchainStatus(blockchain)?.let(navigation::slideFromRight) }
}
