package cash.p.terminal.modules.settings.addresschecker

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.settings.addresschecker.ui.UnifiedAddressCheckScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import kotlinx.parcelize.Parcelize

class AddressCheckPage(val input: Input? = null) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        UnifiedAddressCheckScreen(
            navigation = navigation,
            initialAddress = input?.initialAddress,
            onClose = navigation::navigateUpSafely,
            onPremiumClick = {
                navigation.slideFromBottom(AboutPremiumPage(null))
            }
        )
    }

    @Parcelize
    data class Input(val initialAddress: String? = null) : Parcelable
}
