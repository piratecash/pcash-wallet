package cash.p.terminal.modules.signmessage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.feature.wallet.rememberTransferAction
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.removeLastUntilSafely
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.rememberTextShare
import cash.p.terminal.wallet.Wallet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SignMessagePage(private val wallet: Wallet) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: SignMessageViewModel = koinViewModel { parametersOf(wallet) }
        val uiState = viewModel.uiState
        val hud = rememberHudMessenger()
        val addressLauncher = navigation.rememberResultLauncher<SignAddressSelectPage.Result> {
            viewModel.onAddressSelect(it.address)
        }
        val sign = navigation.rememberTransferAction<Unit> { viewModel.sign() }

        LaunchedEffect(uiState.error) {
            uiState.error?.let {
                hud.showError(it)
                viewModel.onErrorShown()
            }
        }
        LaunchedEffect(uiState.result) {
            uiState.result?.let {
                navigation.slideFromRight(SignatureResultPage(it))
                viewModel.onResultShown()
            }
        }

        SignMessageScreen(
            uiState = uiState,
            navigation = navigation,
            onAddressClick = {
                viewModel.addressSelectInput()?.let { addressLauncher.slideFromRight(SignAddressSelectPage(it)) }
            },
            onMessageChange = viewModel::onMessageChange,
            onFormatSelect = viewModel::onFormatSelect,
            onSignClick = { sign(Unit) },
            onBackClick = navigation::navigateUpSafely,
        )
    }
}

class SignAddressSelectPage(private val input: SignAddressSelectInput) : HSPage() {

    data class Result(val address: String)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SignAddressSelectScreen(
            input = input,
            onSelect = { address ->
                navigation.setResult(this, Result(address))
                navigation.navigateUpSafely()
            },
            onBackClick = navigation::navigateUpSafely,
        )
    }
}

class SignatureResultPage(private val signedMessage: SignedMessage) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val hud = rememberHudMessenger()
        val textShare = rememberTextShare()
        val copiedText = stringResource(Res.string.Hud_Text_Copied)
        SignatureResultScreen(
            signedMessage = signedMessage,
            shareAvailable = textShare.isAvailable,
            onCopyClick = {
                getKoinInstance<IClipboardManager>().copyText(signedMessage.signature)
                hud.showSuccess(copiedText)
            },
            onShareClick = { textShare.share(signedMessage.signature) },
            onDoneClick = { navigation.removeLastUntilSafely(SignMessagePage::class, inclusive = true) },
            onBackClick = navigation::navigateUpSafely,
        )
    }
}
