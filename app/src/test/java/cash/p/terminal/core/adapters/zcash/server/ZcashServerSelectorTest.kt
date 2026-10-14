package cash.p.terminal.core.adapters.zcash.server

import android.net.Network
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashPassOutcome
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.zcash.ServerProbe
import cash.p.zcash.Transport
import cash.p.zcash.ZcashException
import io.horizontalsystems.core.BackgroundManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val TIP = 3_000_000
private const val MINUTE_MS = 60_000L
private const val EVALUATION_INTERVAL_MS = 30 * MINUTE_MS
private const val CUSTOM_URL = "https://my.node.io:443"
private const val ONION_HOST = "zcashlightwalletdonionhostforselectortestsabcdefghijklmn"
private const val ONION_URL = "http://$ONION_HOST.onion:80"

private fun probe(
    blockHeight: Int = TIP,
    infoMs: Long = 100,
    rangeMs: Long = 50,
    chainName: String = "main",
    saplingActivationHeight: Int = 419_200,
    branchIdMatches: Boolean = true,
    estimatedHeight: Int = blockHeight,
    rangeBlocksReceived: Int = 10,
) = ServerProbe(
    chainName = chainName,
    saplingActivationHeight = saplingActivationHeight,
    consensusBranchId = "c2d6d0b4",
    blockHeight = blockHeight,
    estimatedHeight = estimatedHeight,
    branchIdMatches = branchIdMatches,
    infoMs = infoMs,
    rangeMs = rangeMs,
    rangeBlocksReceived = rangeBlocksReceived,
)

/** Answers by URL; an unknown URL behaves like an unreachable server. */
private class FakeProber : ZcashServerProber {
    private val answers = mutableMapOf<String, Pair<ServerProbe, ArrayDeque<Long>>>()
    val probed = mutableListOf<Pair<String, Transport>>()

    fun answer(url: String, probe: ServerProbe, infoSamples: List<Long> = emptyList()) {
        answers[url] = probe to ArrayDeque(infoSamples)
    }

    fun unreachable(url: String) {
        answers.remove(url)
    }

    override suspend fun probe(url: String, transport: Transport, rangeBlocks: Int): ServerProbe {
        probed += url to transport
        val (probe, samples) = answers[url] ?: throw ZcashException("connection refused")
        return probe.copy(infoMs = samples.removeFirstOrNull() ?: probe.infoMs)
    }

    override fun streamBreaks(): Long = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashServerSelectorTest {

    private var storedAuto = true
    private var storedAutoUrl: String? = null
    private var storedCustom = emptyList<String>()
    private val storage = mockk<BlockchainSettingsStorage> {
        every { zcashServerAuto() } answers { storedAuto }
        every { zcashAutoServerUrl() } answers { storedAutoUrl }
        every { saveZcashAutoServerUrl(any()) } answers { storedAutoUrl = firstArg() }
        every { zcashServerUrl() } returns null
        every { zcashCustomServers() } answers { storedCustom }
        every { saveZcashCustomServers(any()) } answers { storedCustom = firstArg() }
    }
    private val serverManager = ZcashServerManager(storage)
    private val urls = serverManager.defaultServers.map { it.url }
    private val prober = FakeProber()

    private var tor = false
    private val localStorage = mockk<ILocalStorage> { every { torEnabled } answers { tor } }

    private val connected = MutableStateFlow(true)
    private val networks = MutableStateFlow(setOf(mockk<Network>()))
    private val connectivityManager = mockk<ConnectivityManager> {
        every { isConnected } returns connected
        every { validatedNetworksFlow } returns networks
    }

    private var now = 0L
    private val uptimeProvider = mockk<UptimeProvider> { every { uptime } answers { now } }
    private var foreground = true
    private val backgroundManager = mockk<BackgroundManager> { every { inForeground } answers { foreground } }

    private fun TestScope.selector() = ZcashServerSelector(
        serverManager = serverManager,
        prober = prober,
        connectivityManager = connectivityManager,
        localStorage = localStorage,
        backgroundManager = backgroundManager,
        uptimeProvider = uptimeProvider,
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope),
    ).also { advanceUntilIdle() }

    private fun TestScope.evaluate(selector: ZcashServerSelector) {
        selector.evaluateIfDue()
        advanceUntilIdle()
    }

    private fun TestScope.reevaluate(selector: ZcashServerSelector) {
        now += EVALUATION_INTERVAL_MS
        evaluate(selector)
    }

