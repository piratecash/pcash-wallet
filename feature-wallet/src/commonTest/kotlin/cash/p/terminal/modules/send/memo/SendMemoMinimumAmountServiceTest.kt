package cash.p.terminal.modules.send.memo

import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.entities.Address
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SendMemoMinimumAmountServiceTest {

    private val adapter = mockk<ISendMemoAdapter>()
    private val service = SendMemoMinimumAmountService(adapter)

    @Test
    fun setValidAddress_lookupCancelled_publishesNoError() = runTest(UnconfinedTestDispatcher()) {
        coEvery { adapter.getMinimumSendAmount(ADDRESS) } coAnswers { CompletableDeferred<BigDecimal?>().await() }
        val lookup = launch { service.setValidAddress(Address(ADDRESS)) }
        assertFalse(service.stateFlow.value.canBeSend)

        lookup.cancel()

        assertEquals(null, service.stateFlow.value.error)
    }

    private companion object {
        const val ADDRESS = "GAZXDMWYHMPM2WF6FCWEBIMJITKKTU6MLHYLCFRVB3WMXTNPVEHBOXRE"
    }
}
