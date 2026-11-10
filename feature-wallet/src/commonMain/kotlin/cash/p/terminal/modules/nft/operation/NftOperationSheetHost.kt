package cash.p.terminal.modules.nft.operation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cash.p.terminal.navigation.BottomSheetDismissHandler
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely

/** Keeps [sheet] open while its operation runs, then closes it with [resultOf] once the outcome is final. */
@Composable
internal fun NftOperationSheetHost(
    sheet: HSBottomSheet,
    navigation: HSNavigation,
    outcome: NftOperationOutcome,
    resultOf: (NftOperationOutcome) -> Any,
    content: @Composable () -> Unit,
) {
    var dismissedWhileRunning by rememberSaveable { mutableStateOf(false) }
    val currentResultOf by rememberUpdatedState(resultOf)

    // The send runs in this sheet's view model, so a swipe must not drop it midway.
    // Material has already hidden the sheet by then, so an Idle return must still close it.
    BottomSheetDismissHandler {
        if (outcome == NftOperationOutcome.Running) dismissedWhileRunning = true else navigation.navigateUpSafely()
    }
    LaunchedEffect(outcome) {
        when (outcome) {
            is NftOperationOutcome.Done, is NftOperationOutcome.Ended -> {
                navigation.setResult(sheet, currentResultOf(outcome))
                navigation.navigateUpFrom(sheet)
            }
            NftOperationOutcome.Idle -> if (dismissedWhileRunning) navigation.navigateUpFrom(sheet)
            NftOperationOutcome.Running -> Unit
        }
    }
    content()
}
