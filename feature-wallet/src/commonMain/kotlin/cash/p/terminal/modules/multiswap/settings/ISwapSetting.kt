package cash.p.terminal.modules.multiswap.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import org.jetbrains.compose.resources.StringResource

interface ISwapSetting {
    val id: String
    val titleRes: StringResource?
        get() = null

    @Composable
    fun GetContent(
        navigation: HSNavigation,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    )

    fun LazyListScope.addContentItems(
        navigation: HSNavigation,
        value: Any?,
        onError: (Throwable?) -> Unit,
        onValueChange: (Any?) -> Unit
    ) {
        item(key = id) {
            GetContent(
                navigation = navigation,
                onError = onError,
                onValueChange = onValueChange,
            )
        }
    }
}
