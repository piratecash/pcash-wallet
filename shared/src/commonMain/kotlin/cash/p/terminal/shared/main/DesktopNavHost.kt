package cash.p.terminal.shared.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import cash.p.terminal.navigation.BottomSheetColors
import cash.p.terminal.navigation.BottomSheetSceneStrategy
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.navigation.ReportPageResumed
import cash.p.terminal.navigation.rememberSharedViewModelStoreNavEntryDecorator
import cash.p.terminal.ui_compose.components.AppSnackbar
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource

private const val SNACKBAR_DURATION_MS = 3_000L

@Composable
fun DesktopNavHost(
    navigation: HSNavigation,
    hudMessenger: DesktopHudMessenger,
    urlOpener: UrlOpener,
    modifier: Modifier = Modifier,
) {
    val colors = BottomSheetColors(
        container = ComposeAppTheme.colors.transparent,
        scrim = ComposeAppTheme.colors.backgroundOverlay,
    )
    val sheetStrategy = remember(colors) {
        BottomSheetSceneStrategy<HSPage>(colors, mutableStateOf(false))
    }
    val navigationEventOwner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    }
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventOwner,
        LocalHostLifecycleOwner provides LocalLifecycleOwner.current,
        LocalHudMessenger provides hudMessenger,
        LocalUrlOpener provides urlOpener,
    ) {
        Box(modifier.fillMaxSize()) {
            NavDisplay(
                backStack = navigation.backStack,
                onBack = { navigation.navigateUp() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberSharedViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategies = listOf(sheetStrategy),
                entryProvider = { page ->
                    NavEntry(page, page.contentKey(), page.metadata()) {
                        ReportPageResumed(navigation, page)
                        page.GetContent(navigation)
                    }
                },
            )
            HudSnackbar(hudMessenger, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun HudSnackbar(hudMessenger: DesktopHudMessenger, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<HudMessage?>(null) }
    LaunchedEffect(hudMessenger) {
        hudMessenger.messages.collectLatest {
            message = it
            delay(SNACKBAR_DURATION_MS)
            message = null
        }
    }
    message?.let {
        AppSnackbar(
            text = it.text,
            variant = it.variant,
            icon = it.icon?.let { icon -> painterResource(icon) },
            modifier = modifier.padding(16.dp),
        )
    }
}
