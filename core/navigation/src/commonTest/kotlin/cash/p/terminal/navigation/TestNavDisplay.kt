package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/** NavDisplay wired like the app's Nav3Host: same decorators and sheet strategy. */
@Composable
internal fun TestNavDisplay(navigation: HSNavigation) {
    NavDisplay(
        backStack = navigation.backStack,
        onBack = { navigation.navigateUp() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberSharedViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(
            remember {
                BottomSheetSceneStrategy(
                    colors = BottomSheetColors(Color.Unspecified, Color.Unspecified),
                    isLocked = mutableStateOf(false),
                )
            }
        ),
        entryProvider = { key ->
            NavEntry(key, key.contentKey(), key.metadata()) {
                ReportPageResumed(navigation, key)
                key.GetContent(navigation)
            }
        },
    )
}
