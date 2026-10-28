package cash.p.terminal.shared.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.modules.send.offline.OfflineFileSaveResult
import cash.p.terminal.modules.send.offline.OfflineTransactionFileTransfer
import cash.p.terminal.modules.send.offline.OfflineTransactionFileTransfer.ReadResult
import io.horizontalsystems.core.DefaultDispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class DesktopOfflineTransactionFilesTest {

    private val dispatcherProvider = DefaultDispatcherProvider()
    private val files = DesktopOfflineTransactionFiles(
        OfflineTransactionFileTransfer(dispatcherProvider, OfflineTransactionPayloadEncoder()),
        dispatcherProvider,
    )
    private val file: File = createTempFile("offline-tx", ".txt").toFile()

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun read_rawTransactionFile_returnsItsText() = runTest {
        file.writeText(RAW_CONTENT)

        assertEquals(ReadResult.Success(RAW_CONTENT), files.read(file.toURI().toString()))
    }

    @Test
    fun read_fileOverPayloadLimit_returnsInvalid() = runTest {
        file.writeText("a".repeat(OfflineTransactionPayloadEncoder.MAX_INPUT_CHARACTERS * 4))

        assertEquals(ReadResult.Invalid, files.read(file.toURI().toString()))
    }

    @Test
    fun read_missingFile_returnsUnavailable() = runTest {
        file.delete()

        assertEquals(ReadResult.Unavailable, files.read(file.toURI().toString()))
    }

    @Test
    fun saveText_existingFile_replacesContent() = runTest {
        file.writeText(PREVIOUS_CONTENT)

        assertEquals(OfflineFileSaveResult.Saved, files.saveText(file, RAW_CONTENT))
        assertEquals(RAW_CONTENT, file.readText())
    }

    @Test
    fun saveText_replacementCannotBeStaged_failsAndKeepsPreviousFile() = runTest {
        assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
        file.writeText(PREVIOUS_CONTENT)
        val dir = file.toPath().parent.resolve("locked-${System.nanoTime()}")
        Files.createDirectory(dir)
        val target = Files.move(file.toPath(), dir.resolve(file.name)).toFile()
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"))
        try {
            // Root ignores directory permissions, so the failure cannot be provoked.
            assumeFalse(Files.isWritable(dir))

            assertEquals(OfflineFileSaveResult.Failed, files.saveText(target, RAW_CONTENT))
            assertEquals(PREVIOUS_CONTENT, target.readText())
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"))
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun rememberShareFile_desktop_returnsNoShareLauncher() = runDesktopComposeUiTest {
        var shareFile: (() -> Unit)? = {}
        setContent { shareFile = files.rememberShareFile(RAW_CONTENT) {} }
        waitForIdle()

        assertNull(shareFile)
    }

    private companion object {
        const val RAW_CONTENT = "deadbeefdeadbeefdead"
        const val PREVIOUS_CONTENT = "previous export"
    }
}
