package cash.p.terminal.ui_compose.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialColorSchemeTest {

    @Test
    fun toMaterialColorScheme_lightPalette_mapsSourceTokens() = assertMapsSourceTokens(lightPalette)

    @Test
    fun toMaterialColorScheme_darkPalette_mapsSourceTokens() = assertMapsSourceTokens(darkPalette)

    private fun assertMapsSourceTokens(colors: Colors) {
        val scheme = colors.toMaterialColorScheme()

        assertEquals(colors.brandDefault, scheme.primary)
        assertEquals(colors.statusSuccess, scheme.secondary)
        assertEquals(colors.backgroundBase, scheme.background)
        assertEquals(colors.surfacePrimary, scheme.surface)
        assertEquals(colors.surfaceElevated, scheme.surfaceContainerHigh)
        assertEquals(colors.statusError, scheme.error)
        assertEquals(colors.borderDefault, scheme.outline)
        assertEquals(colors.borderDivider, scheme.outlineVariant)
        assertEquals(colors.backgroundOverlay, scheme.scrim)
        assertEquals(colors.textPrimary, scheme.onSurface)
    }
}
