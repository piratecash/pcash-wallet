package cash.p.terminal.modules.market.topcoins

import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import cash.p.terminal.RecreatableContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ScrollToTopOnFilterChangeDesktopTest {

    @Test
    fun recreation_scrolledList_keepsItsPosition() = runComposeUiTest {
        var alive by mutableStateOf(true)
        var listState: LazyListState? = null
        setContent {
            RecreatableContent(alive) { CoinListDouble(FILTER) { listState = it } }
        }
        onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)
        runOnIdle { assertEquals(SCROLLED_TO, listState?.firstVisibleItemIndex) }

        runOnIdle { alive = false }
        runOnIdle { alive = true }
        waitForIdle()

        assertEquals(SCROLLED_TO, listState?.firstVisibleItemIndex)
    }

    /** A recreation that loses the filter — Android process death — shows another list. */
    @Test
    fun recreation_withAnotherFilter_scrollsToTheTop() = runComposeUiTest {
        var alive by mutableStateOf(true)
        var filter by mutableStateOf(FILTER)
        var listState: LazyListState? = null
        setContent {
            RecreatableContent(alive) { CoinListDouble(filter) { listState = it } }
        }
        onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)

        runOnIdle { alive = false }
        runOnIdle { filter = OTHER_FILTER }
        runOnIdle { alive = true }
        waitForIdle()

        assertEquals(0, listState?.firstVisibleItemIndex)
    }

    @Test
    fun filterChange_scrolledList_returnsToTheTop() = runComposeUiTest {
        var filter by mutableStateOf(FILTER)
        var listState: LazyListState? = null
        setContent {
            RecreatableContent(alive = true) { CoinListDouble(filter) { listState = it } }
        }
        onNode(hasScrollToIndexAction()).performScrollToIndex(SCROLLED_TO)

        runOnIdle { filter = OTHER_FILTER }
        waitForIdle()

        assertEquals(0, listState?.firstVisibleItemIndex)
    }

    @Composable
    private fun CoinListDouble(filter: String, onState: (LazyListState) -> Unit) {
        val state = rememberLazyListState()
        onState(state)
        ScrollToTopOnFilterChange(filter = filter, listState = state)
        LazyColumn(modifier = Modifier.requiredSize(VIEWPORT), state = state) {
            items(ITEM_COUNT) { index ->
                BasicText(text = "item-$index", modifier = Modifier.requiredHeight(ITEM_HEIGHT))
            }
        }
    }

    private companion object {
        const val FILTER = "TopGainers|Top100|OneDay"
        const val OTHER_FILTER = "TopLosers|Top100|OneDay"
        const val ITEM_COUNT = 100
        const val SCROLLED_TO = 40
        val VIEWPORT = 200.dp
        val ITEM_HEIGHT = 20.dp
    }
}