    private suspend fun TestScope.finish(
        selector: ZcashServerSelector,
        url: String,
        outcome: ZcashPassOutcome,
        breaks: Long = 0,
    ) {
        selector.onPassFinished(url, outcome, breaks)
        advanceUntilIdle()
    }

    private suspend fun TestScope.fail(selector: ZcashServerSelector, url: String) =
        finish(selector, url, ZcashPassOutcome.Failed(IOException("unexpected EOF"), published = true))

    private fun allHealthy(infoMs: Long = 100, rangeMs: Long = 50) =
        urls.forEach { prober.answer(it, probe(infoMs = infoMs, rangeMs = rangeMs)) }

    private fun current() = serverManager.current.url

    private fun ZcashServerSelector.selection() = statusRows()["Server selection"]

    private fun ZcashServerSelector.healthOf(url: String): String {
        val label = serverManager.defaultServers.firstOrNull { it.url == url }?.name ?: url
        return statusRows()["Server health"].orEmpty().lines().first { it.startsWith("$label:") }
    }

    // --- admission ---

    @Test
    fun evaluate_admission_rejectsEachFailingCondition() = runTest {
        storedCustom = listOf(CUSTOM_URL)
        prober.answer(urls[0], probe(chainName = "test"))
        prober.answer(urls[1], probe(saplingActivationHeight = 1))
        prober.answer(urls[2], probe(branchIdMatches = false))
        prober.answer(urls[3], probe(estimatedHeight = TIP + 3))
        prober.answer(urls[4], probe(blockHeight = TIP - 4))
        prober.answer(CUSTOM_URL, probe(infoMs = 500))
        val selector = selector()

        evaluate(selector)

        assertEquals(CUSTOM_URL, current())
        listOf("chain test", "sapling 1", "branch id", "catching up", "lag 4").forEachIndexed { index, reason ->
            assertTrue(selector.healthOf(urls[index]), "rejected: $reason" in selector.healthOf(urls[index]))
        }
        assertFalse("rejected" in selector.healthOf(CUSTOM_URL))
    }

    @Test
    fun statusRows_serverReportsCredentialInChainName_redactsIt() = runTest {
        prober.answer(urls[0], probe(chainName = "https://user:secret@evil.io"))
        val selector = selector()

        evaluate(selector)

        val health = selector.healthOf(urls[0])
        assertTrue(health, "rejected: chain" in health)
        assertFalse(health, "secret" in health)
    }

    @Test
    fun evaluate_oneServerFarAhead_measuresLagAgainstTheMedian() = runTest {
        prober.answer(urls[0], probe(blockHeight = TIP, infoMs = 50))
        prober.answer(urls[1], probe(blockHeight = TIP + 1, infoMs = 100))
        prober.answer(urls[2], probe(blockHeight = TIP + 10, infoMs = 400))
        val selector = selector()

        evaluate(selector)

        assertEquals(urls[0], current())
        assertTrue(selector.healthOf(urls[0]), "lag 1" in selector.healthOf(urls[0]))
    }

    @Test
    fun evaluate_threeLatencySamples_scoresTheirMedian() = runTest {
        prober.answer(urls[0], probe(infoMs = 70))
        prober.answer(urls[1], probe(), infoSamples = listOf(900, 40, 60))
        val selector = selector()

        evaluate(selector)

        assertEquals(urls[1], current())
        assertTrue(selector.healthOf(urls[1]), "info 60ms" in selector.healthOf(urls[1]))
    }

    @Test
    fun evaluate_incompleteBlockRange_rejectsTheServer() = runTest {
        prober.answer(urls[0], probe(infoMs = 10, rangeBlocksReceived = 3))
        prober.answer(urls[1], probe(infoMs = 100))
        val selector = selector()

        evaluate(selector)

        assertEquals(urls[1], current())
        assertTrue(selector.healthOf(urls[0]), "rejected: range 3/10" in selector.healthOf(urls[0]))
    }

    @Test
    fun evaluate_higherIncompatibleServer_doesNotShiftTheReferenceHeight() = runTest {
        storedCustom = listOf(CUSTOM_URL)
        prober.answer(urls[0], probe(blockHeight = TIP))
        prober.answer(CUSTOM_URL, probe(blockHeight = TIP + 1_000_000, chainName = "test"))
        val selector = selector()

        evaluate(selector)

        assertEquals("auto: startup best", selector.selection())
        assertFalse(selector.healthOf(urls[0]), "rejected" in selector.healthOf(urls[0]))
    }

