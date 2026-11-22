package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashSession
import cash.p.terminal.core.adapters.zcash.session.zcashSession
import cash.p.terminal.core.adapters.zcash.session.zcashWalletMock
import cash.p.terminal.core.managers.RestoreSettingsTestFixture
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.entities.ZcashSingleUseAddress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class ZcashTransparentIndexStoreTest {

    private val settings = RestoreSettingsTestFixture()
    private val issuedAddresses = mockk<ZcashSingleUseAddressStorage> {
        coEvery { getAllAddresses(any()) } returns emptyList()
    }
    private val wallet = zcashWalletMock()

    private fun TestScope.store() = ZcashTransparentIndexStore(
        storage = settings.storage,
        issuedAddresses = issuedAddresses,
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
    )

    private fun TestScope.session(): ZcashSession = zcashSession(wallet)

    @Test
    fun raise_lowerIndex_keepsTheHigherOne() = runTest {
        val store = store()

        store.raise(ACCOUNT_ID, 9)
        store.raise(ACCOUNT_ID, 4)

        assertEquals(9, store.issueIndex(ACCOUNT_ID))
    }

    @Test
    fun prepare_noIndexButIssuedRows_backfillsTheHighestIndexTheSdkKnows() = runTest {
        val session = session()
        coEvery { issuedAddresses.getAllAddresses(ACCOUNT_ID) } returns listOf("t1a", "t1b", "t1gone").map(::row)
        coEvery { wallet.transparentAddressIndex(session.dbAccountId, "t1a") } returns 3
        coEvery { wallet.transparentAddressIndex(session.dbAccountId, "t1b") } returns 9
        coEvery { wallet.transparentAddressIndex(session.dbAccountId, "t1gone") } returns null
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns 9
        val store = store()

        store.prepare(session)

        assertEquals(9, store.issueIndex(ACCOUNT_ID))
        assertNull(store.recreatedThrough(ACCOUNT_ID))
    }

    @Test
    fun prepare_indexAlreadyStored_doesNotBackfill() = runTest {
        val session = session()
        coEvery { issuedAddresses.getAllAddresses(ACCOUNT_ID) } returns listOf(row("t1a"))
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns 12
        val store = store()
        store.raise(ACCOUNT_ID, 12)

        store.prepare(session)

        coVerify(exactly = 0) { wallet.transparentAddressIndex(any(), any()) }
    }

    @Test
    fun prepare_sdkHoldsFewerRowsThanIssued_raisesTheFloorBeforeRecreatingTheRows() = runTest {
        val session = session()
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns 2
        val store = store()
        store.raise(ACCOUNT_ID, ISSUED)
        var floorWhileRecreating: Int? = null
        coEvery { wallet.reserveTransparentAddressesThrough(session.dbAccountId, ISSUED) } coAnswers {
            floorWhileRecreating = store.recreatedThrough(ACCOUNT_ID)
            ISSUED - 2
        }

        store.prepare(session)

        assertEquals(ISSUED, floorWhileRecreating)
        assertEquals(ISSUED, store.recreatedThrough(ACCOUNT_ID))
    }

    @Test
    fun prepare_sdkAlreadyHoldsTheIssuedRows_leavesNoFloor() = runTest {
        val session = session()
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns ISSUED
        val store = store()
        store.raise(ACCOUNT_ID, ISSUED)

        store.prepare(session)

        coVerify(exactly = 0) { wallet.reserveTransparentAddressesThrough(any(), any()) }
        assertNull(store.recreatedThrough(ACCOUNT_ID))
    }

    @Test
    fun prepare_neverIssued_recreatesNothing() = runTest {
        val store = store()

        store.prepare(session())

        coVerify(exactly = 0) { wallet.reserveTransparentAddressesThrough(any(), any()) }
        assertNull(store.issueIndex(ACCOUNT_ID))
    }

    @Test
    fun prepare_calledTwiceOnOneSession_checksTheSdkOnce() = runTest {
        val session = session()
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns ISSUED
        val store = store()
        store.raise(ACCOUNT_ID, ISSUED)

        store.prepare(session)
        store.prepare(session)

        coVerify(exactly = 1) { wallet.highestStoredTransparentAddressIndex(any()) }
    }

    @Test
    fun prepare_firstRunFails_theNextCallRetries() = runTest {
        val session = session()
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } throws
            IOException("boom") andThen 2
        val store = store()
        store.raise(ACCOUNT_ID, ISSUED)

        assertFailsWith<IOException> { store.prepare(session) }
        store.prepare(session)

        coVerify(exactly = 1) { wallet.reserveTransparentAddressesThrough(session.dbAccountId, ISSUED) }
    }

    @Test
    fun raiseFromHistory_unusedReceiveAddressStoredAbove_raisesOnlyToTheUsedIndex() = runTest {
        val session = session()
        coEvery { wallet.highestUsedTransparentAddressIndex(session.dbAccountId) } returns 5
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns 12
        val store = store()

        store.raiseFromHistory(session)

        assertEquals(5, store.issueIndex(ACCOUNT_ID))
    }

    @Test
    fun raiseFromHistory_noHistory_leavesTheIndexAbsent() = runTest {
        val session = session()
        coEvery { wallet.highestUsedTransparentAddressIndex(session.dbAccountId) } returns null
        val store = store()

        store.raiseFromHistory(session)

        assertNull(store.issueIndex(ACCOUNT_ID))
    }

    @Test
    fun clear_bothValuesStored_erasesBoth() = runTest {
        val session = session()
        coEvery { wallet.highestStoredTransparentAddressIndex(session.dbAccountId) } returns null
        val store = store()
        store.raise(ACCOUNT_ID, ISSUED)
        store.prepare(session)

        store.clear(ACCOUNT_ID)

        assertNull(store.issueIndex(ACCOUNT_ID))
        assertNull(store.recreatedThrough(ACCOUNT_ID))
    }

    private fun row(address: String) =
        ZcashSingleUseAddress(accountId = ACCOUNT_ID, address = address, useCount = 1, hadBalance = false)

    private companion object {
        // The account id zcashSession() opens.
        const val ACCOUNT_ID = "account"
        const val ISSUED = 40
    }
}
