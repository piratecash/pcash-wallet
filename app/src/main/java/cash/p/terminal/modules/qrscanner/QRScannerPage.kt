package cash.p.terminal.modules.qrscanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.isTonConnectDeeplink
import cash.p.terminal.modules.main.open
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.QrScannerResult
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.tangem.domain.sdk.CardSdkConfigRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class QRScannerPage(val input: QrScannerInput) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: QRScannerViewModel = koinViewModel()
        val deeplinkParser = remember { getKoinInstance<DeeplinkParser>() }
        val tonConnectManager = remember { getKoinInstance<TonConnectManager>() }
        val cardSdkConfigRepository = remember { getKoinInstance<CardSdkConfigRepository>() }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val hostLifecycleOwner = LocalHostLifecycleOwner.current
        val context = LocalContext.current

        LifecycleStartEffect(Unit) {
            // Samsung can silently disable reader mode when CameraX starts. Disable it explicitly first
            // so Tangem's internal state stays in sync with Android.
            cardSdkConfigRepository.disableReaderModeForQrScanner()
            onStopOrDispose { }
        }

        DisposableEffect(Unit) {
            onDispose {
                cardSdkConfigRepository.restoreReaderModeAfterQrScanner()
            }
        }

        val pageLifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(viewModel) {
            viewModel.scanResult.collectLatest { decoded ->
                // A result can arrive while this page is not resumed (app backgrounded, or a late gallery
                // decode after Back). Wait for this page's own RESUMED: once it is popped, the wait is
                // cancelled, so a late result never reaches the caller or pops it.
                pageLifecycle.withResumed {
                    navigateForScanResult(decoded, navigation, hostLifecycleOwner, deeplinkParser, tonConnectManager)
                }
            }
        }

        QRScannerScreen(
            uiState = uiState,
            title = input.title,
            navigation = navigation,
            showPasteButton = input.showPasteButton,
            allowGalleryWithoutPremium = input.allowGalleryWithoutPremium,
            onScan = viewModel::onFrameScanned,
            onPaste = viewModel::onTextPasted,
            onCloseClick = navigation::navigateUpSafely,
            onCameraPermissionSettingsClick = { openCameraPermissionSettings(context) },
            onGalleryImagePicked = viewModel::onImagePicked,
            onErrorMessageConsumed = viewModel::onErrorMessageConsumed
        )
    }

    private fun navigateForScanResult(
        decoded: String,
        navigation: HSNavigation,
        hostLifecycleOwner: LifecycleOwner,
        deeplinkParser: DeeplinkParser,
        tonConnectManager: TonConnectManager,
    ) {
        val uri = decoded.toUri()
        if (uri.isTonConnectDeeplink()) {
            // TonConnectManager.handle emits to dappRequestFlow, which MainActivity observes and
            // opens the TonConnect page. Launch on the host scope so the suspending network call
            // survives this page being popped.
            hostLifecycleOwner.lifecycleScope.launch {
                tonConnectManager.handle(decoded, closeAppOnResult = false)
            }
            navigation.navigateUp()
            return
        }

        val deeplinkPage = deeplinkParser.parse(decoded)

        when {
            deeplinkPage != null -> {
                // Pop QR scanner first, then navigate to deeplink destination
                navigation.navigateUp()
                navigation.open(deeplinkPage)
            }

            else -> {
                navigation.setResult(this, QrScannerResult(decoded))
                navigation.navigateUp()
            }
        }
    }
}

private fun openCameraPermissionSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
