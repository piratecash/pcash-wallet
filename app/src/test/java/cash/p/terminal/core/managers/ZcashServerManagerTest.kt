package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.zcash.Transport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashServerManagerTest {

    private var storedUrl: String? = null
    private var storedCustom: List<String> = emptyList()
    private val storage = mockk<BlockchainSettingsStorage> {
        every { zcashServerUrl() } answers { storedUrl }
        every { saveZcashServerUrl(any()) } answers { storedUrl = firstArg() }
        every { zcashCustomServers() } answers { storedCustom }
        every { saveZcashCustomServers(any()) } answers { storedCustom = firstArg() }
    }
    private val manager = ZcashServerManager(storage)
    private val defaultUrl = manager.defaultServers.first().url

    private class Counter {
        var value = 0
    }

    private fun TestScope.collectCount(flow: SharedFlow<Unit>) = Counter().also { counter ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { counter.value++ } }
    }

    @Test
    fun select_slowCollectorHoldsTheBuffer_fastCollectorStillGetsEveryLaterNotification() =
        runTest(UnconfinedTestDispatcher()) {
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                manager.serverSelectedFlow.collect { release.await() }
            }
            val fast = collectCount(manager.serverSelectedFlow)

            manager.defaultServers.drop(1).take(3).forEach { manager.select(it.url) }

            assertEquals(3, fast.value)
        }

    @Test
    fun addCustom_slowCollectorHoldsTheBuffer_fastCollectorStillGetsEveryLaterUpdate() =
        runTest(UnconfinedTestDispatcher()) {
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                manager.serversUpdatedFlow.collect { release.await() }
            }
            val fast = collectCount(manager.serversUpdatedFlow)

            manager.addCustom("https://a.example")
            manager.addCustom("https://b.example")
            manager.addCustom("https://c.example")

            assertEquals(3, fast.value)
        }

    @Test
    fun defaultServers_matchPlannedZecRocksEndpoints_inOrder() {
        assertEquals(
            listOf(
                "https://zec.rocks:443",
                "https://na.zec.rocks:443",
                "https://sa.zec.rocks:443",
                "https://eu.zec.rocks:443",
                "https://ap.zec.rocks:443",
            ),
            manager.defaultServers.map { it.url },
        )
        assertTrue(manager.defaultServers.none { it.isCustom })
    }

    @Test
    fun current_nothingStored_isFirstDefault() {
        assertEquals(defaultUrl, manager.current.url)
        assertEquals("https://zec.rocks:443", defaultUrl)
    }

    @Test
    fun current_unknownStoredUrl_fallsBackToFirstDefault() {
        storedUrl = "https://gone.example:443"

        assertEquals(defaultUrl, manager.current.url)
    }

    @Test
    fun select_newUrl_persistsAndEmitsOnce() = runTest(UnconfinedTestDispatcher()) {
        val target = manager.defaultServers[2].url
        val emissions = collectCount(manager.serverSelectedFlow)

        manager.select(target)
        manager.select(target)

        assertEquals(target, storedUrl)
        assertEquals(1, emissions.value)
    }

    @Test
    fun select_firstExplicitChoiceOfTheDefault_persistsAndEmits() = runTest(UnconfinedTestDispatcher()) {
        val emissions = collectCount(manager.serverSelectedFlow)

        manager.select(defaultUrl)

        assertEquals(defaultUrl, storedUrl)
        assertEquals(1, emissions.value)
    }

    @Test
    fun addCustom_validUrl_isSavedSelectedAndEmitsBothFlows() = runTest(UnconfinedTestDispatcher()) {
        val selected = collectCount(manager.serverSelectedFlow)
        val updated = collectCount(manager.serversUpdatedFlow)

        val result = manager.addCustom("https://My.Node.io/")

        val url = "https://my.node.io:443"
        assertEquals(AddResult.Added(ZcashServer("my.node.io:443", url, isCustom = true)), result)
        assertEquals(listOf(url), storedCustom)
        assertEquals(url, manager.current.url)
        assertEquals(1, selected.value)
        assertEquals(1, updated.value)
    }

    @Test
    fun addCustom_defaultInOtherSpelling_isDuplicate() {
        assertEquals(AddResult.Duplicate, manager.addCustom("https://zec.rocks/"))
        assertTrue(storedCustom.isEmpty())
    }

    @Test
    fun addCustom_existingCustomInOtherSpelling_isDuplicate() {
        manager.addCustom("https://my.node.io")

        assertEquals(AddResult.Duplicate, manager.addCustom("HTTPS://MY.NODE.IO:443/"))
        assertEquals(1, storedCustom.size)
    }

    @Test
    fun addCustom_invalidUrl_isInvalidAndSavesNothing() {
        assertEquals(AddResult.Invalid, manager.addCustom("http://insecure.io"))

        assertTrue(storedCustom.isEmpty())
        verify(exactly = 0) { storage.saveZcashCustomServers(any()) }
    }

    @Test
    fun delete_unselectedCustom_emitsOnlyServersUpdated() = runTest(UnconfinedTestDispatcher()) {
        manager.addCustom("https://my.node.io")
        manager.select(defaultUrl)
        val selected = collectCount(manager.serverSelectedFlow)
        val updated = collectCount(manager.serversUpdatedFlow)

        manager.delete("https://my.node.io:443")

        assertTrue(storedCustom.isEmpty())
        assertEquals(defaultUrl, storedUrl)
        assertEquals(0, selected.value)
        assertEquals(1, updated.value)
    }

    @Test
    fun delete_currentCustom_reselectsDefaultAndEmitsBothFlows() = runTest(UnconfinedTestDispatcher()) {
        manager.addCustom("https://my.node.io")
        val selected = collectCount(manager.serverSelectedFlow)
        val updated = collectCount(manager.serversUpdatedFlow)

        manager.delete("https://my.node.io:443")

        assertEquals(defaultUrl, storedUrl)
        assertEquals(defaultUrl, manager.current.url)
        assertEquals(1, selected.value)
        assertEquals(1, updated.value)
    }

    @Test
    fun transportFor_onionWithoutTorToggle_isTor() {
        assertEquals(Transport.TOR, manager.transportFor("http://abc.onion:80", torEnabled = false))
    }

    @Test
    fun transportFor_httpsWithTorToggle_isTor() {
        assertEquals(Transport.TOR, manager.transportFor("https://host.io:443", torEnabled = true))
    }

    @Test
    fun transportFor_httpsWithoutTorToggle_isDirect() {
        assertEquals(Transport.DIRECT, manager.transportFor("https://host.io:443", torEnabled = false))
    }
}