    @Test
    fun evaluate_noneAdmitted_keepsTheCurrentServer() = runTest {
        urls.forEach { prober.answer(it, probe(estimatedHeight = TIP + 5)) }
        val selector = selector()

        evaluate(selector)

        assertNull(storedAutoUrl)
        assertEquals("auto: none admitted", selector.selection())
    }

    // --- hysteresis ---

    @Test
    fun evaluate_bestOnly29PercentFaster_keepsTheCurrentServer() = runTest {
        allHealthy(infoMs = 900, rangeMs = 100)
        val selector = selector()
        evaluate(selector)
        assertEquals(urls[0], current())

        prober.answer(urls[1], probe(infoMs = 610, rangeMs = 100))
        reevaluate(selector)

        assertEquals(urls[0], current())
        assertEquals("auto: current kept (hysteresis)", selector.selection())
    }

    @Test
    fun evaluate_best31PercentFaster_switches() = runTest {
        allHealthy(infoMs = 900, rangeMs = 100)
        val selector = selector()
        evaluate(selector)

        prober.answer(urls[1], probe(infoMs = 590, rangeMs = 100))
        reevaluate(selector)

        assertEquals(urls[1], current())
        assertEquals("auto: faster by 31%", selector.selection())
    }

    @Test
    fun evaluate_breaksInTheLastHour_raiseTheScoreButOlderOnesDoNot() = runTest {
        prober.answer(urls[0], probe(infoMs = 100))
        prober.answer(urls[1], probe(infoMs = 600))
        val selector = selector()
        finish(selector, urls[0], ZcashPassOutcome.Synced, breaks = 1)

        evaluate(selector)
        assertEquals(urls[1], current())

        now += 61 * MINUTE_MS
        evaluate(selector)
        assertEquals(urls[0], current())
    }

    // --- pool ---

    @Test
    fun evaluate_torOff_skipsOnionAndProbesCustomServers() = runTest {
        storedCustom = listOf(ONION_URL, CUSTOM_URL)
        allHealthy()
        val selector = selector()

        evaluate(selector)

        val probedUrls = prober.probed.map { it.first }
        assertTrue(CUSTOM_URL in probedUrls)
        assertFalse(ONION_URL in probedUrls)
    }

    @Test
    fun evaluate_torOn_probesOnionOverTor() = runTest {
        storedCustom = listOf(ONION_URL)
        tor = true
        allHealthy()
        val selector = selector()

        evaluate(selector)

        assertTrue(ONION_URL to Transport.TOR in prober.probed)
        assertTrue(urls[0] to Transport.TOR in prober.probed)
    }

    @Test
    fun evaluate_manualMode_neitherProbesNorSwitches() = runTest {
        storedAuto = false
        allHealthy()
        val selector = selector()

        evaluate(selector)

        assertTrue(prober.probed.isEmpty())
        assertNull(storedAutoUrl)
        assertEquals("manual", selector.selection())
    }

    @Test
    fun evaluate_disconnected_doesNotProbe() = runTest {
        connected.value = false
        allHealthy()
        val selector = selector()

        evaluate(selector)

        assertTrue(prober.probed.isEmpty())
    }

    @Test
    fun statusRows_customOnionServerQuarantined_neverShowsItsRawHost() = runTest {
        storedCustom = listOf(ONION_URL)
        tor = true
        prober.answer(ONION_URL, probe(infoMs = 10))
        prober.answer(urls[0], probe(infoMs = 100))
        val selector = selector()
        evaluate(selector)
        assertEquals(ONION_URL, current())

        fail(selector, ONION_URL)

        assertTrue(selector.selection().orEmpty().startsWith("auto: quarantined "))
        selector.statusRows().values.forEach { assertFalse(it, ONION_HOST in it) }
    }

    // --- triggers ---

    @Test
    fun evaluateIfDue_validatedNetworksChangeWhileConnected_evaluatesAgain() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        val firstRound = prober.probed.size
        evaluate(selector)
        assertEquals(firstRound, prober.probed.size)

        networks.value = setOf(mockk<Network>())
        advanceUntilIdle()
        evaluate(selector)

