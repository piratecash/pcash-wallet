package cash.p.terminal.modules.walletconnect.list

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.modules.walletconnect.list.ui.WCSessionsScreen
import kotlinx.parcelize.Parcelize

class WCListFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        val input = getInput<Input>()
        WCSessionsScreen(
            navController,
            input?.deepLinkUri
        )
    }

    @Parcelize
    data class Input(val deepLinkUri: String) : Parcelable
}
