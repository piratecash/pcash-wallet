package cash.p.terminal.modules.nft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.modules.nft.burn.NftBurnSheet
import cash.p.terminal.modules.nft.burn.rememberNftBurn
import cash.p.terminal.modules.nft.renew.NftDomainRenewSheet
import cash.p.terminal.modules.nft.renew.rememberDomainRenewal
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

/** The PIN page pauses the preview, which masks the names; the sheet opened after the PIN must keep the reveal. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class NftNamesRevealedAcrossPinDesktopTest {

    private val owner = NftOwner("account", BlockchainType.Ton, "owner")
    private val item = NftItemRecord(
        accountId = owner.accountId,
        blockchainType = owner.blockchainType,
        ownerAddress = owner.address,
        address = "nft",
        position = 0,
        name = "alice.ton",
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
    private val domain = NftDomainExpiry(owner, "nft", "alice.ton", expiresAt = 0L, isOnSale = false)

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider(
        dispatcher = UnconfinedTestDispatcher(),
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
    )
    private val pendingPin = mutableListOf<() -> Unit>()
    private val pinPage = object : SecretActionAuthorizer {
        @Composable
        override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action

        @Composable
        override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit) = action

        @Composable
        override fun <A> rememberTransferAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit =
            { arg -> pendingPin += { action(arg) } }
    }

    private val backStack = NavBackStack<HSPage>()
    private val navigation = HSNavigation(backStack)
    private var namesRevealed by mutableStateOf(true)
    private lateinit var burn: (NftItemRecord, String?) -> Unit
    private lateinit var renew: (List<NftDomainExpiry>) -> Unit

    @Test
    fun rememberNftBurn_revealDroppedWhilePinOpen_sheetKeepsClickTimeReveal() = runPinTest {
        runOnIdle { burn(item, domain.name) }
        dropRevealAndConfirmPin()

        assertTrue((backStack.last() as NftBurnSheet).namesRevealed)
    }

    @Test
    fun rememberDomainRenewal_revealDroppedWhilePinOpen_sheetKeepsClickTimeReveal() = runPinTest {
        runOnIdle { renew(listOf(domain)) }
        dropRevealAndConfirmPin()

        assertTrue((backStack.last() as NftDomainRenewSheet).namesRevealed)
    }

    private fun ComposeUiTest.dropRevealAndConfirmPin() {
        namesRevealed = false
        waitForIdle()
        runOnIdle { pendingPin.removeAt(0)() }
        waitForIdle()
    }

    private fun runPinTest(body: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest {
        setContent {
            KoinIsolatedContext(
                koinApplication {
                    modules(
                        module {
                            single { mockk<NftCollectionsRepository>(relaxed = true) }
                            single { dispatcherProvider }
                            single<SecretActionAuthorizer> { pinPage }
                        }
                    )
                }
            ) {
                CompositionLocalProvider(LocalHudMessenger provides mockk<HudMessenger>(relaxed = true)) {
                    Caller()
                }
            }
        }
        waitForIdle()
        body()
    }

    @Composable
    private fun Caller() {
        val burnAction = navigation.rememberNftBurn(namesRevealed = namesRevealed) {}
        val renewAction = navigation.rememberDomainRenewal(namesRevealed = namesRevealed)
        SideEffect {
            burn = burnAction
            renew = renewAction
        }
    }
}
