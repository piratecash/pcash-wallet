package cash.p.terminal.core.nft

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.storage.NftItemStorage
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NftCollectionsRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // Like Room, a StateFlow stays silent when the written list equals the current one.
    private val rows = MutableStateFlow<List<NftItemRecord>>(emptyList())
    private val storage = mockk<NftItemStorage> {
        every { observe(any(), any()) } returns rows
        coEvery { replace(any(), any(), any(), any()) } answers {
            val owner = thirdArg<String>()
            rows.value = rows.value.filterNot { it.ownerAddress == owner } + arg<List<NftItemRecord>>(3)
        }
    }
    private val paused = MutableStateFlow<Set<OfflineKey>>(emptySet())
    private val offlineModeManager = mockk<OfflineModeManager> {
        every { isNetworkPaused(any<OfflineKey>()) } returns false
        every { isForgotten(any()) } returns false
        every { effectiveFlow } returns paused
    }
    private val provider = mockk<NftProvider> {
        every { blockchainType } returns BlockchainType.Ton
    }
    private val repository = NftCollectionsRepository(
        storage,
        listOf(provider),
        offlineModeManager,
        TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    )

    @Test
    fun itemsFlow_fetchStillRunning_emitsCachedRows() = test {
        val cached = listOf(record("cached"))
        rows.value = cached
        val fetchGate = CompletableDeferred<List<NftItemRecordDraft>?>()
        coEvery { provider.fetch(ADDRESS) } coAnswers { fetchGate.await() }
        val refresh = launch { repository.refresh(setOf(OWNER), force = true) }

        val state = firstState(setOf(OWNER))

        assertEquals(cached, state.items)
        assertTrue(state.isLoaded)
        coVerify(exactly = 0) { storage.replace(any(), any(), any(), any()) }
        fetchGate.complete(null)
        refresh.join()
    }

    @Test
    fun refresh_accountForgottenDuringFetch_deletesWrittenRows() = test {
        coEvery { provider.fetch(ADDRESS) } returns listOf(draft("a"))
        every { offlineModeManager.isForgotten(ACCOUNT_ID) } returns true
        coEvery { storage.deleteByAccountIds(any()) } returns Unit

        repository.refresh(setOf(OWNER), force = true)

        coVerify { storage.deleteByAccountIds(listOf(ACCOUNT_ID)) }
    }

    @Test
    fun refresh_accountNotForgotten_keepsWrittenRows() = test {
        coEvery { provider.fetch(ADDRESS) } returns listOf(draft("a"))

        repository.refresh(setOf(OWNER), force = true)

        coVerify(exactly = 0) { storage.deleteByAccountIds(any()) }
    }

    @Test
    fun networkResumed_pausedThenOnline_emits() = test {
        paused.value = setOf(KEY)
        val emissions = collectResumes()

        paused.value = emptySet()

        assertEquals(1, emissions.size)
    }

    @Test
    fun networkResumed_startsOnlineAndStaysOnline_doesNotEmit() = test {
        val emissions = collectResumes()

        paused.value = setOf(OfflineKey("other-account", BlockchainType.Ton))

        assertTrue(emissions.isEmpty())
    }

    @Test
    fun networkResumed_goesOffline_doesNotEmit() = test {
        val emissions = collectResumes()

        paused.value = setOf(KEY)

        assertTrue(emissions.isEmpty())
    }

    @Test
    fun refresh_fetchFails_keepsRowsAndRetriesWithoutThrottle() = test {
        val cached = listOf(record("cached"))
        rows.value = cached
        coEvery { provider.fetch(ADDRESS) } throws IllegalStateException("boom")

        repository.refresh(setOf(OWNER), force = false)
        repository.refresh(setOf(OWNER), force = false)

        assertEquals(cached, firstState(setOf(OWNER)).items)
        coVerify(exactly = 0) { storage.replace(any(), any(), any(), any()) }
        coVerify(exactly = 2) { provider.fetch(ADDRESS) }
    }

    @Test
    fun refresh_fetchCancelled_rethrows() = test {
        coEvery { provider.fetch(ADDRESS) } throws CancellationException()

        assertFailsWith<CancellationException> { repository.refresh(setOf(OWNER), force = true) }
    }

    @Test
    fun refresh_oneOwnerFails_otherOwnerStillFetched() = test {
        coEvery { provider.fetch(ADDRESS) } throws IllegalStateException("boom")
        coEvery { provider.fetch(OTHER_ADDRESS) } returns listOf(draft("b"))

        repository.refresh(setOf(OWNER, OTHER_OWNER), force = true)

        assertEquals(listOf("b"), rows.value.map { it.address })
    }

    @Test
    fun refresh_networkPaused_makesNoFetch() = test {
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Ton)) } returns true

        repository.refresh(setOf(OWNER), force = true)

        coVerify(exactly = 0) { provider.fetch(any()) }
    }

    @Test
    fun refresh_notForcedWithinWindow_skipsButForceFetches() = test {
        coEvery { provider.fetch(ADDRESS) } returns listOf(draft("a"))

        repository.refresh(setOf(OWNER), force = false)
        repository.refresh(setOf(OWNER), force = false)
        coVerify(exactly = 1) { provider.fetch(ADDRESS) }

        repository.refresh(setOf(OWNER), force = true)
        coVerify(exactly = 2) { provider.fetch(ADDRESS) }
    }

    @Test
    fun refresh_fetchReturnsNull_leavesCacheAndRetriesAndStaysNotFetched() = test {
        coEvery { provider.fetch(ADDRESS) } returns null

        repository.refresh(setOf(OWNER), force = false)
        repository.refresh(setOf(OWNER), force = false)

        coVerify(exactly = 2) { provider.fetch(ADDRESS) }
        coVerify(exactly = 0) { storage.replace(any(), any(), any(), any()) }
        assertFalse(firstState(setOf(OWNER)).isLoaded)
    }

    @Test
    fun refresh_emptySuccess_clearsRowsAndLoads() = test {
        rows.value = listOf(record("sold"))
        coEvery { provider.fetch(ADDRESS) } returns emptyList()

        repository.refresh(setOf(OWNER), force = true)

        val state = firstState(setOf(OWNER))
        assertTrue(state.items.isEmpty())
        assertTrue(state.isLoaded)
    }

    @Test
    fun refresh_success_storesDraftsUnderOwnerInResponseOrder() = test {
        coEvery { provider.fetch(ADDRESS) } returns listOf(draft("second"), draft("first"))

        repository.refresh(setOf(OWNER), force = true)

        coVerify {
            storage.replace(
                ACCOUNT_ID,
                BlockchainType.Ton,
                ADDRESS,
                listOf(record("second", position = 0), record("first", position = 1)),
            )
        }
    }

    @Test
    fun itemsFlow_emptySuccessOverEmptyCache_collectorReceivesLoaded() = test {
        coEvery { provider.fetch(ADDRESS) } returns emptyList()
        val states = collect(repository.itemsFlow(setOf(OWNER)))
        assertEquals(listOf(NftItemsState(emptyList(), isLoaded = false)), states)

        repository.refresh(setOf(OWNER), force = true)

        assertEquals(NftItemsState(emptyList(), isLoaded = true), states.last())
    }

    @Test
    fun itemsFlow_failedFetchOverEmptyCache_staysNotLoaded() = test {
        coEvery { provider.fetch(ADDRESS) } throws IllegalStateException("boom")
        val states = collect(repository.itemsFlow(setOf(OWNER)))

        repository.refresh(setOf(OWNER), force = true)

        assertFalse(states.last().isLoaded)
    }

    @Test
    fun itemsFlow_onlyOneOfTwoOwnersFetched_staysNotLoaded() = test {
        coEvery { provider.fetch(ADDRESS) } returns emptyList()

        repository.refresh(setOf(OWNER), force = true)

        assertFalse(firstState(setOf(OWNER, OTHER_OWNER)).isLoaded)
    }

    @Test
    fun itemsFlow_noOwners_emitsLoadedWithoutQuery() = test {
        assertEquals(NftItemsState(emptyList(), isLoaded = true), firstState(emptySet()))

        verify(exactly = 0) { storage.observe(any(), any()) }
    }

    @Test
    fun itemsFlow_rowOfSameAddressOnAnotherBlockchain_isFilteredOut() = test {
        rows.value = listOf(record("foreign", blockchainType = BlockchainType.Bitcoin), record("own"))

        assertEquals(listOf("own"), firstState(setOf(OWNER)).items.map { it.address })
    }

    @Test
    fun itemsFlow_sameNftUnderTwoOwners_keepsFirstInQueryOrder() = test {
        val winner = record("dup", position = 0)
        rows.value = listOf(winner, record("dup", ownerAddress = OTHER_ADDRESS, position = 0))

        assertEquals(listOf(winner), firstState(setOf(OWNER, OTHER_OWNER)).items)
    }

    @Test
    fun expiringDomains_networkPaused_returnsNullWithoutRequest() = test {
        every { offlineModeManager.isNetworkPaused(KEY) } returns true

        assertNull(repository.expiringDomains(setOf(OWNER), 30))
        coVerify(exactly = 0) { provider.expiringDomains(any(), any()) }
    }

    @Test
    fun expiringDomains_requestFails_returnsNullAndLogsOnlyExceptionType() = test {
        coEvery { provider.expiringDomains(ADDRESS, 30) } throws IllegalStateException("https://tonapi/$ADDRESS")
        val logs = CapturingLogWriter()
        val previousWriters = Logger.config.logWriterList
        Logger.setLogWriters(logs)
        val result = try {
            repository.expiringDomains(setOf(OWNER), 30)
        } finally {
            Logger.setLogWriters(previousWriters)
        }

        assertNull(result)
        assertEquals(listOf("NFT domain expiry failed for the-open-network: IllegalStateException"), logs.messages)
    }

    @Test
    fun expiringDomains_requestCancelled_rethrows() = test {
        coEvery { provider.expiringDomains(ADDRESS, 30) } throws CancellationException()

        assertFailsWith<CancellationException> { repository.expiringDomains(setOf(OWNER), 30) }
    }

    @Test
    fun expiringDomains_twoOwners_mergedAndSortedByExpiry() = test {
        coEvery { provider.expiringDomains(ADDRESS, 30) } returns listOf(domain("a3", 300), domain("a1", 100))
        coEvery { provider.expiringDomains(OTHER_ADDRESS, 30) } returns listOf(domain("b2", 200))

        val result = repository.expiringDomains(setOf(OWNER, OTHER_OWNER), 30)

        assertEquals(listOf("a1", "b2", "a3"), result?.map { it.nftAddress })
        assertEquals(listOf(OWNER, OTHER_OWNER, OWNER), result?.map { it.owner })
    }

    @Test
    fun expiringDomains_oneOwnerFails_returnsOtherOwnerDomains() = test {
        coEvery { provider.expiringDomains(ADDRESS, 30) } throws IllegalStateException("boom")
        coEvery { provider.expiringDomains(OTHER_ADDRESS, 30) } returns listOf(domain("b", 200))

        assertEquals(
            listOf(NftDomainExpiry(OTHER_OWNER, "b", "b.ton", 200, false)),
            repository.expiringDomains(setOf(OWNER, OTHER_OWNER), 30),
        )
    }

    @Test
    fun expiringDomains_allOwnersUnavailable_returnsNull() = test {
        coEvery { provider.expiringDomains(any(), 30) } returns null

        assertNull(repository.expiringDomains(setOf(OWNER, OTHER_OWNER), 30))
    }

    @Test
    fun domainExpiryTriggers_kitAppearedNewEventAndNetworkResume_eachEmit() = test {
        val changes = MutableSharedFlow<NftChange>()
        every { provider.changes(ADDRESS) } returns changes
        paused.value = setOf(KEY)
        val emissions = mutableListOf<Unit>()
        backgroundScope.launch { repository.domainExpiryTriggers(setOf(OWNER)).toList(emissions) }

        changes.emit(NftChange.KitAppeared)
        changes.emit(NftChange.NewEvent)
        paused.value = emptySet()

        assertEquals(3, emissions.size)
    }

    @Test
    fun owners_walletsOfOneChain_buildsOwnersWithAccount() = test {
        val wallet = wallet()
        coEvery { provider.ownerAddresses(listOf(wallet)) } returns setOf(ADDRESS)

        assertEquals(setOf(OWNER), repository.owners(listOf(wallet)))
        assertEquals(OWNER, repository.ownerFor(wallet))
    }

    @Test
    fun owners_walletWithoutProvider_isIgnored() = test {
        assertTrue(repository.owners(listOf(wallet(BlockchainType.Bitcoin))).isEmpty())
    }

    @Test
    fun supportsSection_walletWithoutProvider_returnsFalse() {
        assertFalse(repository.supportsSection(wallet(BlockchainType.Bitcoin)))
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher, timeout = 10.seconds, testBody = block)

    private suspend fun firstState(owners: Set<NftOwner>): NftItemsState =
        withTimeout(5.seconds) { repository.itemsFlow(owners).first() }

    private fun TestScope.collectResumes(): List<Unit> {
        val emissions = mutableListOf<Unit>()
        backgroundScope.launch { repository.networkResumed(setOf(OWNER)).toList(emissions) }
        return emissions
    }

    private fun TestScope.collect(flow: Flow<NftItemsState>): List<NftItemsState> {
        val states = mutableListOf<NftItemsState>()
        backgroundScope.launch { flow.toList(states) }
        return states
    }

    private fun wallet(type: BlockchainType = BlockchainType.Ton): Wallet {
        val walletToken = mockk<Token> {
            every { blockchainType } returns type
        }
        val walletAccount = mockk<Account> {
            every { id } returns ACCOUNT_ID
        }
        return mockk {
            every { token } returns walletToken
            every { account } returns walletAccount
        }
    }

    private fun domain(address: String, expiresAt: Long) = NftDomainDraft(address, "$address.ton", expiresAt, false)

    private fun draft(address: String) = NftItemRecordDraft(
        address = address,
        name = null,
        description = null,
        realOwnerAddress = null,
        collectionAddress = null,
        collectionName = null,
        collectionDescription = null,
        trust = NftTrust.Whitelist,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    private fun record(
        address: String,
        ownerAddress: String = ADDRESS,
        blockchainType: BlockchainType = BlockchainType.Ton,
        position: Int = 0,
    ) = NftItemRecord(
        accountId = ACCOUNT_ID,
        blockchainType = blockchainType,
        ownerAddress = ownerAddress,
        address = address,
        position = position,
        name = null,
        description = null,
        realOwnerAddress = null,
        collectionAddress = null,
        collectionName = null,
        collectionDescription = null,
        trust = NftTrust.Whitelist,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    private class CapturingLogWriter : LogWriter() {
        val messages = mutableListOf<String>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            messages += message
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val ADDRESS = "owner-address"
        const val OTHER_ADDRESS = "other-owner-address"
        val KEY = OfflineKey(ACCOUNT_ID, BlockchainType.Ton)
        val OWNER = NftOwner(ACCOUNT_ID, BlockchainType.Ton, ADDRESS)
        val OTHER_OWNER = NftOwner(ACCOUNT_ID, BlockchainType.Ton, OTHER_ADDRESS)
    }
}
