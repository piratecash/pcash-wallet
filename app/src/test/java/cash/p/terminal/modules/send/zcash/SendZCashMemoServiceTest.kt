package cash.p.terminal.modules.send.zcash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendZCashMemoServiceTest {

    @Test
    fun setAddress_unifiedAddress_allowsMemo() {
        val service = SendZCashMemoService(memoSupportedByAccount = true)

        service.setAddress("u1recipient")

        assertTrue(service.stateFlow.value.memoIsAllowed)
    }

    @Test
    fun setAddress_transparentAddress_forbidsMemo() {
        val service = SendZCashMemoService(memoSupportedByAccount = true)

        service.setAddress("t1recipient")

        assertFalse(service.stateFlow.value.memoIsAllowed)
    }

    @Test
    fun setAddress_accountWithoutMemoSupport_forbidsMemoAndClearsIt() {
        val service = SendZCashMemoService(memoSupportedByAccount = false)
        service.setMemo("order-42")

        service.setAddress("u1recipient")

        assertFalse(service.stateFlow.value.memoIsAllowed)
        assertEquals("", service.stateFlow.value.memo)
    }

    @Test
    fun setAddress_accountWithoutMemoSupportOnSaplingAddress_forbidsMemo() {
        val service = SendZCashMemoService(memoSupportedByAccount = false)

        service.setAddress("zs1recipient")

        assertFalse(service.stateFlow.value.memoIsAllowed)
    }
}
