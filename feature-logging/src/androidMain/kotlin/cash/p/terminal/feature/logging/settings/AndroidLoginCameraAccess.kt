package cash.p.terminal.feature.logging.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

internal class AndroidLoginCameraAccess : LoginCameraAccess {

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    override fun rememberPermission(onResult: (granted: Boolean) -> Unit): LoginCameraPermission {
        val context = LocalContext.current
        val state = rememberPermissionState(Manifest.permission.CAMERA, onResult)
        return remember(state, context) { AccompanistLoginCameraPermission(state, context) }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private class AccompanistLoginCameraPermission(
    private val state: PermissionState,
    private val context: Context
) : LoginCameraPermission {

    override val isGranted get() = state.status == PermissionStatus.Granted

    override val shouldShowRationale get() = state.status.shouldShowRationale

    override fun request() = state.launchPermissionRequest()

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
}
