package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.OneTimeReceiveAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class ZcashRefundAddressReservationTest {

    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val dao = InMemoryZcashSingleUseAddressDao()
    private val transparentToken = mockZcashToken(TokenType.AddressSpecType.Transparent)
    private val zcashUnified = mockZcashToken(TokenType.AddressSpecType.Unified)
    private val issued = mutableListOf<String>()

    @Before
    fun setUp() {
        every { accountManager.activeAccount } returns buildTestAccount(ACCOUNT_ID)
        marketKit.stubZcashTransparentToken(transparentToken)
        coEvery { walletUseCase.getOneTimeReceiveAddress(transparentToken) } coAnswers { issueLikeAdapter() }
    }

    @Test
    fun temporaryAndFinalRefundAddress_sameSession_shareOneAddress() = runTest {
        val support = buildOffChainSwapProviderSupport(walletUseCase, accountManager, mockk(), marketKit, reservation())

        val warning = support.temporaryRefundAddress(zcashUnified)
        val firstFinal = support.getRefundAddress(zcashUnified)
        val repeatedFinal = support.getRefundAddress(zcashUnified)

        assertEquals(listOf("t1-1"), issued)
        assertEquals(warning, firstFinal)
        assertEquals(warning, repeatedFinal)
    }

    @Test
    fun current_previousSessionNeverSent_reusesItsAddress() = runTest {
        val reservation = reservation()
        val first = reservation.current()

        reservation.startSession()

        assertEquals(first, reservation.current())
        assertEquals(1, issued.size)
    }

    @Test
    fun current_previousSessionSent_issuesNewAddress() = runTest {
        val reservation = reservation()
        val first = reservation.current()
        reservation.markSent(first)

        reservation.startSession()

        assertNotEquals(first, reservation.current())
        assertEquals(2, issued.size)
    }

    @Test
    fun current_afterRestartUnsent_reusesPersistedAddress() = runTest {
        val first = reservation().current()

        assertEquals(first, reservation().current())
        assertEquals(1, issued.size)
    }

    @Test
    fun current_afterRestartSent_issuesNewAddress() = runTest {
        val before = reservation()
        before.markSent(before.current())

        assertEquals("t1-2", reservation().current())
    }

    @Test
    fun current_concurrentCalls_issueOneAddress() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { walletUseCase.getOneTimeReceiveAddress(transparentToken) } coAnswers {
            release.await()
            issueLikeAdapter()
        }
        val reservation = reservation()

        val first = async { reservation.current() }
        val second = async { reservation.current() }
        runCurrent()
        release.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, issued.size)
    }

    @Test
    fun current_issuanceThrows_throwsUnavailable() = runTest {
        coEvery { walletUseCase.getOneTimeReceiveAddress(transparentToken) } throws IllegalStateException("sdk")
        val reservation = reservation()

        assertFailsWith<ZcashRefundAddressUnavailable> { reservation.current() }
        coVerify(exactly = 0) { walletUseCase.getReceiveAddress(any()) }
    }

    @Test
    fun current_adapterIssuesNothing_throwsUnavailableInsteadOfRegularAddress() = runTest {
        val adapter = mockk<OneTimeIssuingAdapter> {
            every { receiveAddress } returns "t1-permanent"
            coEvery { generateOneTimeAddress() } returns null
        }
        val realWalletUseCase = walletUseCaseWith(adapter)

        assertNull(realWalletUseCase.getOneTimeReceiveAddress(transparentToken))
        assertFailsWith<ZcashRefundAddressUnavailable> { reservation(realWalletUseCase).current() }
    }

    private interface OneTimeIssuingAdapter : IReceiveAdapter, OneTimeReceiveAdapter

    private fun walletUseCaseWith(adapter: IReceiveAdapter): WalletUseCase {
        val wallet = mockk<Wallet> { every { token } returns transparentToken }
        val walletManager = mockk<IWalletManager> { every { activeWallets } returns listOf(wallet) }
        val adapterManager = mockk<IAdapterManager> { every { getReceiveAdapterForWallet(wallet) } returns adapter }
        return WalletUseCase(
            walletManager = walletManager,
            accountManager = accountManager,
            adapterManager = adapterManager,
            getHardwarePublicKeyForWalletUseCase = mockk(),
            scanToAddUseCase = mockk(),
            walletFactory = mockk(),
            marketKit = marketKit,
            deletedWalletRestorer = mockk(),
        )
    }

    private fun reservation(useCase: WalletUseCase = walletUseCase) =
        buildZcashRefundAddressReservation(useCase, accountManager, marketKit, dao)

    private suspend fun issueLikeAdapter(): String {
        val address = "t1-${issued.size + 1}"
        issued += address
        dao.saveIssued(ACCOUNT_ID, address)
        return address
    }

    private companion object {
        const val ACCOUNT_ID = "acc-1"
    }
}
