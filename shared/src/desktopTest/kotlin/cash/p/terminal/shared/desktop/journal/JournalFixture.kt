package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecord
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.OpenSafe
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture
import cash.p.terminal.shared.desktop.vault.VaultLoginEvents
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.ICurrentDateProvider
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Date
import kotlin.random.Random

internal val PHOTO = byteArrayOf(1, 2, 3, 4)

internal class FakeSelfieCamera : SelfieCamera {
    var photo: ByteArray? = PHOTO
    var captures = 0

    /** Holds every capture until completed, as a slow webcam would. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun capture(): ByteArray? {
        captures++
        gate?.await()
        return photo
    }
}

internal class SteppingDateProvider : ICurrentDateProvider {
    var now = START
    override val currentDate: Date get() = Date(now++)

    companion object {
        const val START = 1_800_000_000_000L
    }
}

/** The real journal on a real vault; only the camera, Premium and the contacts are fakes. */
internal class JournalFixture(val levels: VaultLevelsFixture) {
    val camera = FakeSelfieCamera()
    /** Premium belongs to a safe, whichever level it has in the session. */
    val nonPremiumSlots = mutableSetOf<Int>()
    var premiumCheckFails = false
    val contacts = mockk<ContactsRepository>(relaxed = true)
    val dateProvider = SteppingDateProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatcherProvider = TestDispatcherProvider(Dispatchers.IO, scope)
    val settingsStore = DesktopSettingsStore(File(levels.dataDirectory, "journal-settings.preferences_pb"), scope)
    private val premium = mockk<CheckPremiumUseCase> {
        coEvery { isPremiumWithParentInCache(any()) } answers {
            check(!premiumCheckFails) { "test" }
            val slot = vault.openSession()?.chain?.safeOrNull(firstArg())?.link?.slot
            slot != null && slot !in nonPremiumSlots
        }
    }
    private val built = mutableMapOf<DesktopVault, Parts>()

    val vault get() = levels.vault
    val session get() = levels.session
    val parts get() = built.getOrPut(vault) { Parts(vault) }

    init {
        levels.loginEvents = object : VaultLoginEvents {
            override fun onEntered(session: DesktopStorageSession) = parts.journal.onEntered(session)
            override fun onFailedUnlock() = parts.journal.onFailedUnlock()
            override fun onEraseContacts() = parts.journal.onEraseContacts()
        }
    }

    inner class Parts(vault: DesktopVault) {
        private val queue = JournalQueue(dispatcherProvider)
        val records = DesktopLoginRecordRepository(vault, dateProvider, dispatcherProvider)
        val settings = DesktopLoggingSettings(vault, settingsStore)
        val secrets = DesktopLoginJournalSecrets(vault, levels.passwordVault, levels.lockoutManager, dispatcherProvider)
        val journal = DesktopLoginJournal(
            vault, camera, lazyOf(premium), lazyOf(contacts),
            FailedAttemptRecorder(vault, camera, settingsStore, dateProvider, queue),
            records, dateProvider, queue,
        )
    }

    /** Journal work runs after the unlock returns. */
    suspend fun settle() {
        while (true) {
            val running = checkNotNull(scope.coroutineContext[Job]).children.toList()
            if (running.isEmpty()) return
            running.joinAll()
        }
    }

    suspend fun relogin(password: String) = levels.relogin(password).also { settle() }

    suspend fun reloginOk(password: String) = levels.reloginOk(password).also { settle() }

    fun safe(level: Int): OpenSafe = checkNotNull(session.chain.safeOrNull(level)) { "Level $level is not open" }

    suspend fun records(level: Int): List<JournalRecord> = checkNotNull(safe(level).journal).all()

    /** Read straight from the file with the vault closed: what someone holding only this safe's key would see. */
    fun safeSettings(slot: Int, key: ByteArray): Map<String, String> =
        SqlCipherDriver(key).use { driver ->
            driver.open(levels.files.safeFile(slot).absolutePath).use { connection ->
                connection.prepare("SELECT `key`, value FROM LevelSetting").use { statement ->
                    buildMap { while (statement.step()) put(statement.getText(0), statement.getText(1)) }
                }
            }
        }

    /** A database that will not open: the file is set aside and garbage put in its place until [restoreFile]. */
    suspend fun breakFile(file: File) {
        vault.close()
        check(file.renameTo(File(file.path + HIDDEN)))
        file.writeBytes(Random.nextBytes(BROKEN_FILE_SIZE))
    }

    fun restoreFile(file: File) {
        check(file.delete() && File(file.path + HIDDEN).renameTo(file))
    }

    suspend fun hideSafe(slot: Int) = breakFile(levels.files.safeFile(slot))

    fun restoreSafe(slot: Int) = restoreFile(levels.files.safeFile(slot))

    /** For a failure the code must survive: run with the vault closed, on the file itself. */
    fun execute(file: File, key: ByteArray, sql: String) = SqlCipherDriver(key).use { driver ->
        driver.open(file.absolutePath).use { connection -> connection.prepare(sql).use { it.step() } }
    }

    /** No new file can be made in the vault directory, so an atomic replace fails; open databases stay writable. */
    suspend fun <T> withVaultDirectoryReadOnly(block: suspend () -> T): T {
        val directory = levels.files.vaultDirectory
        check(directory.setWritable(false))
        return try {
            block().also { settle() }
        } finally {
            directory.setWritable(true)
        }
    }

    fun tearDown() = scope.cancel()

    private companion object {
        const val HIDDEN = ".hidden"
        const val BROKEN_FILE_SIZE = 20480
    }
}

internal fun journalScenario(block: suspend (JournalFixture) -> Unit) = runBlocking {
    val levels = VaultLevelsFixture()
    val fixture = JournalFixture(levels)
    try {
        block(fixture)
    } finally {
        fixture.settle()
        fixture.tearDown()
        levels.tearDown()
    }
}

internal fun password(value: String) = value.toCharArray()
