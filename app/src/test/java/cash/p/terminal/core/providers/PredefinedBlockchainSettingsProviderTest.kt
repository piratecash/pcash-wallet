package cash.p.terminal.core.providers

import android.content.Context
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

private const val ACCOUNT_ID = "account"

class PredefinedBlockchainSettingsProviderTest {

    private lateinit var noBackupDir: File
    private lateinit var databaseFiles: ZcashDatabaseFiles

    private val manager = mockk<RestoreSettingsManager>(relaxed = true)
    private val zcashBirthdayProvider = mockk<ZcashBirthdayProvider> {
        every { getLatestCheckpointBlockHeight() } returns 2_500_000L
    }
    private val validateMoneroHeightUseCase = mockk<ValidateMoneroHeightUseCase> {
        every { getTodayHeight() } returns 3_000_000L
    }
    private val account = mockk<Account> { every { id } returns ACCOUNT_ID }

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("zcash-settings").toFile()
        databaseFiles = ZcashDatabaseFiles(mockk<Context> { every { noBackupFilesDir } returns noBackupDir })
    }

    @After
    fun tearDown() {
        noBackupDir.deleteRecursively()
    }

    @Test
    fun prepareNew_zcash_marksTheAccountPristine() {
        provider().prepareNew(account, BlockchainType.Zcash)

        assertTrue(databaseFiles.isPristine(ACCOUNT_ID))
    }

    @Test
    fun prepareNew_monero_leavesThePristineMarkAlone() {
        provider().prepareNew(account, BlockchainType.Monero)

        assertFalse(databaseFiles.isPristine(ACCOUNT_ID))
    }

    private fun provider() = PredefinedBlockchainSettingsProvider(
        manager = manager,
        zcashBirthdayProvider = zcashBirthdayProvider,
        validateMoneroHeightUseCase = validateMoneroHeightUseCase,
        databaseFiles = databaseFiles,
    )
}
