package cash.p.terminal.ui_compose.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Stable
class Colors(
    jacob: Color,
    yellow: Color,
    remus: Color,
    lucian: Color,
    tyler: Color,
    bran: Color,
    leah: Color,
    claude: Color,
    lawrence: Color,
    navigation: Color,
    actionBackground: Color,
    actionBorder: Color,
    divider: Color,
    filterBackground: Color,
    filterSettingsBackground: Color,
    filterBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    textSecondaryDimmed: Color,
    borderAccentSubtle: Color,
    filterText: Color,
    jeremy: Color,
    laguna: Color,
    purple: Color,
    raina: Color,
    andy: Color,
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
    val grey = Grey
    val grey50 = Grey50
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
    var jacob by mutableStateOf(jacob)
        private set
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
    var leah by mutableStateOf(leah)
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
    var filterSettingsBackground by mutableStateOf(filterSettingsBackground)
        private set
    var filterBorder by mutableStateOf(filterBorder)
        private set
    var textPrimary by mutableStateOf(textPrimary)
        private set
    var textSecondary by mutableStateOf(textSecondary)
        private set
    var textSecondaryDimmed by mutableStateOf(textSecondaryDimmed)
        private set
    var borderAccentSubtle by mutableStateOf(borderAccentSubtle)
        private set
    var filterText by mutableStateOf(filterText)
        private set
    var jeremy by mutableStateOf(jeremy)
        private set
    var laguna by mutableStateOf(laguna)
        private set
    var purple by mutableStateOf(purple)
        private set
    var raina by mutableStateOf(raina)
        private set
    var andy by mutableStateOf(andy)
        private set
    var blade by mutableStateOf(blade)
        private set
    var midnight by mutableStateOf(midnight)
        private set
    var modalOverlay by mutableStateOf(modalOverlay)
        private set

    fun update(other: Colors) {
        jacob = other.jacob
        yellow = other.yellow
        remus = other.remus
        lucian = other.lucian
        tyler = other.tyler
        bran = other.bran
        leah = other.leah
        claude = other.claude
        lawrence = other.lawrence
        navigation = other.navigation
        actionBackground = other.actionBackground
        actionBorder = other.actionBorder
        divider = other.divider
        filterBackground = other.filterBackground
        filterSettingsBackground = other.filterSettingsBackground
        filterBorder = other.filterBorder
        textPrimary = other.textPrimary
        textSecondary = other.textSecondary
        textSecondaryDimmed = other.textSecondaryDimmed
        borderAccentSubtle = other.borderAccentSubtle
        filterText = other.filterText
        jeremy = other.jeremy
        laguna = other.laguna
        purple = other.purple
        raina = other.raina
        andy = other.andy
        blade = other.blade
        midnight = other.midnight
        modalOverlay = other.modalOverlay
    }

    fun copy(): Colors = Colors(
        jacob = jacob,
        yellow = yellow,
        remus = remus,
        lucian = lucian,
        tyler = tyler,
        bran = bran,
        leah = leah,
        claude = claude,
        lawrence = lawrence,
        navigation = navigation,
        actionBackground = actionBackground,
        actionBorder = actionBorder,
        divider = divider,
        filterBackground = filterBackground,
        filterSettingsBackground = filterSettingsBackground,
        filterBorder = filterBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textSecondaryDimmed = textSecondaryDimmed,
        borderAccentSubtle = borderAccentSubtle,
        filterText = filterText,
        jeremy = jeremy,
        laguna = laguna,
        purple = purple,
        raina = raina,
        andy = andy,
        blade = blade,
        midnight = midnight,
        modalOverlay = modalOverlay
    )
}
