package cash.p.terminal.modules.send.evm.settings

import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.DefaultBlockParameter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Single
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SendEvmNonceServiceTest {

    private val evmKit = mockk<EthereumKit>()

    @Test
    fun retry_afterFetchError_refetchesAndKeepsInitialNonce() = runBlocking {
        every { evmKit.getNonce(DefaultBlockParameter.Latest) } returns Single.just(40)
        every { evmKit.getNonce(DefaultBlockParameter.Pending) } returns Single.error(IOException("timeout"))
        val service = SendEvmNonceService(evmKit, initialNonce = 42)
        service.start()
        service.reset()
        assertTrue(service.state is DataState.Error)

        service.retry()

        assertEquals(42L, service.state.dataOrNull?.nonce)
        verify(exactly = 2) { evmKit.getNonce(DefaultBlockParameter.Latest) }
    }

    @Test
    fun retry_runsOffMainThread() = runBlocking {
        var latestNonceThread: Thread? = null
        every { evmKit.getNonce(DefaultBlockParameter.Latest) } answers {
            latestNonceThread = Thread.currentThread()
            Single.just(5)
        }
        every { evmKit.getNonce(DefaultBlockParameter.Pending) } returnsMany listOf(
            Single.error(IOException("timeout")),
            Single.just(5)
        )
        val service = SendEvmNonceService(evmKit)
        service.start()
        assertTrue(service.state is DataState.Error)

        service.retry()

        assertNotSame(Thread.currentThread(), latestNonceThread)
        assertEquals(5L, service.state.dataOrNull?.nonce)
    }
}
