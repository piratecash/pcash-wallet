package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.zcash.AccountInfo
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
    private var discoveredAccountIds = emptySet<String>()

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
        discoveredAccountIds = setOf(ACCOUNT_ID)
        every { localStorage.zcashDiscoveredAccountIds } answers { discoveredAccountIds }
        every { localStorage.zcashDiscoveredAccountIds = any() } answers {
            discoveredAccountIds = firstArg()
        }
        every { localStorage.invalidateZcashAddressDiscovery(any()) } answers { callOriginal() }
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
    fun open_lostDbKey_dropsDatabaseAndRediscoversTransparentAddresses() = runTest {
        val leftover = databaseFiles.databaseFile(ACCOUNT_ID)
            .apply { parentFile?.mkdirs() }
            .apply { writeText("encrypted with a key that is gone") }
        every { dbKeyProvider.keyFor(ACCOUNT_ID) } returns ZcashDbKey(ByteArray(32), newlyGenerated = true)

        assertEquals(DB_ACCOUNT_ID, opener().open(wallet()).dbAccountId)

        assertFalse(leftover.exists())
        coVerify(exactly = 1) { zcashWallet.restoreAccount(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { zcashWallet.discoverTransparentAddresses(DB_ACCOUNT_ID) }
    }

    private fun opener() = ZcashWalletOpenerImpl(
        databaseFiles = databaseFiles,
        localStorage = localStorage,
        restoreSettingsManager = restoreSettingsManager,
        birthdayProvider = birthdayProvider,
        dbKeyProvider = dbKeyProvider,
    )

    private fun wallet(passphrase: String = "") = mockk<Wallet>(relaxed = true) {
        every { account } returns Account(
            id = ACCOUNT_ID,
            name = "Zcash",
            type = AccountType.Mnemonic(listOf("one", "two", "three"), passphrase),
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
