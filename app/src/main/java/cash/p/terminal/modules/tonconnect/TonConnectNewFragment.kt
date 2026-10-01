package cash.p.terminal.modules.tonconnect

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.setNavigationResultX
import cash.p.terminal.ui_compose.BaseComposeFragment
import kotlinx.parcelize.Parcelize

class TonConnectNewFragment : BaseComposeFragment() {
    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Input>(navController) { input ->
            TonConnectNewScreen(
                navController = navController,
                uri = input.uri,
                onResult = { approved ->
                    navController.setNavigationResultX(Result(approved))
                    navController.popBackStackSafely()
                },
            )
        }
    }

    @Parcelize
    data class Input(val uri: String) : Parcelable

    @Parcelize
    data class Result(val approved: Boolean) : Parcelable
}
