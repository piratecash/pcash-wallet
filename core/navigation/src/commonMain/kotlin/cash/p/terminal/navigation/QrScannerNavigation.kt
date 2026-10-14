package cash.p.terminal.navigation

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

fun HSNavigation.openQrScanner(
    appPages: AppPages,
    title: String,
    showPasteButton: Boolean = false,
    allowGalleryWithoutPremium: Boolean = false,
    onResult: (String) -> Unit,
) {
    slideFromBottomForResult<QrScannerResult>(
        appPages.qrScanner(QrScannerInput(title, showPasteButton, allowGalleryWithoutPremium))
    ) { result ->
        onResult(result.text)
    }
}
