package cash.p.terminal.screenshots

import android.app.Application
import cash.p.terminal.modules.multiswap.SwapOutputInputPreview
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/**
 * Local-only design check. Run with:
 * `./gradlew :app:recordRoborazziDebug -Pscreenshots --tests "*SwapOutputPreviewScreenshotTest"`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel5,
)
class SwapOutputPreviewScreenshotTest {

    @Test
    fun snapshot_exactIn_rendersOutputField() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/SwapOutputInputExactInPreview.png",
        ) {
            SwapOutputInputPreview(
                amount = BigDecimal("0.12345678"),
                fiatAmount = BigDecimal("20"),
                fiatPriceImpact = BigDecimal("-1.62"),
            )
        }
    }

    @Test
    fun snapshot_exactOut_rendersOutputField() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/SwapOutputInputExactOutPreview.png",
        ) {
            SwapOutputInputPreview(
                amount = BigDecimal("100"),
                fiatAmount = BigDecimal("123456789.12"),
                fiatPriceImpact = BigDecimal("1.23"),
            )
        }
    }

    @Test
    fun snapshot_noPriceImpact_rendersExpandedFiatField() {
        captureRoboImage(
            filePath = "build/outputs/roborazzi/SwapOutputInputNoPriceImpactPreview.png",
        ) {
            SwapOutputInputPreview(
                amount = BigDecimal("100"),
                fiatAmount = null,
                fiatPriceImpact = null,
            )
        }
    }
}
