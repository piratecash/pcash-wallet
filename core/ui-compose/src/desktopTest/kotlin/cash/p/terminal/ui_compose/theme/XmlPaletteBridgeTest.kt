package cash.p.terminal.ui_compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class XmlPaletteBridgeTest {

    private val tokenByXmlName: Map<String, (Colors) -> Color> = mapOf(
        "background_base" to { it.backgroundBase },
        "brand_default" to { it.brandDefault },
        "icon_secondary" to { it.iconSecondary },
        "icon_primary" to { it.iconPrimary },
        "text_primary" to { it.textPrimary },
        "status_success" to { it.statusSuccess },
        "status_error" to { it.statusError },
        "icon_disabled" to { it.iconDisabled },
    )

    private val nonPaletteXmlNames = setOf("black", "white_50", "light_grey")

    @Test
    fun lightColorsXml_everyEntry_equalsLightPaletteToken() =
        assertBridgeMatches("values", lightPalette)

    @Test
    fun nightColorsXml_everyEntry_equalsDarkPaletteToken() =
        assertBridgeMatches("values-night", darkPalette)

    private fun assertBridgeMatches(valuesDir: String, palette: Colors) {
        val entries = readColorEntries(valuesDir)

        val unmapped = entries.keys - tokenByXmlName.keys - nonPaletteXmlNames
        assertEquals(emptySet(), unmapped, "colors.xml entries without a palette mapping")
        assertEquals(tokenByXmlName.keys, entries.keys.intersect(tokenByXmlName.keys), "bridge entries missing in $valuesDir")

        tokenByXmlName.forEach { (name, token) ->
            assertEquals(token(palette).toArgb(), entries.getValue(name), "$valuesDir/$name")
        }
    }

    private fun readColorEntries(valuesDir: String): Map<String, Int> {
        val file = File("../resources/src/androidMain/res/$valuesDir/colors.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file).getElementsByTagName("color")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to parseArgb(node.textContent.trim())
        }
    }

    private fun parseArgb(hex: String): Int {
        val digits = hex.removePrefix("#")
        val argb = if (digits.length == 6) "FF$digits" else digits
        return argb.toLong(16).toInt()
    }
}
