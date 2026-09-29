package cash.p.terminal.modules.receive.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import cash.p.beam.BeamAddressType
import cash.p.terminal.modules.receive.ReceiveModule
import cash.p.terminal.modules.receive.viewmodels.ReceiveMoneroUiState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching the BEAM receiver type used to replace the whole card with a full-screen spinner, so
 * the screen collapsed to roughly half its height and then jumped back. These cases pin the part
 * of that promise the layout can actually keep: the QR panel reserves the height the selected
 * type will settle to. The address block below it still moves when the token arrives — deliberate,
 * because its height depends on how a proportional font wraps a token nobody has seen yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReceiveAddressCardLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun beamPublicOffline_loadingReservesTheSettledQrHeight() {
        val (settled, loading) = panelHeights(
            beamState(ViewState.Success, BeamAddressType.PublicOffline, SHORT_TOKEN),
            beamState(ViewState.Loading, BeamAddressType.PublicOffline, ""),
        )

        assertEquals(settled, loading)
    }

    @Test
    fun beamOffline_loadingReservesTheSettledMessageHeight() {
        // An Offline token is past QR byte-mode capacity, so this type settles to a message panel
        // of a different height than the other two. The loading state must reserve that one.
        val (settled, loading) = panelHeights(
            beamState(ViewState.Success, BeamAddressType.Offline, LONG_TOKEN),
            beamState(ViewState.Loading, BeamAddressType.Offline, ""),
        )

        assertEquals(settled, loading)
    }

    @Test
    fun beamOffline_andPublicOffline_doNotShareAPanelHeight() {
        // Guards the premise of the two cases above: if both types settled to the same height, a
        // single hard-coded height would pass them and the reservation would prove nothing.
        val (offline, publicOffline) = panelHeights(
            beamState(ViewState.Success, BeamAddressType.Offline, LONG_TOKEN),
            beamState(ViewState.Success, BeamAddressType.PublicOffline, SHORT_TOKEN),
        )

        assert(offline != publicOffline) { "Both BEAM types settled to $offline px" }
    }

    @Test
    fun beamTypeSwitch_keepsOneCardWithTheSpinnerInItsQrPanel() {
        // A cross-fade would recompose the outgoing card with the emptied uri, drawing a QR of
        // nothing beside the spinner for the length of the animation.
        val state = beamScreen(beamState(ViewState.Success, BeamAddressType.PublicOffline, SHORT_TOKEN))

        midTransitionTo(state, beamState(ViewState.Loading, BeamAddressType.MaxPrivacy, ""))

        qrPanels().assertCountEquals(1)
        qrPanels().assertAll(hasAnyDescendant(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)))
    }

    @Test
    fun beamRequestFailed_fadingCardDrawsNoQrOfTheClearedToken() {
        val state = beamScreen(beamState(ViewState.Loading, BeamAddressType.MaxPrivacy, ""))

        midTransitionTo(state, beamState(ViewState.Error(IllegalStateException()), BeamAddressType.MaxPrivacy, ""))

        qrPanels().assertCountEquals(1)
        qrPanels().assertAll(hasAnyDescendant(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)))
    }

    private fun beamScreen(initial: ReceiveModule.AbstractUiState): MutableState<ReceiveModule.AbstractUiState> {
        val state = mutableStateOf(initial)
        composeTestRule.setContent {
            ComposeAppTheme {
                ReceiveAddressScreen(
                    title = "Receive BEAM",
                    uiState = state.value,
                    setAmount = {},
                    onBackPress = {},
                    closeModule = {},
                    allowSetAmount = false,
                )
            }
        }
        return state
    }

    /** Stops the clock about a third into the cross-fade, where both sides of a key change are composed. */
    private fun midTransitionTo(
        state: MutableState<ReceiveModule.AbstractUiState>,
        next: ReceiveModule.AbstractUiState,
    ) {
        composeTestRule.mainClock.autoAdvance = false
        state.value = next
        Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(100)
    }

    private fun qrPanels() = composeTestRule.onAllNodesWithTag(QrPanelTestTag, useUnmergedTree = true)

    @Test
    fun nonBeamState_loading_keepsThePlainSpinner() {
        // R13: the card layout is BEAM-only. A chain without a receiver type has no target height
        // to reserve, so it must keep today's full-screen spinner and draw no QR panel at all.
        setContent(beamState(ViewState.Loading, beamAddressType = null, uri = ""))

        composeTestRule.onNodeWithTag(QrPanelTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun nonBeamState_reloadingAfterSuccess_keepsThePlainSpinner() {
        // The history-dependent case: setData() republishes ViewState.Loading after a Success on
        // every adaptersReadyObservable emission, for any chain. Scoping on "a card was already
        // shown" would have given this one the BEAM layout with no target type to reserve.
        val state = mutableStateOf<ReceiveModule.AbstractUiState>(
            beamState(ViewState.Success, beamAddressType = null, uri = SHORT_TOKEN)
        )
        composeTestRule.setContent {
            ComposeAppTheme {
                ReceiveAddressScreen(
                    title = "Receive BTC",
                    uiState = state.value,
                    setAmount = {},
                    onBackPress = {},
                    closeModule = {},
                    allowSetAmount = false,
                )
            }
        }
        composeTestRule.onNodeWithTag(QrPanelTestTag, useUnmergedTree = true).assertExists()

        composeTestRule.runOnUiThread {
            state.value = beamState(ViewState.Loading, beamAddressType = null, uri = "")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(QrPanelTestTag, useUnmergedTree = true).assertDoesNotExist()
        // Not merely "no card": the plain full-screen indicator is what must be there instead.
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun moneroState_loading_keepsThePlainSpinner() {
        // The same composable is shared with Monero and Stellar, which use a different state type
        // entirely; scoping on "a card was already shown" would have caught them.
        setContent(ReceiveMoneroUiState(viewState = ViewState.Loading))

        composeTestRule.onNodeWithTag(QrPanelTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    /** One composition, two states: the rule allows a single setContent per test. */
    private fun panelHeights(
        first: ReceiveModule.AbstractUiState,
        second: ReceiveModule.AbstractUiState,
    ): Pair<Int, Int> {
        val state = mutableStateOf(first)
        composeTestRule.setContent {
            ComposeAppTheme {
                ReceiveAddressScreen(
                    title = "Receive BEAM",
                    uiState = state.value,
                    setAmount = {},
                    onBackPress = {},
                    closeModule = {},
                    allowSetAmount = false,
                )
            }
        }
        val firstHeight = qrPanelHeight()
        composeTestRule.runOnUiThread { state.value = second }
        composeTestRule.waitForIdle()
        return firstHeight to qrPanelHeight()
    }

    private fun qrPanelHeight(): Int =
        composeTestRule.onNodeWithTag(QrPanelTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .size
            .height

    private fun setContent(uiState: ReceiveModule.AbstractUiState) {
        composeTestRule.setContent {
            ComposeAppTheme {
                ReceiveAddressScreen(
                    title = "Receive BEAM",
                    uiState = uiState,
                    setAmount = {},
                    onBackPress = {},
                    closeModule = {},
                    allowSetAmount = false,
                )
            }
        }
    }

    private fun beamState(
        viewState: ViewState,
        beamAddressType: BeamAddressType?,
        uri: String,
    ) = ReceiveModule.UiState(
        viewState = viewState,
        address = uri,
        mainNet = true,
        usedAddresses = emptyList(),
        usedChangeAddresses = emptyList(),
        isAddressHistorySupported = false,
        showTronAlert = false,
        beamAddressType = beamAddressType,
        uri = uri,
        blockchainName = "Beam",
        addressFormat = null,
        watchAccount = false,
        additionalItems = emptyList(),
        amount = null,
        alertText = null,
    )

    private companion object {
        const val SHORT_TOKEN = "3TxYRLMoZUyRNRNHBHqB1EsvwCXoZZ9gYVFBoK1Dz3aDZk2uqD"

        /** Past QR byte-mode capacity, the size of a real BEAM Offline token. */
        val LONG_TOKEN = "a".repeat(3_200)
    }
}
