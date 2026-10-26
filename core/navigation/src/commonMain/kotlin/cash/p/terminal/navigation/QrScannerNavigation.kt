package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
data class QrScannerInput(
    val title: String,
    val showPasteButton: Boolean = false,
    val allowGalleryWithoutPremium: Boolean = false
) : CommonParcelable

@CommonParcelize
data class QrScannerResult(val text: String) : CommonParcelable

class QrScannerLauncher internal constructor(
    private val launcher: ResultLauncher<Unit>,
    private val appPages: AppPages,
) {
    fun open(title: String, showPasteButton: Boolean = false, allowGalleryWithoutPremium: Boolean = false) =
        launcher.slideFromBottom(appPages.qrScanner(QrScannerInput(title, showPasteButton, allowGalleryWithoutPremium)))
}

/** Remember at the screen root, see [rememberResultLauncher]. */
@Composable
fun HSNavigation.rememberQrScannerLauncher(appPages: AppPages, onResult: (String) -> Unit): QrScannerLauncher {
    val launcher = rememberResultLauncher<QrScannerResult> { onResult(it.text) }
    return remember(launcher, appPages) { QrScannerLauncher(launcher, appPages) }
}
