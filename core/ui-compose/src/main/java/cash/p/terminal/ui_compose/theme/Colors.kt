package cash.p.terminal.ui_compose.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Stable
class Colors(
    yellow: Color,
    remus: Color,
    lucian: Color,
    tyler: Color,
    bran: Color,
    claude: Color,
    lawrence: Color,
    navigation: Color,
    actionBackground: Color,
    actionBorder: Color,
    divider: Color,
    filterBackground: Color,
    filterBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    textSecondaryDimmed: Color,
    textDisabled: Color,
    iconPrimary: Color,
    iconSecondary: Color,
    iconDisabled: Color,
    brand: Color,
    borderAccentSubtle: Color,
    filterText: Color,
    jeremy: Color,
    purple: Color,
    raina: Color,
    blade: Color,
    midnight: Color,
    modalOverlay: Color
) {

    //base colors
    val transparent = Color.Transparent
    val dark = Dark
    val light = Light
    val white = Color.White
    val black50 = Black50
    val issykBlue = Color(0xFF3372FF)
    val lightGrey = LightGrey
    val steelLight = SteelLight
    val steelDark = SteelDark
    val steel10 = Steel10
    val steel20 = Steel20
    val yellow50 = Yellow50
    val yellow20 = Yellow20
    val green20 = Green20

    val yellowD = YellowD
    val yellowL = YellowL
    val greenD = GreenD
    val greenL = GreenL
    val green50 = Green50
    val redD = RedD
    val redL = RedL
    val elenaD = Color(0xFF6E7899)
    val red50 = Red50
    val red20 = Red20

    //themed colors
    var yellow by mutableStateOf(yellow)
        private set
    var remus by mutableStateOf(remus)
        private set
    var lucian by mutableStateOf(lucian)
        private set
    var tyler by mutableStateOf(tyler)
        private set
    var bran by mutableStateOf(bran)
        private set
    var claude by mutableStateOf(claude)
        private set
    var lawrence by mutableStateOf(lawrence)
        private set
    var navigation by mutableStateOf(navigation)
        private set
    var actionBackground by mutableStateOf(actionBackground)
        private set
    var actionBorder by mutableStateOf(actionBorder)
        private set
    var divider by mutableStateOf(divider)
        private set
    var filterBackground by mutableStateOf(filterBackground)
        private set
    var filterBorder by mutableStateOf(filterBorder)
        private set
    var textPrimary by mutableStateOf(textPrimary)
        private set
    var textSecondary by mutableStateOf(textSecondary)
        private set
    var textSecondaryDimmed by mutableStateOf(textSecondaryDimmed)
        private set
    var textDisabled by mutableStateOf(textDisabled)
        private set
    var iconPrimary by mutableStateOf(iconPrimary)
        private set
    var iconSecondary by mutableStateOf(iconSecondary)
        private set
    var iconDisabled by mutableStateOf(iconDisabled)
        private set
    var brand by mutableStateOf(brand)
        private set
    var borderAccentSubtle by mutableStateOf(borderAccentSubtle)
        private set
    var filterText by mutableStateOf(filterText)
        private set
    var jeremy by mutableStateOf(jeremy)
        private set
    var purple by mutableStateOf(purple)
        private set
    var raina by mutableStateOf(raina)
        private set
    var blade by mutableStateOf(blade)
        private set
    var midnight by mutableStateOf(midnight)
        private set
    var modalOverlay by mutableStateOf(modalOverlay)
        private set

    fun update(other: Colors) {
        yellow = other.yellow
        remus = other.remus
        lucian = other.lucian
        tyler = other.tyler
        bran = other.bran
        claude = other.claude
        lawrence = other.lawrence
        navigation = other.navigation
        actionBackground = other.actionBackground
        actionBorder = other.actionBorder
        divider = other.divider
        filterBackground = other.filterBackground
        filterBorder = other.filterBorder
        textPrimary = other.textPrimary
        textSecondary = other.textSecondary
        textSecondaryDimmed = other.textSecondaryDimmed
        textDisabled = other.textDisabled
        iconPrimary = other.iconPrimary
        iconSecondary = other.iconSecondary
        iconDisabled = other.iconDisabled
        brand = other.brand
        borderAccentSubtle = other.borderAccentSubtle
        filterText = other.filterText
        jeremy = other.jeremy
        purple = other.purple
        raina = other.raina
        blade = other.blade
        midnight = other.midnight
        modalOverlay = other.modalOverlay
    }

    fun copy(): Colors = Colors(
        yellow = yellow,
        remus = remus,
        lucian = lucian,
        tyler = tyler,
        bran = bran,
        claude = claude,
        lawrence = lawrence,
        navigation = navigation,
        actionBackground = actionBackground,
        actionBorder = actionBorder,
        divider = divider,
        filterBackground = filterBackground,
        filterBorder = filterBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textSecondaryDimmed = textSecondaryDimmed,
        textDisabled = textDisabled,
        iconPrimary = iconPrimary,
        iconSecondary = iconSecondary,
        iconDisabled = iconDisabled,
        brand = brand,
        borderAccentSubtle = borderAccentSubtle,
        filterText = filterText,
        jeremy = jeremy,
        purple = purple,
        raina = raina,
        blade = blade,
        midnight = midnight,
        modalOverlay = modalOverlay
    )
}
