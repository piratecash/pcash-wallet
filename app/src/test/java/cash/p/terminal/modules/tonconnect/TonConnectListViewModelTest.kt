package cash.p.terminal.modules.tonconnect

import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TonConnectListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val tonConnectManager = mockk<TonConnectManager>()
    private val accountManager = mockk<IAccountManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val account = mockk<Account> {
            every { id } returns WALLET_ID
            every { name } returns ACCOUNT_NAME
        }
        every { accountManager.accounts } returns listOf(account)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_kitInitializationFails_showsDAppsWithoutInvalidUriError() = runTest(dispatcher) {
        val dapp = mockk<DAppEntity> { every { walletId } returns WALLET_ID }
        coEvery { tonConnectManager.kit() } throws IllegalStateException("kit unavailable")
        every { tonConnectManager.getDApps() } returns flowOf(listOf(dapp))

        val viewModel = TonConnectListViewModel(accountManager, tonConnectManager)
        advanceUntilIdle()

        // The screen renders any error as an invalid-URI sheet, which would mislead here.
        assertNull(viewModel.uiState.error)
        assertEquals(mapOf(ACCOUNT_NAME to listOf(dapp)), viewModel.uiState.dapps)
    }

    @Test
    fun init_kitAvailable_requestsKitAndShowsDApps() = runTest(dispatcher) {
        val dapp = mockk<DAppEntity> { every { walletId } returns WALLET_ID }
        coEvery { tonConnectManager.kit() } returns mockk<TonConnectKit>()
        every { tonConnectManager.getDApps() } returns flowOf(listOf(dapp))

        val viewModel = TonConnectListViewModel(accountManager, tonConnectManager)
        advanceUntilIdle()

        assertEquals(mapOf(ACCOUNT_NAME to listOf(dapp)), viewModel.uiState.dapps)
        coVerify(exactly = 1) { tonConnectManager.kit() }
    }

    private companion object {
        const val WALLET_ID = "wallet-1"
        const val ACCOUNT_NAME = "Wallet 1"
    }
}
