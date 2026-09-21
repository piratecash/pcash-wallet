package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.Colors
import cash.p.terminal.ui_compose.theme.Typography
import cash.p.terminal.ui_compose.theme.darkPalette
import cash.p.terminal.ui_compose.theme.lightPalette
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsComponentsDesktopTest {

    @Test
    fun settingsComponents_lightDarkLight_useSharedMaterialTheme() = runComposeUiTest {
        var darkTheme by mutableStateOf(false)
        var renderedTheme = RenderedTheme()
        var clickCount = 0

        setContent {
            MaterialTheme(
                colorScheme = if (darkTheme) lightColorScheme() else darkColorScheme(),
            ) {
                ComposeAppTheme(darkTheme = darkTheme) {
                    val colors = ComposeAppTheme.colors
                    val typography = ComposeAppTheme.typography
                    val materialColors = MaterialTheme.colorScheme
                    val materialTypography = MaterialTheme.typography
                    renderedTheme = RenderedTheme(
                        sharedBackground = colors.lawrence,
                        fontScale = LocalDensity.current.fontScale,
                        background = materialColors.background,
                        onBackground = materialColors.onBackground,
                        surface = materialColors.surface,
                        onSurface = materialColors.onSurface,
                        typography = MaterialTypography(
                            bodyLarge = materialTypography.bodyLarge,
                            bodyMedium = materialTypography.bodyMedium,
                            titleLarge = materialTypography.titleLarge,
                            titleMedium = materialTypography.titleMedium,
                            titleSmall = materialTypography.titleSmall,
                            labelLarge = materialTypography.labelLarge,
                            labelMedium = materialTypography.labelMedium,
                            labelSmall = materialTypography.labelSmall,
                        ),
                    )

                    Column {
                        MaterialConsumers(
                            onBackgroundContent = {
                                renderedTheme = renderedTheme.copy(backgroundContent = it)
                            },
                            onSurfaceContent = { color, style ->
                                renderedTheme = renderedTheme.copy(
                                    surfaceContent = color,
                                    defaultTextStyle = style,
                                )
                            },
                        )
                        CellUniversalLawrenceSection(
                            listOf(
                                {
                                    HsSettingCell(
                                        title = "Clickable row",
                                        arrowPainter = ColorPainter(Color.Gray),
                                        leadingPainter = ColorPainter(Color.Blue),
                                        counterBadge = "7",
                                        newBadgeText = "NEW",
                                        alertPainter = ColorPainter(Color.Red),
                                        onClick = { clickCount++ },
                                    )
                                },
                                {
                                    HsSettingCell(
                                        title = "Value row",
                                        arrowPainter = ColorPainter(Color.Gray),
                                        value = "Enabled",
                                    )
                                },
                            ),
                        )
                        SectionUniversalLawrence {
                            HsSettingCell(title = "Plain section", arrowPainter = null)
                        }
                        PremiumHeader(
                            starPainter = ColorPainter(Color.Yellow),
                            text = "Premium section",
                        )
                        SectionPremiumUniversalLawrence {
                            HsSettingCell(title = "Premium row", arrowPainter = null)
                        }
                    }
                }
            }
        }

        fun assertTheme(colors: Colors, typography: Typography) {
            waitForIdle()
            assertEquals(colors.lawrence, renderedTheme.sharedBackground)
            assertEquals(1f, renderedTheme.fontScale)
            assertEquals(colors.tyler, renderedTheme.background)
            assertEquals(colors.leah, renderedTheme.onBackground)
            assertEquals(colors.lawrence, renderedTheme.surface)
            assertEquals(colors.leah, renderedTheme.onSurface)
            assertEquals(colors.leah, renderedTheme.backgroundContent)
            assertEquals(colors.leah, renderedTheme.surfaceContent)
            assertEquals(colors.tyler, onNodeWithTag(BACKGROUND_TAG).captureToImage().toPixelMap()[0, 0])
            assertEquals(colors.lawrence, onNodeWithTag(SURFACE_TAG).captureToImage().toPixelMap()[0, 0])
            assertEquals(typography.body, renderedTheme.typography.bodyLarge)
            assertEquals(typography.subhead2, renderedTheme.typography.bodyMedium)
            assertEquals(typography.title3, renderedTheme.typography.titleLarge)
            assertEquals(typography.headline2, renderedTheme.typography.titleMedium)
            assertEquals(typography.subhead1, renderedTheme.typography.titleSmall)
            assertEquals(typography.subhead1, renderedTheme.typography.labelLarge)
            assertEquals(typography.captionSB, renderedTheme.typography.labelMedium)
            assertEquals(typography.microSB, renderedTheme.typography.labelSmall)
            assertEquals(typography.body, renderedTheme.defaultTextStyle)
        }

        assertTheme(lightPalette, Typography())
        onNodeWithText("Material consumer").assertExists()
        onNodeWithText("7").assertExists()
        onNodeWithText("NEW").assertExists()
        onNodeWithText("Enabled").assertExists()
        onNodeWithText("Plain section").assertExists()
        onNodeWithText("Premium section").assertExists()
        onNodeWithText("Premium row").assertExists()
        onNodeWithText("Clickable row").performClick()
        runOnIdle {
            assertEquals(1, clickCount)
            darkTheme = true
        }

        assertTheme(darkPalette, Typography())
        assertEquals(Color.White, lightPalette.lawrence)
        runOnIdle { darkTheme = false }

        assertTheme(lightPalette, Typography())
    }

    @Composable
    private fun MaterialConsumers(
        onBackgroundContent: (Color) -> Unit,
        onSurfaceContent: (Color, TextStyle) -> Unit,
    ) {
        Surface(
            modifier = Modifier.size(32.dp).testTag(BACKGROUND_TAG),
            color = MaterialTheme.colorScheme.background,
        ) {
            onBackgroundContent(LocalContentColor.current)
        }
        Surface(modifier = Modifier.size(64.dp).testTag(SURFACE_TAG)) {
            onSurfaceContent(LocalContentColor.current, LocalTextStyle.current)
            Text(text = "Material consumer", modifier = Modifier.padding(8.dp))
        }
    }

    private data class RenderedTheme(
        val sharedBackground: Color = Color.Unspecified,
        val fontScale: Float = 0f,
        val background: Color = Color.Unspecified,
        val onBackground: Color = Color.Unspecified,
        val surface: Color = Color.Unspecified,
        val onSurface: Color = Color.Unspecified,
        val backgroundContent: Color = Color.Unspecified,
        val surfaceContent: Color = Color.Unspecified,
        val typography: MaterialTypography = MaterialTypography(),
        val defaultTextStyle: TextStyle = TextStyle(),
    )

    private data class MaterialTypography(
        val bodyLarge: TextStyle = TextStyle(),
        val bodyMedium: TextStyle = TextStyle(),
        val titleLarge: TextStyle = TextStyle(),
        val titleMedium: TextStyle = TextStyle(),
        val titleSmall: TextStyle = TextStyle(),
        val labelLarge: TextStyle = TextStyle(),
        val labelMedium: TextStyle = TextStyle(),
        val labelSmall: TextStyle = TextStyle(),
    )

    private companion object {
        const val BACKGROUND_TAG = "material-background"
        const val SURFACE_TAG = "material-surface"
    }
}
