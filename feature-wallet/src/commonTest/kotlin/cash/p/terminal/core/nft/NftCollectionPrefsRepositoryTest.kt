package cash.p.terminal.core.nft

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.NftCollectionPrefStorage
import cash.p.terminal.entities.NftCollectionPrefRecord
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.entities.NftUserTrust
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class NftCollectionPrefsRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val rows = MutableStateFlow<List<NftCollectionPrefRecord>>(emptyList())
    private var failNextUpsert = false
    private var failDelete = false

    private val storage = mockk<NftCollectionPrefStorage> {
        every { observe(any()) } answers {
            val accountId = firstArg<String>()
            rows.map { all -> all.filter { it.accountId == accountId } }
        }
        coEvery { get(any(), any(), any()) } answers {
            val key = NftCollectionPrefKey(secondArg(), thirdArg())
            rows.value.firstOrNull { it.accountId == firstArg<String>() && it.key == key }
        }
        coEvery { upsert(any()) } answers {
            if (failNextUpsert) {
                failNextUpsert = false
                throw IOException("disk full")
            }
            val record = firstArg<NftCollectionPrefRecord>()
            rows.value = rows.value.filterNot { it.key == record.key && it.accountId == record.accountId } + record
        }
        coEvery { deleteByAccountIds(any()) } answers {
            if (failDelete) throw IOException("disk full")
            val ids = firstArg<List<String>>()
            rows.value = rows.value.filterNot { it.accountId in ids }
        }
    }

    @Test
    fun setHidden_hideThenImmediateUndo_endsVisible() = test {
        val repository = repository()

        repository.setHidden(ITEM, true)
        repository.setHidden(ITEM, false)
        runCurrent()

        assertEquals(false, repository.prefOf(ITEM)?.hidden)
    }

    @Test
    fun markNotSpam_afterSpamAndHide_clearsBoth() = test {
        val repository = repository()
        repository.setHidden(ITEM, true)
        repository.markSpam(ITEM)

        repository.markNotSpam(ITEM)
        runCurrent()

        assertEquals(pref(hidden = false, userTrust = NftUserTrust.Trust), repository.prefOf(ITEM))
    }

    @Test
    fun setHidden_afterNotSpam_keepsTrustMark() = test {
        val repository = repository()
        repository.markNotSpam(ITEM)

        repository.setHidden(ITEM, true)
        runCurrent()

        assertEquals(pref(hidden = true, userTrust = NftUserTrust.Trust), repository.prefOf(ITEM))
    }

    @Test
    fun setHidden_itemWithoutCollection_storedUnderItemAddressForItsAccount() = test {
        val repository = repository()
        val loose = ITEM.copy(accountId = OTHER_ACCOUNT, collectionAddress = null)

        repository.setHidden(loose, true)
        runCurrent()

        assertEquals(
            mapOf(NftCollectionPrefKey(BlockchainType.Ton, loose.address) to pref(OTHER_ACCOUNT, loose.address, true)),
            repository.prefsFlow(OTHER_ACCOUNT).first(),
        )
        assertTrue(repository.prefsFlow(ACCOUNT).first().isEmpty())
    }

    @Test
    fun deleteByAccountIds_writeQueuedBefore_leavesNoRows() = test {
        val repository = repository()
        repository.setHidden(ITEM, true)

        repository.deleteByAccountIds(listOf(ACCOUNT))

        assertTrue(rows.value.isEmpty())
    }

    @Test
    fun deleteByAccountIds_storageFails_propagatesFailure() = test {
        val repository = repository()
        failDelete = true

        var thrown: Throwable? = null
        try {
            repository.deleteByAccountIds(listOf(ACCOUNT))
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull(thrown)
    }

    @Test
    fun setHidden_previousWriteFailed_stillApplied() = test {
        val repository = repository()
        failNextUpsert = true
        repository.markSpam(ITEM)

        repository.setHidden(ITEM, true)
        runCurrent()

        assertEquals(pref(hidden = true, userTrust = NftUserTrust.None), repository.prefOf(ITEM))
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher, timeout = 10.seconds, testBody = block)

    private fun TestScope.repository() =
        NftCollectionPrefsRepository(storage, TestDispatcherProvider(dispatcher, backgroundScope))

    private suspend fun NftCollectionPrefsRepository.prefOf(item: NftItemRecord) =
        prefsFlow(item.accountId).first()[item.collectionPrefKey]

    private fun pref(
        accountId: String = ACCOUNT,
        collectionKey: String = COLLECTION,
        hidden: Boolean,
        userTrust: NftUserTrust = NftUserTrust.None,
    ) = NftCollectionPrefRecord(accountId, BlockchainType.Ton, collectionKey, hidden, userTrust)

    private companion object {
        const val ACCOUNT = "account-1"
        const val OTHER_ACCOUNT = "account-2"
        const val COLLECTION = "collection-a"

        val ITEM = NftItemRecord(
            accountId = ACCOUNT,
            blockchainType = BlockchainType.Ton,
            ownerAddress = "owner",
            address = "nft-1",
            position = 0,
            name = null,
            description = null,
            realOwnerAddress = null,
            collectionAddress = COLLECTION,
            collectionName = null,
            collectionDescription = null,
            trust = NftTrust.Graylist,
            verified = false,
            imageUrl = null,
            largeImageUrl = null,
        )
    }
}
