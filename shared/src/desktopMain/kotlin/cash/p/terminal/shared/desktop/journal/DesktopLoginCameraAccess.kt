package cash.p.terminal.shared.desktop.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.feature.logging.settings.LoginCameraAccess
import cash.p.terminal.feature.logging.settings.LoginCameraPermission
import cash.p.terminal.shared.desktop.qrscanner.DesktopOs
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILoggingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop has no permission dialog of its own: turning a selfie switch on takes one photo to find out whether the
 * camera answers, which is also when macOS asks the user.
 */
class DesktopLoginCameraAccess(
    private val vault: DesktopVault,
    private val camera: SelfieCamera,
    private val loggingSettings: ILoggingSettings,
    private val userManager: UserManager,
    private val dispatcherProvider: DispatcherProvider,
) : LoginCameraAccess {

    @Composable
    override fun rememberPermission(onResult: (granted: Boolean) -> Unit): LoginCameraPermission {
        val scope = rememberCoroutineScope()
        val currentOnResult by rememberUpdatedState(onResult)
        return remember {
            val selfieInUse = loggingSettings.anySelfieEnabled(userManager.getUserLevel())
            DesktopLoginCameraPermission(
                scope = scope,
                initiallyGranted = selfieInUse && !vault.selfieProblem(),
                onResult = { granted -> currentOnResult(granted) },
            )
        }
    }

    private suspend fun probe(): Boolean = withContext(dispatcherProvider.io) {
        camera.captureFor(vault.openSession()?.rootSafe) != null
    }

    private inner class DesktopLoginCameraPermission(
        private val scope: CoroutineScope,
        initiallyGranted: Boolean,
        private val onResult: (Boolean) -> Unit,
    ) : LoginCameraPermission {
        override var isGranted by mutableStateOf(initiallyGranted)
            private set

        override val shouldShowRationale = false

        override val canOpenSettings = DesktopOs.current == DesktopOs.MacOs

        override fun request() {
            scope.launch {
                isGranted = probe()
                onResult(isGranted)
            }
        }

        override fun openSettings() {
            tryOrNull { ProcessBuilder("open", MAC_CAMERA_PRIVACY_PANE).start() }
        }
    }
}

private const val MAC_CAMERA_PRIVACY_PANE = "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera"
