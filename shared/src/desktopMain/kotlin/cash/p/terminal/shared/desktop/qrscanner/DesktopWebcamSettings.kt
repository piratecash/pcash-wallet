package cash.p.terminal.shared.desktop.qrscanner

import io.horizontalsystems.core.DesktopSettingsStore

class DesktopWebcamSettings(private val store: DesktopSettingsStore) {
    var cameraName: String?
        get() = store.string(CAMERA_NAME)
        set(value) = store.setString(CAMERA_NAME, value)

    private companion object {
        const val CAMERA_NAME = "qr_scanner_camera_name"
    }
}
