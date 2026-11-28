package cash.p.terminal.modules.backuplocal

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.RestoreSettingType
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupItem
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import com.google.gson.Gson
import com.google.gson.JsonNull
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ZcashIssueIndexBackupDesktopTest {

    private val walletStorage = mockk<IEnabledWalletStorage>()
    private val settingsManager = mockk<RestoreSettingsManager>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val accountFactory = mockk<IAccountFactory>()
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper> { every { tokens(any<List<TokenQuery>>()) } returns emptyList() }

    private val provider = WalletBackupProvider(
        walletStorage = walletStorage,
        settingsManager = settingsManager,
        accountManager = accountManager,
        accountFactory = accountFactory,
        walletManager = mockk(relaxed = true),
        restoreSettingsManager = restoreSettingsManager,
        marketFavoritesManager = mockk(relaxed = true),
        appSettingsBackup = mockk(relaxed = true),
    )

    private val account = Account(ACCOUNT_ID, NAME, MNEMONIC, AccountOrigin.Restored, level = 0, isBackedUp = true)

    @Before
    fun setUp() {
        // App startup does this too: the backup file's EVM signer needs the kit's Keccak provider.
        EthereumKit.init()
        every { accountFactory.account(any(), any(), any(), any(), any()) } returns account
        every { walletStorage.enabledWallets(ACCOUNT_ID) } returns listOf(
            EnabledWallet(tokenQueryId = ZCASH_QUERY_ID, accountId = ACCOUNT_ID, coinImage = null)
        )
        every { settingsManager.settings(account, BlockchainType.Zcash) } returns zcashSettings(issueIndex = null)
        startKoin { modules(module { single { marketKit } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun restoreSingleWalletBackup_backupCarriesTheIssueIndex_restoresItBesideTheBirthday() = runTest {
        every { settingsManager.settings(account, BlockchainType.Zcash) } returns zcashSettings(issueIndex = "41")

        restore(backupOfAccount())

        verify {
            restoreSettingsManager.save(
                match<RestoreSettings> {
                    it.birthdayHeight == BIRTHDAY && it[RestoreSettingType.ZcashTransparentIssueIndex] == "41"
                },
                account,
                BlockchainType.Zcash,
            )
        }
    }

    @Test
    fun restoreSingleWalletBackup_oldBackupWithoutIssueIndex_restoresTheBirthdayOnly() = runTest {
        restore(backupOfAccount())

        verify {
            restoreSettingsManager.save(
                match<RestoreSettings> {
                    it.birthdayHeight == BIRTHDAY && it[RestoreSettingType.ZcashTransparentIssueIndex] == null
                },
                account,
                BlockchainType.Zcash,
            )
        }
    }

    @Test
    fun restoreSingleWalletBackup_issueIndexOutOfRange_failsBeforeWritingAnything() = runTest {
        listOf("100001", "-1", "not-a-number").forEach { value ->
            val backup = backupOfAccount().withIssueIndex(value)

            assertFailsWith<IllegalArgumentException> {
                provider.restoreSingleWalletBackup(MNEMONIC, NAME, backup, BackupSource.Authenticated)
            }
        }

        verify(exactly = 0) { accountManager.save(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun restoreSingleWalletBackup_issueIndexExplicitNull_failsBeforeWritingAnything() = runTest {
        every { settingsManager.settings(account, BlockchainType.Zcash) } returns zcashSettings(issueIndex = "41")
        val tree = Gson().toJsonTree(backupOfAccount())
        val settings = tree.asJsonObject["enabled_wallets"].asJsonArray.single().asJsonObject["settings"].asJsonObject
        val issueKey = settings.keySet().single { settings[it].asString == "41" }
        settings.add(issueKey, JsonNull.INSTANCE)
        val backup = Gson().fromJson(tree, BackupLocalModule.WalletBackup::class.java)

        assertFailsWith<IllegalArgumentException> {
            provider.restoreSingleWalletBackup(MNEMONIC, NAME, backup, BackupSource.Authenticated)
        }

        verify(exactly = 0) { accountManager.save(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun restoreSingleWalletBackup_backupCarriesTheIssueIndex_savesItBeforeTheAccount() = runTest {
        every { settingsManager.settings(account, BlockchainType.Zcash) } returns zcashSettings(issueIndex = "41")

        restore(backupOfAccount())

        verifyOrder {
            restoreSettingsManager.prepareForNewAccount(account)
            restoreSettingsManager.save(any(), account, BlockchainType.Zcash)
            accountManager.save(account)
        }
    }

    @Test
    fun restoreFullBackup_issueIndexAboveTheCeiling_failsBeforeWritingAnything() = runTest {
        val wallets = backupOfAccount().withIssueIndex("100001").enabledWallets.orEmpty()
        val decrypted = DecryptedFullBackup(
            wallets = listOf(WalletBackupItem(account, wallets, BackupSource.Authenticated)),
            watchlist = emptyList(),
            settings = null,
            contacts = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> { provider.restoreFullBackup(decrypted, PASSWORD) }

        verify(exactly = 0) { accountManager.import(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun enabledWalletBackups_issueIndexStoredForANonZcashToken_leavesItOut() {
        every { walletStorage.enabledWallets(ACCOUNT_ID) } returns listOf(
            EnabledWallet(tokenQueryId = MONERO_QUERY_ID, accountId = ACCOUNT_ID, coinImage = null)
        )
        every { settingsManager.settings(account, BlockchainType.Monero) } returns zcashSettings(issueIndex = "41")

        val settings = provider.enabledWalletBackups(account).single().settings

        assertNull(settings?.get(RestoreSettingType.ZcashTransparentIssueIndex))
    }

    /** Through the real file format, so the setting's serialized name is what round-trips. */
    private fun backupOfAccount(): BackupLocalModule.WalletBackup {
        val backupV3 = assertNotNull(provider.parseV3Backup(provider.createWalletBackup(account, PASSWORD)))
        val (innerJson, _, _) = provider.unwrapV3FormatWithKey(backupV3, PASSWORD)
        return Gson().fromJson(innerJson, BackupLocalModule.WalletBackup::class.java)
    }

    private suspend fun restore(backup: BackupLocalModule.WalletBackup) {
        assertEquals(
            RestoreOutcome.Restored,
            provider.restoreSingleWalletBackup(MNEMONIC, NAME, backup, BackupSource.Authenticated),
        )
    }

    private fun BackupLocalModule.WalletBackup.withIssueIndex(value: String) = copy(
        enabledWallets = enabledWallets?.map { wallet ->
            wallet.copy(settings = wallet.settings.orEmpty() + (RestoreSettingType.ZcashTransparentIssueIndex to value))
        }
    )

    private fun zcashSettings(issueIndex: String?) = RestoreSettings().apply {
        birthdayHeight = BIRTHDAY
        issueIndex?.let { this[RestoreSettingType.ZcashTransparentIssueIndex] = it }
    }

    private companion object {
        const val ACCOUNT_ID = "zcash-account"
        const val NAME = "Zcash"
        const val PASSWORD = "issue-index-password"
        const val BIRTHDAY = 2_500_000L
        const val ZCASH_QUERY_ID = "zcash|native"
        const val MONERO_QUERY_ID = "monero|native"
        val MNEMONIC = AccountType.Mnemonic(List(11) { "abandon" } + "about", "")
    }
}
