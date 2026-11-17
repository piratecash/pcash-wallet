package cash.p.terminal.feature.logging.settings

import androidx.compose.runtime.Composable

interface LoginCameraPermission {
    val isGranted: Boolean
    val shouldShowRationale: Boolean

    /** False where no system page for the permission exists; the dialog then offers no such button. */
    val canOpenSettings: Boolean get() = true

    fun request()
    fun openSettings()
}

interface LoginCameraAccess {
    /** [onResult] reports the outcome of every [LoginCameraPermission.request]. */
    @Composable
    fun rememberPermission(onResult: (granted: Boolean) -> Unit): LoginCameraPermission
}
