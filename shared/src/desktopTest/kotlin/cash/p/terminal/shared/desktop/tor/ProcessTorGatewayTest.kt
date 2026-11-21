package cash.p.terminal.shared.desktop.tor

import cash.p.terminal.core.tor.TorGateway
import cash.p.terminal.shared.desktop.qrscanner.DesktopOs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessTorGatewayTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var gateway: ProcessTorGateway? = null

    @AfterTest
    fun tearDown() {
        gateway?.unbind()
    }

    @Test
    fun torrc_dataDirectoryWithSpaces_quotesPathsAndKeepsBaseOptions() {
        val text = torrc(File("/data/Application Support/tor"), ownerPid = 42)

        TorGateway.BASE_TORRC_OPTIONS.forEach { assertTrue(it in text, it) }
        assertTrue("DataDirectory \"/data/Application Support/tor\"" in text)
        assertTrue("CookieAuthFile \"/data/Application Support/tor/control_auth_cookie\"" in text)
        assertTrue("ControlPort auto" in text)
        assertTrue("__OwningControllerProcess 42" in text)
    }

    @Test
    fun torrc_pathWithQuoteAndBackslash_escapesThem() {
        val text = torrc(File("/data/a\"b\\c"), ownerPid = 1)

        assertTrue("DataDirectory \"/data/a\\\"b\\\\c\"" in text)
    }

    @Test
    fun parseControlPort_fileWrittenByTor_returnsPort() {
        assertEquals(9151, parseControlPort("PORT=127.0.0.1:9151\n"))
        assertNull(parseControlPort(""))
    }

    @Test
    fun bind_missingExecutable_returnsFalse() {
        assertFalse(createGateway(File(folder.root, "absent")).bind())
        assertFalse(createGateway(null).bind())
    }

    @Test
    fun bind_torExitsOnItsOwn_emitsTorExited() = runBlocking {
        val gateway = createGateway(script("exit 0"))
        val event = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { gateway.events.first() }

        gateway.bind()

        assertEquals(TorGateway.Event.TorExited, withTimeout(EVENT_WAIT_MILLIS) { event.await() })
    }

    @Test
    fun unbind_runningTor_stopsItWithoutEvent() = runBlocking {
        val gateway = createGateway(script("exec sleep 30"))
        val event = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(EVENT_WAIT_MILLIS) { gateway.events.first() }
        }
        assertTrue(gateway.bind())

        gateway.unbind()

        assertFalse(gateway.isTorRunning)
        assertNull(event.await())
    }

    @Test
    fun deleteStaleFiles_torDisabled_deletesDataDirectory() {
        val data = dataDirectory()

        createGateway(null, data).deleteStaleFiles(torEnabled = false)

        assertFalse(data.exists())
    }

    @Test
    fun deleteStaleFiles_torEnabled_keepsDataDirectory() {
        val data = dataDirectory()

        createGateway(null, data).deleteStaleFiles(torEnabled = true)

        assertTrue(File(data, "cached-certs").isFile)
    }

    private fun createGateway(executable: File?, data: File = File(folder.root, "tor")) =
        ProcessTorGateway(executable, data, DesktopOs.current).also { gateway = it }

    private fun dataDirectory() = File(folder.root, "tor").apply {
        mkdirs()
        File(this, "cached-certs").writeText("certs")
    }

    private fun script(body: String): File {
        assumeFalse(DesktopOs.current == DesktopOs.Windows)
        return File(folder.root, "fake-tor").apply {
            writeText("#!/bin/sh\n$body\n")
            setExecutable(true)
        }
    }

    private companion object {
        const val EVENT_WAIT_MILLIS = 5_000L
    }
}