        assertEquals(2 * firstRound, prober.probed.size)
    }

    @Test
    fun evaluateIfDue_currentCustomServerDeleted_evaluatesAgain() = runTest {
        storedCustom = listOf(CUSTOM_URL)
        allHealthy(infoMs = 500)
        prober.answer(CUSTOM_URL, probe(infoMs = 10))
        val selector = selector()
        evaluate(selector)
        assertEquals(CUSTOM_URL, current())
        prober.probed.clear()

        serverManager.delete(CUSTOM_URL)
        advanceUntilIdle()
        evaluate(selector)

        assertTrue(prober.probed.isNotEmpty())
    }

    @Test
    fun evaluateIfDue_thirtyMinutesInForeground_evaluatesAgain() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        val firstRound = prober.probed.size

        now += EVALUATION_INTERVAL_MS - 1
        evaluate(selector)
        assertEquals(firstRound, prober.probed.size)

        now += 1
        evaluate(selector)
        assertEquals(2 * firstRound, prober.probed.size)
    }

    @Test
    fun evaluateIfDue_thirtyMinutesInBackground_doesNotEvaluate() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        val firstRound = prober.probed.size
        foreground = false

        reevaluate(selector)

        assertEquals(firstRound, prober.probed.size)
    }

    // --- pass feedback ---

    @Test
    fun onPassFinished_repeatedNetworkFailures_quarantineGrowsFrom10To60Minutes() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)

        listOf(10, 20, 40, 60, 60).forEach { minutes ->
            fail(selector, urls[0])
            assertTrue(selector.healthOf(urls[0]), "quarantined ${minutes}m left" in selector.healthOf(urls[0]))
        }
    }

    @Test
    fun onPassFinished_syncedAfterFailures_resetsTheQuarantine() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        fail(selector, urls[0])
        fail(selector, urls[0])

        finish(selector, urls[0], ZcashPassOutcome.Synced)
        assertFalse(selector.healthOf(urls[0]), "quarantined" in selector.healthOf(urls[0]))
        fail(selector, urls[0])

        assertTrue(selector.healthOf(urls[0]), "quarantined 10m left" in selector.healthOf(urls[0]))
    }

    @Test
    fun onPassFinished_cancelled_keepsTheQuarantine() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        fail(selector, urls[0])

        finish(selector, urls[0], ZcashPassOutcome.Cancelled)

        assertTrue(selector.healthOf(urls[0]), "quarantined 10m left" in selector.healthOf(urls[0]))
    }

    @Test
    fun onPassFinished_networkFailureOfTheCurrentServer_switchesToTheBestOther() = runTest {
        allHealthy()
        prober.answer(urls[3], probe(infoMs = 10))
        val selector = selector()
        evaluate(selector)
        prober.answer(urls[3], probe(infoMs = 100))
        prober.answer(urls[2], probe(infoMs = 20))

        fail(selector, urls[3])

        assertEquals(urls[2], current())
        assertEquals("auto: quarantined ${serverManager.defaultServers[3].name}", selector.selection())
    }

    @Test
    fun onPassFinished_everyProbeFails_neitherQuarantinesNorSwitches() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        urls.forEach { prober.unreachable(it) }

        fail(selector, urls[0])

        assertEquals(urls[0], current())
        assertFalse(selector.healthOf(urls[0]), "quarantined" in selector.healthOf(urls[0]))
        assertEquals("auto: no network", selector.selection())
    }

    @Test
    fun onPassFinished_failureWithNoAlternative_keepsTheCurrentServer() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        urls.drop(1).forEach { prober.unreachable(it) }

        fail(selector, urls[0])

        assertEquals(urls[0], current())
        assertEquals("auto: no alternative", selector.selection())
    }

    @Test
    fun onPassFinished_failureOfAAfterAMoveToB_quarantinesAAndDoesNotSwitch() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        prober.answer(urls[1], probe(infoMs = 10))
        reevaluate(selector)
        assertEquals(urls[1], current())

        fail(selector, urls[0])

        assertEquals(urls[1], current())
        assertTrue(selector.healthOf(urls[0]), "quarantined" in selector.healthOf(urls[0]))
        assertFalse(selector.healthOf(urls[1]), "quarantined" in selector.healthOf(urls[1]))
    }

    @Test
    fun evaluate_everyServerQuarantined_keepsTheCurrentWithoutProbing() = runTest {
        allHealthy()
        val selector = selector()
        evaluate(selector)
        urls.forEach { fail(selector, it) }
        val keptUrl = current()
        prober.probed.clear()

        networks.value = setOf(mockk<Network>())
        advanceUntilIdle()
        evaluate(selector)

        assertTrue(prober.probed.isEmpty())
        assertEquals(keptUrl, current())
        assertEquals("auto: all quarantined", selector.selection())
    }
}
