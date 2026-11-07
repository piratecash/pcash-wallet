package cash.p.terminal.modules.qrscanner

interface QrCodeImageDecoder {
    /** [imageRef] is a platform image reference (Android: content Uri string, desktop: file path). */
    suspend fun decode(imageRef: String): String?
}
