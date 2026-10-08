package cash.p.terminal.feature.miniapp.ui.miniapp

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import cash.p.terminal.feature.miniapp.R
import cash.p.terminal.feature.miniapp.ui.GETGEMS_COLLECTION_URL
import cash.p.terminal.feature.miniapp.ui.TELEGRAM_BOT_START_URL
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class MiniAppPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<MiniAppViewModel>()
        val context = LocalContext.current
        val view = LocalView.current
        // The scanner result arrives after this page left composition, so use the host's scope.
        val hostScope = LocalHostLifecycleOwner.current.lifecycleScope
        val appPages: AppPages = koinInject()

        PageResumeEffect(
            onResume = viewModel::updateConnectionStatus,
            onPause = {}
        )

        MiniAppScreen(
            uiState = viewModel.uiState,
            onConnectionClick = {
                navigation.openQrScanner(
                    appPages = appPages,
                    title = context.getString(R.string.mini_app_connection),
                    showPasteButton = true,
                    allowGalleryWithoutPremium = true
                ) { _ ->
                    hostScope.launch {
                        delay(100)
                        HudHelper.showErrorMessage(view, R.string.invalid_qr_code)
                    }
                }
            },
            onStartEarningClick = {
                val intent = Intent(Intent.ACTION_VIEW, TELEGRAM_BOT_START_URL.toUri())
                context.startActivity(intent)
            },
            onBuyNftClick = {
                val intent = Intent(Intent.ACTION_VIEW, GETGEMS_COLLECTION_URL.toUri())
                context.startActivity(intent)
            },
            onClose = navigation::navigateUpSafely
        )
    }
}
