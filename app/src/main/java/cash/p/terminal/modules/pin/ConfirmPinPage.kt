package cash.p.terminal.modules.pin

import android.os.Parcelable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.pin.ui.PinConfirm
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Unlock_EnterPasscode

class ConfirmPinPage(val input: InputConfirm?) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PinConfirm(
            title = stringResource(input?.description ?: Res.string.Unlock_EnterPasscode),
            pinType = input?.pinType ?: PinType.REGULAR,
            onSuccess = {
                navigation.setResult(this, Result(true))
                navigation.navigateUpSafely()
            },
            onCancel = {
                navigation.navigateUpSafely()
            }
        )
    }

    data class InputConfirm(val description: StringResource, val pinType: PinType)

    @Parcelize
    data class Result(val success: Boolean) : Parcelable
}
