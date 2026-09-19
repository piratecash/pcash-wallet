package cash.p.terminal.domain.usecase

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.KeyStoreCleaner
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.widgets.MarketWidgetWorker
import cash.p.terminal.widgets.MarketWidget
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException
import kotlin.test.assertFailsWith

class ResetUseCaseTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context = mockk<Context>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val contacts = mockk<ContactsRepository>(relaxed = true)
    private val glance = mockk<GlanceAppWidgetManager>(relaxed = true)
    private val icons = mockk<AppIconService>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>(relaxed = true)
    private val keyStoreCleaner = mockk<KeyStoreCleaner>(relaxed = true)

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun invoke_intentOrBeamCleanupFails_hasNoGlobalDestructiveEffects() = runTest {
        mockkObject(WCDelegate)
        coEvery { preflight.prepareExplicitReset() } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { reset()() }

        verify {
            listOf(WCDelegate, context, localStorage, database, keyStoreCleaner, contacts, glance, icons) wasNot Called
        }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_keystoreBarrierBlocks_stopsBeforeDatabaseOrFilePurge() = runTest {
        prepareReset()
        coEvery { keyStoreCleaner.cleanExplicitReset() } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { reset()() }

        coVerifyOrder {
            preflight.prepareExplicitReset()
            WCDelegate.getActiveSessions()
            keyStoreCleaner.cleanExplicitReset()
        }
        verify { listOf(context, database, contacts, glance, icons) wasNot Called }
        verify(exactly = 0) { localStorage.mainShowedOnce = any() }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_keystoreResetCancelled_doesNotContinuePurge() = runTest {
        prepareReset()
        coEvery { keyStoreCleaner.cleanExplicitReset() } throws CancellationException()

        assertFailsWith<CancellationException> { reset()() }

        verify { listOf(context, database, contacts, glance, icons) wasNot Called }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_globalLinkageFailure_doesNotClearDatabaseOrIntent() = runTest {
        prepareReset()
        coEvery { keyStoreCleaner.cleanExplicitReset() } throws IOException("linkage")

        assertFailsWith<IOException> { reset()() }

        verify { database wasNot Called }
        coVerify(exactly = 0) { preflight.finishExplicitReset() }
    }

    @Test
    fun invoke_appDatabaseFailure_doesNotRemoveResetIntent() = runTest {
        prepareReset()
        every { database.clearAllTables() } throws IOException("database")

        assertFailsWith<IOException> { reset()() }

        coVerify(exactly = 0) { preflight.finishExplicitReset() }
        verify { listOf(context, contacts, glance) wasNot Called }
    }

    @Test
    fun invoke_cleanupComplete_finishesMarkerOnlyAfterGlobalPurge() = runTest {
        prepareFilePurge()

        reset()()

        coVerifyOrder {
            preflight.prepareExplicitReset()
            keyStoreCleaner.cleanExplicitReset()
            database.clearAllTables()
            contacts.clear()
            preflight.finishExplicitReset()
        }
        coVerify(exactly = 1) { contacts.clear() }
        coVerify(exactly = 0) { preflight.ensureCanReset() }
        verify { context.deleteDatabase("logging_database") }
    }

    // The global clear already ran and the BEAM wrappers are shredded, so a retained marker only
    // defers a leftover file to startup cleanup; it must not surface as a failed reset.
    @Test
    fun invoke_markerRemovalFails_completesGlobalClearWithoutFailing() = runTest {
        prepareFilePurge()
        coEvery { preflight.finishExplicitReset() } throws AccountDeletionBlockedException()

        reset()()

        coVerifyOrder {
            keyStoreCleaner.cleanExplicitReset()
            database.clearAllTables()
            preflight.finishExplicitReset()
        }
        verify { context.deleteDatabase("logging_database") }
    }

    @Test
    fun invoke_markerRemovalCancelled_propagatesCancellation() = runTest {
        prepareFilePurge()
        coEvery { preflight.finishExplicitReset() } throws CancellationException()

        assertFailsWith<CancellationException> { reset()() }

        coVerify { keyStoreCleaner.cleanExplicitReset() }
    }

    private fun prepareReset() {
        mockkObject(WCDelegate)
        every { WCDelegate.getActiveSessions() } returns emptyList()
        every { WCDelegate.deleteAllPairings() } returns Unit
        every { localStorage.appIcon } returns null
    }

    private fun prepareFilePurge() {
        prepareReset()
        every { context.filesDir } returns temporaryFolder.root
        every { context.getDir(any(), any()) } returns temporaryFolder.newFolder("tor")
        mockkObject(MarketWidgetWorker.Companion)
        every { MarketWidgetWorker.cancel(context) } returns Unit
        coEvery { glance.getGlanceIds(MarketWidget::class.java) } returns emptyList()
    }

    private fun TestScope.reset() = ResetUseCase(
        context, localStorage, database, contacts,
        TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
        glance, icons, preflight, keyStoreCleaner,
    )
}
