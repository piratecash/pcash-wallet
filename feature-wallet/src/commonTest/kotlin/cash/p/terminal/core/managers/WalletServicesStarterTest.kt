package cash.p.terminal.core.managers

import cash.p.terminal.core.KoinLogsDao
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.IAdapterManager
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class WalletServicesStarterTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val chainAccountManagersStarter = mockk<ChainAccountManagersStarter>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val evmLabelManager = mockk<EvmLabelManager>(relaxed = true)

    @Test
    fun start_allSteps_runInAndroidStartupOrder() = runTest(dispatcher) {
        starter().start()

        coVerifyOrder {
            chainAccountManagersStarter.start()
            adapterManager.startAdapterManager()
            locallyCreatedTransactionRepository.trimAllAccounts()
            addressLabelManager.initialize()
            contactsRepository.initialize()
            evmLabelManager.sync()
        }
    }

    @Test
    fun scheduleAccountDataCleanup_always_cleansTheOldJournalRows() {
        val trimmed = CountDownLatch(1)
        val logsDao = mockk<LogsDao>(relaxed = true) { every { trimToNewest(any()) } answers { trimmed.countDown() } }

        KoinLogsDao(logsDao).use {
            starter().scheduleAccountDataCleanup()

            assertTrue(trimmed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
        verify { logsDao.deleteOlderThan(any()) }
    }

    // A scope of its own: runTest would otherwise wait out the delayed cleanup.
    private fun starter() = WalletServicesStarter(
        chainAccountManagersStarter = chainAccountManagersStarter,
        adapterManager = adapterManager,
        locallyCreatedTransactionRepository = locallyCreatedTransactionRepository,
        addressLabelManager = addressLabelManager,
        contactsRepository = contactsRepository,
        evmLabelManager = evmLabelManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    )

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
