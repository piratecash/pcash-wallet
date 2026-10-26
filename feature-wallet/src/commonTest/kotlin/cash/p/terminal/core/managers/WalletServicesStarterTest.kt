package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.IAdapterManager
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WalletServicesStarterTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val chainAccountManagersStarter = mockk<ChainAccountManagersStarter>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val nftMetadataSyncer = mockk<NftMetadataSyncer>(relaxed = true)
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
            nftMetadataSyncer.start()
            locallyCreatedTransactionRepository.trimAllAccounts()
            addressLabelManager.initialize()
            contactsRepository.initialize()
            evmLabelManager.sync()
        }
    }

    // A scope of its own: runTest would otherwise wait out the delayed cleanup.
    private fun starter() = WalletServicesStarter(
        chainAccountManagersStarter = chainAccountManagersStarter,
        adapterManager = adapterManager,
        nftMetadataSyncer = nftMetadataSyncer,
        locallyCreatedTransactionRepository = locallyCreatedTransactionRepository,
        addressLabelManager = addressLabelManager,
        contactsRepository = contactsRepository,
        evmLabelManager = evmLabelManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    )
}
