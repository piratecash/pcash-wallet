package cash.p.terminal.modules.syncerror

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.isEvm
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsPage
import cash.p.terminal.modules.evmnetwork.EvmNetworkPage
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BalanceSyncError_ButtonChangeSource
import cash.p.terminal.resources.BalanceSyncError_ButtonReport
import cash.p.terminal.resources.BalanceSyncError_ButtonRetry
import cash.p.terminal.resources.BalanceSyncError_Title
import cash.p.terminal.resources.Hud_Text_EmailAddressCopied
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.balance_sync_error_changeable_source
import cash.p.terminal.resources.balance_sync_error_fixed_source
import cash.p.terminal.resources.ic_attention_red_24
import cash.p.terminal.resources.source_blocked_by_provider_error
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.annotatedStringResource
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.isMonero
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SyncErrorSheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SyncErrorScreen(navigation, input.wallet, input.errorMessage ?: "", input.isBlockedByProvider)
    }

    @CommonParcelize
    data class Input(
        val wallet: Wallet,
        val errorMessage: String?,
        val isBlockedByProvider: Boolean
    ) : CommonParcelable
}

fun HSNavigation.showSyncErrorDialog(
    wallet: Wallet,
    errorMessage: String?,
    isBlockedByProvider: Boolean
) {
    if (lastOrNull() is SyncErrorSheet) return

    slideFromBottom(SyncErrorSheet(SyncErrorSheet.Input(wallet, errorMessage, isBlockedByProvider)))
}

internal fun syncErrorDescriptionRes(
    isMonero: Boolean,
    isEvm: Boolean,
    isBlockedByProvider: Boolean,
    sourceChangeable: Boolean
): StringResource = when {
    isMonero || (isEvm && isBlockedByProvider) -> Res.string.source_blocked_by_provider_error
    sourceChangeable -> Res.string.balance_sync_error_changeable_source
    else -> Res.string.balance_sync_error_fixed_source
}

@Composable
private fun SyncErrorScreen(
    navigation: HSNavigation,
    wallet: Wallet,
    error: String,
    isBlockedByProvider: Boolean
) {
    val viewModel = koinViewModel<SyncErrorViewModel> { parametersOf(wallet) }

    val hudMessenger = rememberHudMessenger()
    val clipboardManager = LocalClipboardManager.current
    val sendMail = rememberMailComposer()
    val emailCopiedText = stringResource(Res.string.Hud_Text_EmailAddressCopied)

    val errorDescription = annotatedStringResource(
        syncErrorDescriptionRes(
            isMonero = wallet.token.isMonero(),
            isEvm = wallet.token.blockchainType.isEvm,
            isBlockedByProvider = isBlockedByProvider,
            sourceChangeable = viewModel.sourceChangeable
        )
    )

    SyncErrorContent(
        coinCode = wallet.coin.code,
        errorDescription = errorDescription,
        showChangeSourceButton = viewModel.sourceChangeable,
        onClose = navigation::navigateUpSafely,
        onRetry = {
            viewModel.retry()
            navigation.navigateUpSafely()
        },
        onChangeSource = {
            navigation.navigateUpSafely()

            val blockchainWrapper = viewModel.blockchainWrapper
            when (blockchainWrapper?.type) {
                SyncErrorModule.BlockchainWrapper.Type.Bitcoin -> {
                    navigation.slideFromBottom(BtcBlockchainSettingsPage(blockchainWrapper.blockchain))
                }

                SyncErrorModule.BlockchainWrapper.Type.Evm -> {
                    navigation.slideFromBottom(EvmNetworkPage(blockchainWrapper.blockchain))
                }

                else -> {}
            }
        },
        onReport = {
            navigation.navigateUpSafely()

            if (!sendMail(viewModel.reportEmail, viewModel.buildReportBody(error))) {
                clipboardManager.setText(AnnotatedString(viewModel.reportEmail))
                hudMessenger.showSuccess(emailCopiedText)
            }
        }
    )
}

@Composable
internal fun SyncErrorContent(
    coinCode: String,
    errorDescription: AnnotatedString,
    showChangeSourceButton: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onChangeSource: () -> Unit,
    onReport: () -> Unit,
) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_attention_red_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
            title = stringResource(Res.string.BalanceSyncError_Title) + " - $coinCode",
            onCloseClick = onClose
        ) {
            subhead2_grey(
                text = errorDescription,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            Spacer(Modifier.height(20.dp))
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.BalanceSyncError_ButtonRetry),
                onClick = onRetry
            )
            if (showChangeSourceButton) {
                Spacer(Modifier.height(12.dp))
                ButtonPrimaryDefault(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    title = stringResource(Res.string.BalanceSyncError_ButtonChangeSource),
                    onClick = onChangeSource
                )
            }
            Spacer(Modifier.height(12.dp))
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.BalanceSyncError_ButtonReport),
                onClick = onReport
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Opens a mail draft of the report; `false` when there is no mail app to open it. */
@Composable
internal expect fun rememberMailComposer(): (email: String, body: String) -> Boolean
