package cash.p.terminal.modules.pin

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.modules.pin.ui.PinSet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize
import cash.p.terminal.navigation.navigateUpSafely

class SetPinPage(val input: Input?) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PinSet(
            title = stringResource(R.string.PinSet_Title),
            description = stringResource(input?.descriptionResId ?: R.string.PinSet_Info),
            pinType = input?.pinType ?: PinType.REGULAR,
            dismissWithSuccess = {
                navigation.setResult(this, Result(true))
                navigation.navigateUpSafely()
            },
            onBackPress = { navigation.navigateUpSafely() }
        )
    }

    @Parcelize
    data class Input(val descriptionResId: Int, val pinType: PinType) : Parcelable

    @Parcelize
    data class Result(val success: Boolean) : Parcelable
}
