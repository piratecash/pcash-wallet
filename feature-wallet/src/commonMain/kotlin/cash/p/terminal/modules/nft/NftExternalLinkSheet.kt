package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.nft.NftLinkButton
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.modules.nft.operation.NftConfirmSheetContent
import cash.p.terminal.modules.nft.operation.NftConfirmStyle
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.nft_link_open_anyway
import cash.p.terminal.resources.nft_link_unsupported
import cash.p.terminal.resources.nft_link_warning_body
import cash.p.terminal.resources.nft_link_warning_title
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.openOrNotifyUnsupported
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Warns before opening [url] from an unverified collection; closes itself with [Confirmed]. */
class NftExternalLinkSheet(private val url: String) : HSBottomSheet() {

    data class Confirmed(val url: String)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        NftExternalLinkSheetContent(
            url = url,
            onConfirm = {
                navigation.setResult(this, Confirmed(url))
                navigation.navigateUpSafely()
            },
            onCancel = navigation::navigateUpSafely,
        )
    }
}

/** Opens a metadata button: unsupported links only toast, suspicious collections warn first. */
@Composable
internal fun HSNavigation.rememberNftLinkOpener(viewModel: NftPreviewViewModel): (NftLinkButton) -> Unit {
    val hud = rememberHudMessenger()
    val scope = rememberCoroutineScope()
    val urlOpener = LocalUrlOpener.current
    val sendPages: WalletSendPages = koinInject()

    fun open(target: NftLinkTarget) {
        when (target) {
            is NftLinkTarget.Web -> urlOpener.open(target.url)
            is NftLinkTarget.Transfer -> openOrNotifyUnsupported(sendPages.sendTokenSelect(target.openSend), hud)
            NftLinkTarget.Unsupported -> scope.launch { hud.showWarning(getString(Res.string.nft_link_unsupported)) }
        }
    }

    val currentOpen by rememberUpdatedState(::open)
    val launcher = rememberResultLauncher<NftExternalLinkSheet.Confirmed> { result ->
        if (viewModel.uiState.canOpenConfirmedLink(result.url)) currentOpen(nftLinkTarget(result.url))
    }

    return { button ->
        val target = nftLinkTarget(button.uri)
        if (target != NftLinkTarget.Unsupported && viewModel.uiState.isSuspicious) {
            launcher.slideFromBottom(NftExternalLinkSheet(button.uri))
        } else {
            open(target)
        }
    }
}

@Composable
internal fun NftExternalLinkSheetContent(url: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    NftConfirmSheetContent(
        icon = Res.drawable.ic_attention_24,
        title = stringResource(Res.string.nft_link_warning_title),
        body = stringResource(Res.string.nft_link_warning_body, url),
        rows = emptyList(),
        confirmTitle = stringResource(Res.string.nft_link_open_anyway),
        confirmStyle = NftConfirmStyle.Destructive,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}

@Preview
@Composable
private fun NftExternalLinkSheetPreview() {
    ComposeAppTheme {
        NftExternalLinkSheetContent(url = "https://win-5000-usdt.example/claim", onConfirm = {}, onCancel = {})
    }
}
