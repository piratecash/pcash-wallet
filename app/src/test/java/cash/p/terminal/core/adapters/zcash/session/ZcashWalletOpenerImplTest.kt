package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.adapters.zcash.CapturedAppLog
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.managers.ZcashServer
import cash.p.terminal.core.managers.ZcashServerManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.zcash.AccountInfo
import cash.p.zcash.ServerConfig
import cash.p.zcash.Transport
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

private const val ACCOUNT_ID = "account"
private const val BIRTHDAY = 2_500_000
private const val DB_ACCOUNT_ID = 7

class ZcashWalletOpenerImplTest {

    private lateinit var noBackupDir: File
    private lateinit var databaseFiles: ZcashDatabaseFiles

    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val restoreSettingsManager = mockk<RestoreSettingsManager>()
    private val birthdayProvider = mockk<ZcashBirthdayProvider>(relaxed = true)
    private val dbKeyProvider = mockk<ZcashDbKeyProvider>(relaxed = true)
    private val zcashWallet = mockk<ZcashWallet>(relaxed = true)
    private val serverManager = mockk<ZcashServerManager> {
        every { current } returns ZcashServer("zec.rocks", "https://zec.rocks:443", isCustom = false)
        every { transportFor(any(), any()) } returns Transport.DIRECT
    }

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("zcash-open").toFile()
        databaseFiles = ZcashDatabaseFiles(mockk<Context> { every { noBackupFilesDir } returns noBackupDir })

        mockkObject(ZcashSdk)
        mockkObject(ZcashWallet.Companion)
        coEvery { ZcashSdk.initialize(any(), any()) } returns Unit
        coEvery { ZcashWallet.open(any(), any(), any(), any()) } returns zcashWallet
        coEvery { zcashWallet.accounts() } returns emptyList()
        coEvery { zcashWallet.restoreAccount(any(), any(), any(), any(), any(), any()) } returns DB_ACCOUNT_ID

