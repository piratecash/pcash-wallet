package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.models.Event
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TonConnectSendRequestScreenDesktopTest {

    // The app bar title says Confirm too.
    private val confirmButton = hasText("Confirm") and hasClickAction()
    private val navigation = HSNavigation(NavBackStack<HSPage>(RootPage()))
    private val passThroughPin = object : SecretActionAuthorizer {
        @Composable
        override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action

        @Composable
        override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit) = action

        @Composable
        override fun <A> rememberTransferAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action
    }

    // The fee cell resolves its formatter through the global Koin, not the composition.
    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<SecretActionAuthorizer> { passThroughPin }
                    single<TransactionInfoHelper> { transactionInfoHelper }
                    single<IAppNumberFormatter> { numberFormatter }
                    single<IClipboardManager> { mockk(relaxed = true) }
                }
            )
        }
    }

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun confirmClick_confirmEnabled_confirmsRequest() {
        val viewModel = viewModel(confirmEnabled = true)

        runScreenTest(viewModel) {
            onNode(confirmButton).performClick()
            waitForIdle()
        }

        verify(exactly = 1) { viewModel.confirm() }
    }

    @Test
    fun confirmButton_scopeClosed_isDisabled() = runScreenTest(viewModel(confirmEnabled = false)) {
        onNode(confirmButton).assertIsNotEnabled()
    }

    @Test
    fun rejectClick_rejectEnabled_rejectsRequest() {
        val viewModel = viewModel(confirmEnabled = true)

        runScreenTest(viewModel) {
            onNodeWithText("Reject").assertIsEnabled().performClick()
            waitForIdle()
        }

        verify(exactly = 1) { viewModel.reject() }
    }

    @Test
    fun screen_unsupportedAction_showsWarningMessagesAndNftOnce() =
        runScreenTest(requestViewModel(TonTransactionRecord.Action.Type.Unsupported("NftPurchase"))) {
            onNodeWithText(UNKNOWN_ACTION_WARNING).assertExists()
            onNodeWithText(RECIPIENT).assertExists()
            onAllNodesWithText(SENT_AMOUNT).assertCountEquals(1)
            onNodeWithText(NFT_NAME).assertExists()
            onAllNodesWithText(NFT_ADDRESS).assertCountEquals(1)
            onNodeWithText(NFT_ADDRESS_TITLE).assertExists()
            onNodeWithText(NATIVE).assertDoesNotExist()
        }

    @Test
    fun screen_supportedActionsOnly_showsNoWarningMessagesOrRisk() =
        runScreenTest(requestViewModel(TonTransactionRecord.Action.Type.ContractDeploy(emptyList()))) {
            onNodeWithText(UNKNOWN_ACTION_WARNING).assertDoesNotExist()
            onNodeWithText(RECIPIENT).assertDoesNotExist()
            onAllNodesWithText(SENT_AMOUNT).assertCountEquals(0)
            onNodeWithText(NFT_ADDRESS).assertDoesNotExist()
        }

    private fun viewModel(confirmEnabled: Boolean) = mockk<TonConnectSendRequestViewModel>(relaxed = true) {
        every { uiState } returns TonConnectSendRequestUiState(
            tonTransactionRecord = null,
            error = null,
            confirmEnabled = confirmEnabled,
            rejectEnabled = true,
        )
    }

    private fun requestViewModel(actionType: TonTransactionRecord.Action.Type): TonConnectSendRequestViewModel {
        val token = Token(
            coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
            blockchain = Blockchain(BlockchainType.Ton, "The Open Network", null),
            type = TokenType.Native,
            decimals = 9,
        )
        val account = Account("account-id", "Account", AccountType.Mnemonic(listOf("word"), ""), AccountOrigin.Created, 0)
        val record = TonTransactionRecord(
            source = TransactionSource(token.blockchain, account, TokenType.Native.meta),
            event = Event("e", 1L, 1_715_000_000L, false, true, -5_000_000L, emptyList()),
            token = token,
            actions = listOf(TonTransactionRecord.Action(actionType, TransactionStatus.Completed)),
        )
        val sent = TransactionValue.CoinValue(token, BigDecimal("-5"))
        val nft = TransactionValue.NftValue(NFT_ADDRESS, BigDecimal.ONE.negate(), NFT_NAME, "NFT")
        return mockk(relaxed = true) {
            every { uiState } returns TonConnectSendRequestUiState(
                tonTransactionRecord = record,
                error = null,
                confirmEnabled = true,
                rejectEnabled = true,
                riskValues = listOf(nft),
                requestMessages = listOf(TonConnectRequestMessage(RECIPIENT, sent)),
            )
        }
    }

    private fun runScreenTest(
        viewModel: TonConnectSendRequestViewModel,
        body: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalHudMessenger provides mockk<HudMessenger>(relaxed = true),
                LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
            ) {
                ComposeAppTheme { TonConnectSendRequestScreen(navigation, viewModel) }
            }
        }
        body()
    }

    private val transactionInfoHelper = mockk<TransactionInfoHelper>(relaxed = true) {
        every { getXRate(any()) } returns null
        every { getContact(any(), any()) } returns null
    }
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true) {
        every { formatCoinFull(any(), any(), any()) } answers { "${firstArg<BigDecimal>().toPlainString()} ${secondArg<String?>()}" }
    }

    private companion object {
        const val UNKNOWN_ACTION_WARNING =
            "The app could not recognize this action. Check the amounts and addresses below before confirming."
        const val RECIPIENT = "EQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG"
        const val SENT_AMOUNT = "-5 TON"
        const val NFT_ADDRESS = "EQCvxJy4eG8hyHBFsZ7eePxrRsUQSFE_jpptRAYBmcG_DOGS"
        const val NFT_NAME = "olegleonov.ton"
        const val NFT_ADDRESS_TITLE = "NFT address"
        const val NATIVE = "Native"
    }

    private class RootPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }
}
