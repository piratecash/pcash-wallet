package cash.p.terminal.ui.extensions

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.ImageSource
import kotlinx.parcelize.Parcelize
import java.util.UUID

class BottomSheetSelectorMultipleSheet(val config: Config) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        BottomSheetSelectorMultiple(
            config = config,
            onItemsSelected = { selected ->
                navigation.setResult(this, Result(selected))
            },
            onCloseClick = navigation::navigateUpSafely,
        )
    }

    data class Config(
        val icon: ImageSource,
        val title: String,
        val selectedIndexes: List<Int>,
        val viewItems: List<BottomSheetSelectorViewItem>,
        val descriptionTitle: String? = null,
        val description: String? = null,
        val allowEmpty: Boolean = false
    ) {
        val uuid = UUID.randomUUID().toString()
    }

    @Parcelize
    data class Result(val selectedIndexes: List<Int>) : Parcelable
}

data class BottomSheetSelectorViewItem(
    val title: String,
    val subtitle: String,
    val copyableString: String? = null,
    val icon: String? = null,
    val header: String? = null
)
