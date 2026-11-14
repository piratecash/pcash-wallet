package cash.p.terminal.modules.nft.burn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.nft.operation.NftOperationSheetHost
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drives the real [rememberNftBurn] result path and the real [NftOperationSheetHost]; only the sheet body and the PIN are stubbed. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class NftBurnResultDesktopTest {

    private val item = NftItemRecord(
        accountId = "account",
        blockchainType = BlockchainType.Ton,
        ownerAddress = "owner",
        address = "nft",
        position = 0,
        name = "Spam",
        description = null,
        realOwnerAddress = null,
        collectionAddress = null,
        collectionName = null,
        collectionDescription = null,
        trust = NftTrust.None,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )
    private val owner = NftOwner("account", BlockchainType.Ton, "owner")

    private val repository = mockk<NftCollectionsRepository> {
        coEvery { refresh(any(), any()) } returns Unit
    }
    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(
        dispatcher = UnconfinedTestDispatcher(),
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
    )
    private val passThroughPin = object : SecretActionAuthorizer {
        @Composable
        override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action

        @Composable
        override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit) = action

        @Composable
        override fun <A> rememberTransferAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action
    }

    private val caller = CallerPage()
    private val backStack = NavBackStack<HSPage>(RootPage(), caller)
    private val navigation = HSNavigation(backStack)
    private val finished = mutableListOf<NftBurnSheet.Result>()
    private var outcome by mutableStateOf<NftOperationOutcome>(NftOperationOutcome.Idle)
    private lateinit var burn: (NftItemRecord, String?) -> Unit

    @Test
    fun burnResult_done_refreshesOwnerForcedOnceAndClosesCaller() = runBurnTest {
        outcome = NftOperationOutcome.Done(sent = 1)
        waitForIdle()

        coVerify(exactly = 1) { repository.refresh(setOf(owner), true) }
        assertEquals(1, finished.size)
        assertTrue(finished.single().outcome is NftOperationOutcome.Done)
        assertTrue(backStack.single() is RootPage)
    }

    @Test
    fun burnResult_ended_refreshesOwnerForcedOnceAndClosesCaller() = runBurnTest {
        outcome = NftOperationOutcome.Ended(1, 1, HSCaution(TranslatableString.PlainString("boom")))
        waitForIdle()

        coVerify(exactly = 1) { repository.refresh(setOf(owner), true) }
        assertEquals(1, finished.size)
        assertTrue(finished.single().outcome is NftOperationOutcome.Ended)
        assertTrue(backStack.single() is RootPage)
    }

    @Test
    fun burnResult_runningThenIdleWithoutDismiss_deliversNothingAndKeepsSheetAndCaller() = runBurnTest {
        outcome = NftOperationOutcome.Running
        waitForIdle()
        outcome = NftOperationOutcome.Idle
        waitForIdle()

        coVerify(exactly = 0) { repository.refresh(any(), any()) }
        assertTrue(finished.isEmpty())
        assertTrue(backStack.any { it is NftBurnSheet })
        assertTrue(caller in backStack)
    }

    private fun runBurnTest(body: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest {
        setContent {
            KoinIsolatedContext(
                koinApplication {
                    modules(
                        module {
                            single { repository }
                            single { dispatcherProvider }
                            single<SecretActionAuthorizer> { passThroughPin }
                        }
                    )
                }
            ) {
                CompositionLocalProvider(
                    LocalHudMessenger provides mockk<HudMessenger>(relaxed = true),
                    LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    TestHost()
                }
            }
        }
        runOnIdle { burn(item, null) }
        waitForIdle()
        assertTrue(backStack.any { it is NftBurnSheet })
        body()
    }

    @Composable
    private fun TestHost() {
        NavDisplay(
            backStack = navigation.backStack,
            onBack = { navigation.navigateUp() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = { key ->
                NavEntry(key, key.contentKey()) {
                    if (key is NftBurnSheet) {
                        NftOperationSheetHost(
                            sheet = key,
                            navigation = navigation,
                            outcome = outcome,
                            resultOf = { NftBurnSheet.Result(it, isDomain = false) },
                        ) {}
                    } else {
                        key.Content(navigation)
                    }
                }
            },
        )
    }

    private class RootPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }

    // Finishes the way NftPreviewPage does: the caller closes itself once the result arrives.
    private inner class CallerPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) {
            val action = navigation.rememberNftBurn(namesRevealed = false) {
                finished += it
                navigation.navigateUpFrom(this)
            }
            SideEffect { burn = action }
        }
    }
}
