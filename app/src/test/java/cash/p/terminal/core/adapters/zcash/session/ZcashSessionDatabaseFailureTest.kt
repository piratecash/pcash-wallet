package cash.p.terminal.core.adapters.zcash.session

import cash.p.zcash.AccountInfo
import cash.p.zcash.SyncStage
import cash.p.zcash.SyncState
import cash.p.zcash.ZcashException
import io.horizontalsystems.core.logger.AppLogger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private const val DB_ACCOUNT_ID = 7
private const val OTHER_BIRTH_HEIGHT = 2_500_000
private const val BIRTH_HEIGHT = 3_428_200
private const val DATABASE_FAILURE = "error returned from database: (code: 14) unable to open database file"

class ZcashSessionDatabaseFailureTest {

    private val wallet = zcashWalletMock()

    private val logger = mockk<AppLogger>(relaxed = true)

    private fun TestScope.session() = zcashSession(wallet, logger = logger)

    private fun account(id: Int, birthHeight: Int) = AccountInfo(
        id = id,
        name = "Test",
        birthHeight = birthHeight,
        accountIndex = 0,
        diversifierIndex = 0,
        position = 0,
        height = 0,
        time = 0L,
        balance = 0L,
        hidden = false,
        enabled = true,
        internal = false,
        hardwareWallet = false,
    )

    @Test
    fun refresh_databaseFailure_publishesFailedStateWithTheSameError() = runTest {
        val failure = ZcashException(DATABASE_FAILURE)
        coEvery { wallet.balance(any(), any()) } throws failure
        val session = session()

        assertEquals(ZcashSessionResult.Success(Unit), session.refresh())

        assertSame(failure, (session.state.value.syncState as SyncState.Failed).error)
    }

    @Test
    fun sync_databaseFailure_publishesTheFailureAtOnce() = runTest {
        val failure = ZcashException(DATABASE_FAILURE, stage = SyncStage.SCAN)
        every { wallet.sync(any()) } returns flow { throw failure }
        val session = session()

        val result = session.sync()

        assertEquals(ZcashSessionResult.Success(ZcashPassOutcome.Failed(failure, published = true)), result)
        assertSame(failure, (session.state.value.syncState as SyncState.Failed).error)
    }

    @Test
    fun refresh_databaseFailure_logsFilesystemDiagnostics() = runTest {
        coEvery { wallet.balance(any(), any()) } throws ZcashException(DATABASE_FAILURE)
        val databaseFiles = mockk<ZcashDatabaseFiles> { every { diagnostics(any()) } returns "db-fs: test" }

        zcashSession(wallet, logger = logger, databaseFiles = databaseFiles).refresh()

        verify(exactly = 1) { logger.warning("db-fs: test") }
    }

    @Test
    fun refresh_success_keepsSyncState() = runTest {
        val session = session()
        val before = session.state.value.syncState

        session.refresh()

        assertEquals(before, session.state.value.syncState)
    }

    @Test
    fun accountBirthHeight_databaseFailure_publishesFailedStateWithTheSameError() = runTest {
        val failure = ZcashException(DATABASE_FAILURE)
        coEvery { wallet.accounts() } throws failure
        val session = session()

        assertNull(session.accountBirthHeight())

        assertSame(failure, (session.state.value.syncState as SyncState.Failed).error)
    }

    @Test
    fun accountBirthHeight_databaseFailure_logsFilesystemDiagnostics() = runTest {
        coEvery { wallet.accounts() } throws ZcashException(DATABASE_FAILURE)
        val databaseFiles = mockk<ZcashDatabaseFiles> { every { diagnostics(any()) } returns "db-fs: test" }

        zcashSession(wallet, logger = logger, databaseFiles = databaseFiles).accountBirthHeight()

        verify(exactly = 1) { logger.warning("db-fs: test") }
    }

    @Test
    fun accountBirthHeight_matchingAccount_returnsItsBirthHeight() = runTest {
        coEvery { wallet.accounts() } returns listOf(
            account(id = DB_ACCOUNT_ID + 1, birthHeight = OTHER_BIRTH_HEIGHT),
            account(id = DB_ACCOUNT_ID, birthHeight = BIRTH_HEIGHT),
        )

        assertEquals(BIRTH_HEIGHT, session().accountBirthHeight())
    }
}
