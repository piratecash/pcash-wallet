package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashTransparentIndexStore
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.managers.RestoreSettingsTestFixture
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.zcash.TransparentCoverage
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ZcashAddressSearchTest {

    private val settings = RestoreSettingsTestFixture()
    private val wallet = zcashWalletMock()

    private fun TestScope.dispatcherProvider() =
        TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope)

    private fun TestScope.indexStore() = ZcashTransparentIndexStore(
        storage = settings.storage,
        issuedAddresses = mockk(relaxed = true),
        dispatcherProvider = dispatcherProvider(),
    )

    private fun TestScope.search() = ZcashAddressSearch(
        storage = settings.storage,
        transparentIndexStore = indexStore(),
        dispatcherProvider = dispatcherProvider(),
    )

    private suspend fun ZcashAddressSearch.current() = state(ACCOUNT_ID).first()

    /** A search whose cursor is where a deep walk past a used index 4 stopped. */
    private suspend fun TestScope.searchAtCursor(session: ZcashSession): ZcashAddressSearch {
        coEvery { wallet.transparentCoverage(session.dbAccountId) } returns TransparentCoverage(true, DEEP_GAP, 0)
        coEvery { wallet.highestUsedTransparentAddressIndex(session.dbAccountId) } returns 4
        return search().also { it.runStep(session) }
    }

    @Test
    fun runStep_noCursorYet_startsWhereTheCertifiedWalkStopped() = runTest {
        val search = searchAtCursor(zcashSession(wallet))

        val state = assertNotNull(search.current())
        assertEquals(CURSOR, state.checkedThrough)
        assertFalse(state.running)
        coVerify(exactly = 0) { wallet.discoverTransparentAddressRange(any(), any(), any(), any()) }
    }

    @Test
    fun runStep_walkNotCertified_leavesTheCursorUnknown() = runTest {
        val session = zcashSession(wallet)
        coEvery { wallet.transparentCoverage(session.dbAccountId) } returns TransparentCoverage(false, DEEP_GAP, 1)
        val search = search()

        search.runStep(session)

        assertNull(search.current())
    }

    @Test
    fun runStep_noTransparentKey_touchesNothing() = runTest {
        val search = search()

        assertEquals(ZcashAddressSearchStep.IDLE, search.runStep(zcashSession(wallet, supportsTransparent = false)))

        coVerify(exactly = 0) { wallet.transparentCoverage(any()) }
        assertNull(search.current())
    }

    @Test
    fun runStep_targetPending_checksOneChunkAndPersistsIt() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 250)
        coEvery { wallet.discoverTransparentAddressRange(session.dbAccountId, CURSOR, CURSOR + 100, any()) } returns 2

        assertEquals(ZcashAddressSearchStep.MORE_PENDING, search.runStep(session))

        val state = assertNotNull(search.current())
        assertEquals(CURSOR + 100, state.checkedThrough)
        assertEquals(CURSOR + 250, state.target)
        assertEquals(2, state.found)
        assertNotNull(state.checkedAt)
        assertTrue(state.running)
    }

    @Test
    fun runStep_chunkFindsHistory_raisesTheIssueIndexBeforeRecordingTheChunk() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 250)
        var checkedWhileRaising: Int? = null
        coEvery { wallet.highestUsedTransparentAddressIndex(session.dbAccountId) } coAnswers {
            checkedWhileRaising = search.current()?.checkedThrough
            FOUND_INDEX
        }

        search.runStep(session)

        assertEquals(CURSOR, checkedWhileRaising)
        assertEquals(FOUND_INDEX, indexStore().issueIndex(ACCOUNT_ID))
        assertEquals(CURSOR + 100, assertNotNull(search.current()).checkedThrough)
    }

    @Test
    fun runStep_indexRaiseFails_leavesTheChunkUnrecorded() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 250)
        coEvery { wallet.highestUsedTransparentAddressIndex(session.dbAccountId) } throws IOException("boom")

        assertFailsWith<IOException> { search.runStep(session) }

        val state = assertNotNull(search.current())
        assertEquals(CURSOR, state.checkedThrough)
        assertNull(state.checkedAt)
    }

    @Test
    fun runStep_lastChunk_stopsAtTheTarget() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 30)

        assertEquals(ZcashAddressSearchStep.CHECKED, search.runStep(session))

        coVerify { wallet.discoverTransparentAddressRange(session.dbAccountId, CURSOR, CURSOR + 30, any()) }
        assertFalse(assertNotNull(search.current()).running)
    }

    @Test
    fun runStep_chunkFails_doesNotAdvance() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 250)
        coEvery { wallet.discoverTransparentAddressRange(any(), any(), any(), any()) } throws IOException("boom")

        assertFailsWith<IOException> { search.runStep(session) }

        val state = assertNotNull(search.current())
        assertEquals(CURSOR, state.checkedThrough)
        assertNull(state.checkedAt)
        assertTrue(state.running)
    }

    @Test
    fun runStep_newInstanceOverTheSameStorage_continuesFromThePersistedPoint() = runTest {
        val session = zcashSession(wallet)
        val first = searchAtCursor(session)
        first.start(ACCOUNT_ID, 250)
        first.runStep(session)

        val second = search()
        second.runStep(session)

        coVerify { wallet.discoverTransparentAddressRange(session.dbAccountId, CURSOR + 100, CURSOR + 200, any()) }
        assertEquals(CURSOR + 200, assertNotNull(second.current()).checkedThrough)
    }

    @Test
    fun stop_duringAChunk_keepsTheCheckedPart() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 250)
        coEvery { wallet.discoverTransparentAddressRange(any(), any(), any(), any()) } coAnswers {
            search.stop(ACCOUNT_ID)
            1
        }

        assertEquals(ZcashAddressSearchStep.CHECKED, search.runStep(session))

        val state = assertNotNull(search.current())
        assertEquals(CURSOR + 100, state.checkedThrough)
        assertEquals(1, state.found)
        assertFalse(state.running)
    }

    @Test
    fun checkAllAgain_afterAChunk_restartsFromZeroUpToWhatWasChecked() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 30)
        coEvery { wallet.discoverTransparentAddressRange(any(), any(), any(), any()) } returns 3
        search.runStep(session)

        search.checkAllAgain(ACCOUNT_ID)

        val state = assertNotNull(search.current())
        assertEquals(0, state.checkedThrough)
        assertEquals(0, state.found)
        assertEquals(CURSOR + 30, state.target)
        search.runStep(session)
        coVerify { wallet.discoverTransparentAddressRange(session.dbAccountId, 0, 100, any()) }
    }

    @Test
    fun clear_searchInProgress_forgetsIt() = runTest {
        val search = searchAtCursor(zcashSession(wallet))
        search.start(ACCOUNT_ID, 250)

        search.clear(ACCOUNT_ID)

        assertNull(search.current())
    }

    @Test
    fun runStep_searchProgressStored_staysOutOfTheBackedUpSettings() = runTest {
        val session = zcashSession(wallet)
        val search = searchAtCursor(session)
        search.start(ACCOUNT_ID, 250)
        search.runStep(session)
        val account = Account(ACCOUNT_ID, "Zcash", MNEMONIC, AccountOrigin.Restored, level = 0, isBackedUp = true)

        // The chunk's index raise is backed up on purpose; nothing of the search itself may be.
        assertEquals(
            setOf(RestoreSettingType.ZcashTransparentIssueIndex),
            settings.manager().settings(account, BlockchainType.Zcash).values.keys,
        )
    }

    @Test
    fun longSearchMinutes_aboveTheThreshold_roundsTheEstimateUp() {
        assertNull(ZcashAddressSearch.longSearchMinutes(ZcashAddressSearch.LONG_SEARCH_THRESHOLD))
        assertEquals(3, ZcashAddressSearch.longSearchMinutes(3_001))
    }

    private companion object {
        const val ACCOUNT_ID = "account"
        const val DEEP_GAP = 500
        const val CURSOR = 4 + 1 + DEEP_GAP
        const val FOUND_INDEX = CURSOR + 42
        val MNEMONIC = AccountType.Mnemonic(List(11) { "abandon" } + "about", "")
    }
}
