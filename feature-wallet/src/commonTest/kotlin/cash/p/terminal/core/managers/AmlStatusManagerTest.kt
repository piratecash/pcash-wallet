package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.modules.transactions.AmlCheckResults
import cash.p.terminal.modules.transactions.CheckAmlIncomingTransactionUseCase
import cash.p.terminal.modules.transactions.IncomingAddressCheckResult
import cash.p.terminal.premium.domain.PremiumSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AmlStatusManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val checkAml = mockk<CheckAmlIncomingTransactionUseCase> {
        coEvery { this@mockk(any(), any()) } returns
            AmlCheckResults(perAddress = emptyMap(), overall = IncomingAddressCheckResult.Low)
    }
    private val bothLevelsEnabled = mockk<PremiumSettings>(relaxed = true) {
        every { getAmlCheckReceivedEnabled() } returns true
    }
    private val record = mockk<EvmTransactionRecord>(relaxed = true) {
        every { transactionRecordType } returns TransactionRecordType.EVM_INCOMING
        every { incomingEvents } returns null
        every { from } returns "0xsender"
    }

    @Test
    fun reloadEnabled_enqueueStillPendingBothLevelsEnabled_neverRequests() = runTest(dispatcher) {
        val manager = manager()

        manager.fetchStatusIfNeeded(UID, record)
        manager.reloadEnabled()
        advanceUntilIdle()

        coVerify(exactly = 0) { checkAml(any(), any()) }
        assertNull(manager.getStatus(UID))
    }

    @Test
    fun fetchStatusIfNeeded_afterReload_requests() = runTest(dispatcher) {
        val manager = manager()
        manager.reloadEnabled()

        manager.fetchStatusIfNeeded(UID, record)
        advanceUntilIdle()

        coVerify(exactly = 1) { checkAml(any(), any()) }
        assertNotNull(manager.getStatus(UID))
    }

    @Test
    fun fetchStatusIfNeeded_noReload_requests() = runTest(dispatcher) {
        val manager = manager()

        manager.fetchStatusIfNeeded(UID, record)
        advanceUntilIdle()

        coVerify(exactly = 1) { checkAml(any(), any()) }
    }

    private fun TestScope.manager() =
        AmlStatusManager(checkAml, bothLevelsEnabled, TestDispatcherProvider(dispatcher, this))

    private companion object {
        const val UID = "upper-transaction"
    }
}
