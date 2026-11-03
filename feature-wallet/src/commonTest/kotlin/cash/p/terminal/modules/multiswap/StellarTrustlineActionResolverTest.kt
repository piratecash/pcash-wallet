package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.modules.multiswap.action.ActionActivateStellarAsset
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.Network
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.stellarkit.room.StellarAsset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class StellarTrustlineActionResolverTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val walletUseCase = mockk<WalletUseCase>()
    private val adapterManager = mockk<IAdapterManager>()
    private val stellarKitManager = mockk<StellarKitManager>()
    private val provider = mockk<IMultiSwapProvider> {
        every { getCreateTokenActionRequired(any()) } returns null
    }
    private val wallet = mockk<Wallet>(relaxed = true)
    private val adapter = mockk<StellarAssetAdapter>()
    private val assetToken = stellarToken(TokenType.Asset("USDC", "ISSUER"))
    private val offlineOperationGate = mockk<OfflineOperationGate> {
        every { isBlocked(any<Wallet>()) } returns false
    }

    private val resolver = StellarTrustlineActionResolver(
        walletUseCase,
        adapterManager,
        stellarKitManager,
        offlineOperationGate,
        TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun resolve_nonStellarToken_returnsNull() = runTest {
        val token = mockk<Token> {
            every { blockchainType } returns BlockchainType.Ethereum
            every { type } returns TokenType.Eip20("0x1")
        }

        assertNull(resolver.resolve(provider, token))
    }

    @Test
    fun resolve_nativeXlm_returnsNull() = runTest {
        assertNull(resolver.resolve(provider, stellarToken(TokenType.Native)))
    }

    @Test
    fun resolve_assetWalletMissing_returnsProviderCreateAction() = runTest {
        val create = mockk<ActionCreate>()
        every { provider.getCreateTokenActionRequired(listOf(assetToken)) } returns create

        assertTrue(resolver.resolve(provider, assetToken) === create)
    }

    @Test
    fun resolve_assetAdapterReportsTrustline_returnsNull() = runTest {
        givenWalletWithAdapter()
        coEvery { adapter.isTrustlineEstablished() } returns true

        assertNull(resolver.resolve(provider, assetToken))
    }

    @Test
    fun resolve_assetAdapterReportsNoTrustline_returnsActivateAction() = runTest {
        givenWalletWithAdapter()
        coEvery { adapter.isTrustlineEstablished() } returns false

        assertTrue(resolver.resolve(provider, assetToken) is ActionActivateStellarAsset)
    }

    @Test
    fun resolve_assetAdapterMissing_usesHorizonCheck() = runTest {
        every { walletUseCase.getWallet(assetToken) } returns wallet
        every { adapterManager.getAdapterForWallet<StellarAssetAdapter>(wallet) } returns null
        every { stellarKitManager.getAddress(any()) } returns "ADDRESS"
        mockkObject(StellarKit.Companion)
        val asset = StellarAsset.Asset("USDC", "ISSUER")

        every { StellarKit.isAssetEnabled(Network.MainNet, asset, "ADDRESS") } returns false
        assertTrue(resolver.resolve(provider, assetToken) is ActionActivateStellarAsset)

        every { StellarKit.isAssetEnabled(Network.MainNet, asset, "ADDRESS") } returns true
        assertNull(resolver.resolve(provider, assetToken))
    }

    @Test
    fun resolve_trustlineCheckThrows_returnsNull() = runTest {
        givenWalletWithAdapter()
        coEvery { adapter.isTrustlineEstablished() } throws IllegalStateException("horizon down")

        assertNull(resolver.resolve(provider, assetToken))
    }

    @Test
    fun resolve_cancelled_rethrowsCancellation() = runTest {
        givenWalletWithAdapter()
        coEvery { adapter.isTrustlineEstablished() } throws CancellationException("cancelled")

        try {
            resolver.resolve(provider, assetToken)
            fail("CancellationException expected")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun resolve_networkOffline_returnsNullWithoutLookup() = runTest {
        givenWalletWithAdapter()
        coEvery { adapter.isTrustlineEstablished() } returns false
        every { offlineOperationGate.isBlocked(wallet) } returns true

        assertNull(resolver.resolve(provider, assetToken))
        coVerify(exactly = 0) { adapter.isTrustlineEstablished() }
    }

    private fun givenWalletWithAdapter() {
        every { walletUseCase.getWallet(assetToken) } returns wallet
        every { adapterManager.getAdapterForWallet<StellarAssetAdapter>(wallet) } returns adapter
    }

    private fun stellarToken(tokenType: TokenType) = mockk<Token> {
        every { blockchainType } returns BlockchainType.Stellar
        every { type } returns tokenType
    }
}
