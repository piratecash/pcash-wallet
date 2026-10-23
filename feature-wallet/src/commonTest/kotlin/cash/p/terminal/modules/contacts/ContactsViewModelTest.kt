package cash.p.terminal.modules.contacts

import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.contacts.viewmodel.ContactsViewModel
import cash.p.terminal.wallet.MarketKitWrapper
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val repository = ContactsRepository(mockk<MarketKitWrapper>(relaxed = true), InMemoryContactsFileStorage())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restore_onOpenSettingsScreen_uiStateCarriesRestoredContactsForBackupAndWarning() {
        val viewModel = ContactsViewModel(repository, Mode.Full)
        assertFalse(viewModel.uiState.hasContacts)

        viewModel.restore("""[{"uid":"uid-1","name":"Alice","addresses":[]}]""")

        assertTrue(viewModel.uiState.hasContacts)
        assertEquals(
            listOf(Contact(uid = "uid-1", name = "Alice", addresses = emptyList())),
            repository.parseFromJson(viewModel.uiState.backupJson)
        )
    }

    private class InMemoryContactsFileStorage : ContactsFileStorage {
        private var json: String? = null
        override fun read(): String? = json
        override fun write(json: String) {
            this.json = json
        }
    }
}
