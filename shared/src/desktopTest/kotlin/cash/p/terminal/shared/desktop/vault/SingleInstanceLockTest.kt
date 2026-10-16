package cash.p.terminal.shared.desktop.vault

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SingleInstanceLockTest {

    private val directory = createTempDirectory("desktop-instance").toFile()
    private val file = File(directory, ".instance.lock")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun acquire_lockAlreadyHeld_returnsNullAndFreesItOnClose() {
        val first = assertNotNull(SingleInstanceLock.acquire(file))

        assertNull(SingleInstanceLock.acquire(file))

        first.close()
        assertNotNull(SingleInstanceLock.acquire(file)).close()
    }
}
