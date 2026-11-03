package cash.p.terminal.modules.walletconnect.list

import cash.p.terminal.modules.walletconnect.WCSessionManager
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletConnectListViewModelTest {

    private val wcSessionManager = mockk<WCSessionManager> {
        every { sessions } returns emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WalletKit)
        mockkStatic(WalletKit::class)
        every { WalletKit.pair(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun init_v2DeepLink_pairsExactlyOnce() {
        val viewModel = createViewModel(DEEP_LINK)

        viewModel.refreshList()

        verify(exactly = 1) { WalletKit.pair(Wallet.Params.Pair(DEEP_LINK), any(), any()) }
    }

    @Test
    fun init_noDeepLink_doesNotPair() {
        createViewModel(deepLinkUri = null)

        verify(exactly = 0) { WalletKit.pair(any(), any(), any()) }
    }

    private fun createViewModel(deepLinkUri: String?) =
        WalletConnectListViewModel(wcSessionManager, mockk(), deepLinkUri)

    private companion object {
        const val DEEP_LINK = "wc:c51d72@2?relay-protocol=irn&symKey=abc"
    }
}
