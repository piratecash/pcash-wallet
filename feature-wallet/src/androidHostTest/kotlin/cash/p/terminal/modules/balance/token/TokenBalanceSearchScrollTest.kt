package cash.p.terminal.modules.balance.token

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.balance.BalanceViewItem
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_down_left_24
import cash.p.terminal.ui.compose.BalanceHideOnFlipHandler
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.balance.DeemedValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TokenBalanceSearchScrollTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val flipHandler = mockk<BalanceHideOnFlipHandler>(relaxed = true) {
            every { pendingInfo } returns MutableStateFlow(false)
        }
        startKoin { modules(module { single { flipHandler } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun searchClick_listScrolledFarDown_keepsScrollPosition() {
        setContent()
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(DEEP_TRANSACTION))
        composeTestRule.onNodeWithText(DEEP_TRANSACTION).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
        composeTestRule.onNodeWithText(DEEP_TRANSACTION).assertIsDisplayed()
        composeTestRule.onNodeWithText(FIRST_TRANSACTION).assertDoesNotExist()
    }

    @Test
    fun searchClick_listAtTop_scrollsSearchHeaderToTop() {
        setContent()
        composeTestRule.onNodeWithText(BALANCE).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).assertIsFocused().assertIsDisplayed()
        composeTestRule.onNodeWithText(BALANCE).assertDoesNotExist()
    }

    @Test
    fun searchQueryInput_listScrolledFarDown_firstResultStartsBelowSearchField() {
        setContent()
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(DEEP_TRANSACTION))
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        composeTestRule.onNode(hasSetTextAction()).performTextInput(QUERY)
        composeTestRule.waitForIdle()

        val fieldBottom = composeTestRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.bottom
        val firstResultTop = composeTestRule.onNode(hasText(FIRST_RESULT) and !hasSetTextAction()).fetchSemanticsNode().boundsInRoot.top
        assertTrue("first result top $firstResultTop is under the search field $fieldBottom", firstResultTop >= fieldBottom)
    }

    @Test
    fun searchResults_screenRestored_keepsScrollPosition() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { ScreenContent() }
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(BROAD_QUERY)
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(DEEP_TRANSACTION))

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(DEEP_TRANSACTION).assertIsDisplayed()
    }

    private fun setContent() {
        composeTestRule.setContent { ScreenContent() }
    }

    // Saveable like the ViewModel state, which outlives the screen's composition
    @Composable
    private fun ScreenContent() {
        var searchActive by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        ComposeAppTheme {
            TokenBalanceScreenContent(
                uiState = uiState(searchActive, query),
                secondaryValue = DeemedValue("$1,234.56"),
                sendResult = null,
                navigation = HSNavigation(NavBackStack()),
                refreshing = false,
                onToggleFavorite = {},
                onToggleBalanceVisibility = {},
                onSearchClick = { searchActive = true },
                onSearchClose = {
                    searchActive = false
                    query = ""
                },
                onSearchQueryChange = { query = it },
                onSetTransactionType = {},
                onWillShow = {},
                onTransactionClick = {},
                onSensitiveTransactionClick = {},
                onBottomReached = {},
                onSetAmlCheckEnabled = {},
                onDismissAmlPromo = {},
                onDismissNetworkFeeWarning = {},
                onSendClick = {},
                onReceiveClick = {},
                onSwapClick = {},
                onShieldClick = {},
                onSyncErrorClick = {},
                onStackingClicked = {},
                onShowAllTransactionsClicked = {},
                onClickSubtitle = {},
                onRefresh = {},
                onSettingsClick = {},
                onGoOnline = {},
            )
        }
    }

    private fun uiState(searchActive: Boolean, query: String) = TokenBalanceModule.TokenBalanceUiState(
        title = "Preview Coin",
        coinCode = "PCN",
        balanceViewItem = balanceViewItem(),
        transactions = (0 until TRANSACTION_COUNT).map(::transaction)
            .filter { it.title.contains(query) }
            .groupBy { "Day ${it.uid.removePrefix("uid-").toInt() / GROUP_SIZE}" },
        hasHiddenTransactions = false,
        syncing = false,
        searchActive = searchActive,
        searchQuery = query,
    )

    private fun transaction(index: Int) = TransactionViewItem(
        uid = "uid-$index",
        progress = null,
        title = "Tx $index",
        subtitle = "",
        primaryValue = null,
        secondaryValue = null,
        date = Date(0),
        formattedTime = "",
        icon = TransactionViewItem.Icon.ImageResource(Res.drawable.ic_arrow_down_left_24),
    )

    private fun balanceViewItem() = BalanceViewItem(
        wallet = WalletFactory.previewWallet(),
        primaryValue = DeemedValue(BALANCE),
        exchangeValue = DeemedValue("$1,000.00", visible = false),
        secondaryValue = DeemedValue("$1,234.56"),
        lockedValues = emptyList(),
        sendAvailability = OperationAvailability.Available,
        syncingProgress = SyncingProgress(null, null),
        syncingTextValue = null,
        syncedUntilTextValue = null,
        failedIconVisible = false,
        coinIconVisible = true,
        badge = null,
        swapVisible = true,
        swapAvailability = OperationAvailability.Available,
        errorMessage = null,
        isWatchAccount = false,
        warning = null,
        displayDiffOptionType = DisplayDiffOptionType.NONE,
    )

    private companion object {
        const val TRANSACTION_COUNT = 60
        const val GROUP_SIZE = 20
        const val BALANCE = "1.2345 PCN"
        const val FIRST_TRANSACTION = "Tx 0"
        const val DEEP_TRANSACTION = "Tx 45"
        const val QUERY = "Tx 5"
        const val BROAD_QUERY = "Tx"
        const val FIRST_RESULT = "Tx 5"
    }
}