        every { dbKeyProvider.keyFor(ACCOUNT_ID) } returns ZcashDbKey(ByteArray(32), newlyGenerated = false)
        every { restoreSettingsManager.settings(any(), any()) } returns
            RestoreSettings().apply { birthdayHeight = BIRTHDAY.toLong() }
    }

    @After
    fun tearDown() {
        unmockkAll()
        noBackupDir.deleteRecursively()
    }

    @Test
    fun open_existingDatabase_reusesItsAccount() = runTest {
        coEvery { zcashWallet.accounts() } returns listOf(accountInfo())

        assertEquals(DB_ACCOUNT_ID, opener().open(wallet()).dbAccountId)
        coVerify(exactly = 0) { zcashWallet.restoreAccount(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun open_afterErase_restoresFromTheStoredBirthday() = runTest {
        assertEquals(DB_ACCOUNT_ID, opener().open(wallet()).dbAccountId)

        coVerify(exactly = 1) {
            zcashWallet.restoreAccount(
                name = any(),
                key = "one two three",
                birthHeight = BIRTHDAY,
                passphrase = "",
            )
        }
    }

    @Test
    fun open_blankPassphrase_restoresWithItVerbatim() = runTest {
        opener().open(wallet(passphrase = "  "))

        coVerify(exactly = 1) {
            zcashWallet.restoreAccount(
                name = any(),
                key = "one two three",
                birthHeight = BIRTHDAY,
                passphrase = "  ",
            )
        }
    }

    @Test
    fun open_lostDbKey_dropsTheDatabaseAndRestoresFresh() = runTest {
        val leftover = databaseFiles.databaseFile(ACCOUNT_ID)
            .apply { parentFile?.mkdirs() }
            .apply { writeText("encrypted with a key that is gone") }
        every { dbKeyProvider.keyFor(ACCOUNT_ID) } returns ZcashDbKey(ByteArray(32), newlyGenerated = true)

        assertEquals(DB_ACCOUNT_ID, opener().open(wallet()).dbAccountId)

        assertFalse(leftover.exists())
        coVerify(exactly = 1) { zcashWallet.restoreAccount(any(), any(), any(), any(), any(), any()) }
        // Transparent-address discovery is periodic now (ZcashSyncScheduler), not part of opening
        // the wallet: the coverage record died with the deleted database, so the session's own
        // first walk will be deep.
        coVerify(exactly = 0) { zcashWallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun open_pristineAccount_reportsNoDeepSweepRequired() = runTest {
        databaseFiles.markPristine(ACCOUNT_ID)

        assertFalse(opener().open(wallet()).deepSweepRequired)
    }

    @Test
    fun open_accountNotPristine_reportsDeepSweepRequired() = runTest {
        assertTrue(opener().open(wallet()).deepSweepRequired)
    }

    @Test
    fun open_lostDbKeyWithADatabase_dropsTheMarkWithTheDatabase() = runTest {
        val leftover = databaseFiles.databaseFile(ACCOUNT_ID)
            .apply { parentFile?.mkdirs() }
            .apply { writeText("encrypted with a key that is gone") }
        databaseFiles.markPristine(ACCOUNT_ID)
        every { dbKeyProvider.keyFor(ACCOUNT_ID) } returns ZcashDbKey(ByteArray(32), newlyGenerated = true)

        val opened = opener().open(wallet())

        assertFalse(leftover.exists())
        assertFalse(databaseFiles.isPristine(ACCOUNT_ID))
        assertTrue(opened.deepSweepRequired)
    }

    @Test
    fun open_firstOpenOfAPristineAccount_keepsTheMark() = runTest {
        databaseFiles.markPristine(ACCOUNT_ID)
        every { dbKeyProvider.keyFor(ACCOUNT_ID) } returns ZcashDbKey(ByteArray(32), newlyGenerated = true)

        val opened = opener().open(wallet())

        assertTrue(databaseFiles.isPristine(ACCOUNT_ID))
        assertFalse(opened.deepSweepRequired)
    }

    @Test
    fun open_usesTheSelectedServer() = runTest {
        every { serverManager.current } returns ZcashServer("mine", "https://my.node.io:443", isCustom = true)
        every { serverManager.transportFor("https://my.node.io:443", true) } returns Transport.TOR
        every { localStorage.torEnabled } returns true

        opener().open(wallet())

        coVerify(exactly = 1) {
            ZcashWallet.open(
                any(), any(), ServerConfig(url = "https://my.node.io:443", transport = Transport.TOR), any(),
            )
        }
    }

    @Test
    fun open_writesOneLinePerStep() = runTest {
        val appLog = CapturedAppLog()
        every { dbKeyProvider.keyFor(ACCOUNT_ID) } returns
            ZcashDbKey(ByteArray(32) { 0xAB.toByte() }, newlyGenerated = false)

        opener().open(wallet(words = listOf("zebra", "quartz", "nimbus")))
        appLog.awaitEntry { it.message.startsWith("open: done") }

        val lines = appLog.messages
        val opening = "open: db=absent key=stored deepSweep=true legacyDir=absent " +
            "server=https://zec.rocks:443 transport=DIRECT"
        assertTrue(lines.any { it.startsWith(opening) })
        assertTrue(lines.contains("restore: birthSource=setting birth=$BIRTHDAY"))
        assertTrue(lines.any { it.startsWith("open: done dbAccountId=$DB_ACCOUNT_ID accounts=0 elapsed=") })
        val forbidden = listOf("zebra", "quartz", "nimbus", "abababab")
        assertTrue(lines.none { line -> forbidden.any { it in line } })
    }

    @Test
    fun open_logsServerWithoutUserInfo() = runTest {
        val appLog = CapturedAppLog()
        every { serverManager.current } returns ZcashServer("mine", "https://user:pw@host.io:443", isCustom = true)

        opener().open(wallet())
        val line = appLog.awaitEntry { it.message.startsWith("open: db=") }.message

        assertTrue(line.contains("server=https://host.io:443"))
        assertFalse(line.contains("user"))
        assertFalse(line.contains("pw"))
    }

    @Test
    fun open_selectedServer_reportsItsUrl() = runTest {
        assertEquals("https://zec.rocks:443", opener().open(wallet()).serverUrl)
    }

    private fun opener() = ZcashWalletOpenerImpl(
        databaseFiles = databaseFiles,
        localStorage = localStorage,
        restoreSettingsManager = restoreSettingsManager,
        birthdayProvider = birthdayProvider,
        dbKeyProvider = dbKeyProvider,
        serverManager = serverManager,
    )

    private fun wallet(
        passphrase: String = "",
        words: List<String> = listOf("one", "two", "three"),
    ) = mockk<Wallet>(relaxed = true) {
        every { account } returns Account(
            id = ACCOUNT_ID,
            name = "Zcash",
            type = AccountType.Mnemonic(words, passphrase),
            origin = AccountOrigin.Restored,
            level = 0,
        )
    }

    private fun accountInfo() = AccountInfo(
        id = DB_ACCOUNT_ID,
        name = "Zcash",
        birthHeight = BIRTHDAY,
        accountIndex = 0,
        diversifierIndex = 0,
        position = 0,
        height = BIRTHDAY,
        time = 0,
        balance = 0,
        hidden = false,
        enabled = true,
        internal = false,
        hardwareWallet = false,
    )
}
