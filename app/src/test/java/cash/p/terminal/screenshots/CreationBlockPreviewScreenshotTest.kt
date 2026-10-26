package cash.p.terminal.screenshots

import android.app.Application
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner

/**
 * Renders the Creation Block screen to a PNG on the JVM (Robolectric, no emulator) for local
 * design review against the ART-385 mockup.
 *
 * Record: `./gradlew :app:recordRoborazziDebug --tests "*CreationBlockPreviewScreenshotTest"`
 * Output: `app/build/outputs/roborazzi/CreationBlockScreen.png`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class CreationBlockPreviewScreenshotTest {

    @Test
    fun snapshot() {
        val preview = AndroidComposablePreviewScanner()
            .scanPackageTrees("cash.p.terminal.modules.balance.token.creationblock")
            .getPreviews()
            .single { it.methodName == "CreationBlockScreenPreview" }
        captureRoboImage(filePath = "build/outputs/roborazzi/CreationBlockScreen.png") {
            preview()
        }
    }
}
