package cash.p.terminal.navigation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class QrScannerInput(
    val title: String,
    val showPasteButton: Boolean = false,
    val allowGalleryWithoutPremium: Boolean = false
) : Parcelable

@Parcelize
data class QrScannerResult(val text: String) : Parcelable

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
