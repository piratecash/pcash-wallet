package cash.p.terminal.modules.pin

import android.os.Parcelable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.pin.ui.PinSet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_Info
import cash.p.terminal.resources.PinSet_Title

class SetPinPage(val input: Input?) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PinSet(
            title = stringResource(Res.string.PinSet_Title),
            description = stringResource(input?.description ?: Res.string.PinSet_Info),
            pinType = input?.pinType ?: PinType.REGULAR,
            dismissWithSuccess = {
                navigation.setResult(this, Result(true))
                navigation.navigateUpSafely()
            },
            onBackPress = { navigation.navigateUpSafely() }
        )
    }

    data class Input(val description: StringResource, val pinType: PinType)

    @Parcelize
    data class Result(val success: Boolean) : Parcelable
}
