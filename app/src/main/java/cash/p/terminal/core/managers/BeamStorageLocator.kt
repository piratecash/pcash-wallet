package cash.p.terminal.core.managers

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

enum class BeamNetwork(val directoryName: String) {
    Mainnet("mainnet")
}

class BeamStorageLocator(private val context: Context) {
    private val root: File get() = File(context.noBackupFilesDir, "beam")

    fun hasAnyData(): Boolean {
        if (Files.notExists(root.toPath())) return false
        return checkNotNull(root.list()) { "Unable to inspect BEAM storage" }.isNotEmpty()
    }

    fun storagePath(accountId: String, network: BeamNetwork = BeamNetwork.Mainnet): File =
        File(context.noBackupFilesDir, "beam/${storageId(accountId, network)}")

    fun databaseFile(accountId: String, network: BeamNetwork = BeamNetwork.Mainnet): File =
        File(storagePath(accountId, network), "wallet.db")

    internal fun storageIds(): Set<String> {
        validateContained(root)
        if (Files.notExists(root.toPath())) return emptySet()
        return children(root).flatMap { account ->
            validateContained(account)
            check(account.name.matches(Regex("[0-9a-f]{64}")) && account.isDirectory)
            children(account).ifEmpty {
                listOf(File(account, BeamNetwork.Mainnet.directoryName))
            }.map { network ->
                "${account.name}/${network.name}".also { validatedPath(it) }
            }
        }.toSet()
    }

    internal fun validatedPath(id: String): File {
        val networkNames = BeamNetwork.entries.joinToString("|") { it.directoryName }
        require(id.matches(Regex("[0-9a-f]{64}/($networkNames)"))) { "Invalid BEAM storage scope" }
        return File(root, id).also {
            validateContained(root)
            validateContained(checkNotNull(it.parentFile))
            validateContained(it)
        }
    }

    internal fun erase(id: String) {
        val directory = validatedPath(id)
        if (directory.exists()) {
            validateTree(directory)
            check(directory.deleteRecursively() && !directory.exists()) { "BEAM storage removal failed" }
        }
        check(Files.notExists(directory.toPath())) { "Unable to verify BEAM storage removal" }
        val accountDirectory = checkNotNull(directory.parentFile)
        if (accountDirectory.exists() && children(accountDirectory).isEmpty()) {
            check(accountDirectory.delete()) { "BEAM storage directory removal failed" }
        }
    }

    private fun validateTree(file: File) {
        validateContained(file)
        if (file.isDirectory) children(file).forEach(::validateTree)
    }

    private fun validateContained(file: File) {
        val expected = File(context.noBackupFilesDir.canonicalFile, file.relativeTo(context.noBackupFilesDir).path)
        check(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile == expected) {
            "BEAM storage contains a symbolic link"
        }
    }

    private fun children(directory: File): List<File> =
        checkNotNull(directory.listFiles()) { "Unable to inspect BEAM storage" }.toList()

    internal fun storageId(accountId: String, network: BeamNetwork): String {
        val bytes = accountId.toByteArray(Charsets.UTF_8)
        require(accountId.isNotEmpty() && String(bytes, Charsets.UTF_8) == accountId) {
            "BEAM account ID must be nonempty valid Unicode"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val accountDirectory = digest.joinToString("") { "%02x".format(it) }
        return "$accountDirectory/${network.directoryName}"
    }
}
