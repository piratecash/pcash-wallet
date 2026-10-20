package cash.p.terminal.modules.backuplocal

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.modules.backuplocal.fullbackup.AppSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.FullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import com.google.gson.Gson
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertNotNull

/** Backup files written by the Android app before the split restore through the common provider on desktop. */
class GoldenBackupRestoreDesktopTest {

    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val accountFactory = mockk<IAccountFactory>(relaxed = true)
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)
    private val marketFavoritesManager = mockk<MarketFavoritesManager>(relaxed = true)
    private val appSettingsBackup = mockk<AppSettingsBackup>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>()

    private val provider = WalletBackupProvider(
        walletStorage = mockk(relaxed = true),
        settingsManager = mockk(relaxed = true),
        accountManager = accountManager,
        accountFactory = accountFactory,
        walletManager = walletManager,
        restoreSettingsManager = restoreSettingsManager,
        marketFavoritesManager = marketFavoritesManager,
        appSettingsBackup = appSettingsBackup,
    )

    @Before
    fun setUp() {
        EthereumKit.init()
        every { accountFactory.getUniqueName(any(), any()) } answers { firstArg() }
        every { accountFactory.account(any(), any(), any(), any(), any()) } answers {
            Account(firstArg(), firstArg(), secondArg(), thirdArg(), level = 0, isBackedUp = arg(3), isFileBackedUp = arg(4))
        }
        every { accountFactory.watchAccount(any(), any()) } answers {
            Account(firstArg(), firstArg(), secondArg(), AccountOrigin.Restored, level = 0, isBackedUp = true)
        }
        val curated = listOf(ETH_QUERY_ID, USDT_QUERY_ID).associateWith(::curatedEthereumToken)
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().mapNotNull { curated[it.id] }
        }
        startKoin { modules(module { single { marketKit } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun restoreFullBackup_goldenV3_restoresAccountsWalletsAndWatchlist() = runTest {
        val backupV3 = assertNotNull(provider.parseV3Backup(resourceText("golden_v3_full.json")))
        val (innerJson, key, kdfParams) = provider.unwrapV3FormatWithKey(backupV3, PRIMARY_PASSWORD)
        val fullBackup = Gson().fromJson(innerJson, FullBackup::class.java)
        val decrypted =
            provider.decryptedFullBackupWithKey(fullBackup, key, kdfParams, PRIMARY_PASSWORD, BackupSource.Authenticated)

        assertEquals(listOf(MNEMONIC_NAME to MNEMONIC, WATCH_NAME to WATCH), decrypted.accountTypes())
        assertEquals("de", decrypted.settings?.language)

        val savedWallets = restore(decrypted)

        assertEquals(PRIMARY_WALLETS, savedWallets.map { it.accountId to it.tokenQueryId })
        verifyZcashBirthdayRestored()
        coVerify { marketFavoritesManager.addAll(listOf("bitcoin", "ethereum")) }
        coVerify { appSettingsBackup.restore(match { it.language == "de" }, PRIMARY_PASSWORD) }
    }

    @Test
    fun restoreSingleWalletBackup_goldenV3_restoresAccountAndWallets() = runTest {
        val backupV3 = assertNotNull(provider.parseV3Backup(resourceText("golden_v3_single.json")))
        val (innerJson, key, kdfParams) = provider.unwrapV3FormatWithKey(backupV3, PRIMARY_PASSWORD)
        val walletBackup = Gson().fromJson(innerJson, BackupLocalModule.WalletBackup::class.java)
        val type = provider.accountTypeWithKey(walletBackup, key, kdfParams, PRIMARY_PASSWORD)

        assertEquals(MNEMONIC, type)

        val savedWallets = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(savedWallets)) } returns Unit
        val outcome = provider.restoreSingleWalletBackup(
            requireNotNull(type), MNEMONIC_NAME, walletBackup, BackupSource.Authenticated
        )

        assertEquals(RestoreOutcome.Restored, outcome)
        assertEquals(PRIMARY_WALLETS.take(3), savedWallets.captured.map { it.accountId to it.tokenQueryId })
        verifyZcashBirthdayRestored()
    }

    @Test
    fun restoreFromV4BinaryBackup_goldenPrimaryPassword_restoresPrimaryPayload() = runTest {
        val decrypted = assertNotNull(provider.restoreFromV4BinaryBackup(resourceBytes(), PRIMARY_PASSWORD))

        assertEquals(listOf(MNEMONIC_NAME to MNEMONIC, WATCH_NAME to WATCH), decrypted.accountTypes())

        val savedWallets = restore(decrypted)

        assertEquals(PRIMARY_WALLETS, savedWallets.map { it.accountId to it.tokenQueryId })
        verifyZcashBirthdayRestored()
    }

    @Test
    fun restoreFromV4BinaryBackup_goldenHiddenPassword_restoresOnlyHiddenPayload() = runTest {
        val decrypted = assertNotNull(provider.restoreFromV4BinaryBackup(resourceBytes(), HIDDEN_PASSWORD))

        assertEquals(listOf(HIDDEN_NAME to HIDDEN), decrypted.accountTypes())
        assertEquals(listOf(HIDDEN_NAME to ETH_QUERY_ID), restore(decrypted).map { it.accountId to it.tokenQueryId })
    }

    @Test
    fun restoreFromV4BinaryBackup_wrongPassword_findsNothing() = runTest {
        assertNull(provider.restoreFromV4BinaryBackup(resourceBytes(), "not-a-golden-password"))
    }

    private suspend fun restore(decrypted: DecryptedFullBackup): List<EnabledWallet> {
        val savedWallets = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(savedWallets)) } returns Unit

        assertEquals(RestoreOutcome.Restored, provider.restoreFullBackup(decrypted, PRIMARY_PASSWORD))
        verify { accountManager.import(decrypted.wallets.map { it.account }) }
        return savedWallets.captured
    }

    private fun verifyZcashBirthdayRestored() {
        verify {
            restoreSettingsManager.save(
                match<RestoreSettings> { it.birthdayHeight == ZCASH_BIRTHDAY },
                match { it.name == MNEMONIC_NAME },
                BlockchainType.Zcash
            )
        }
    }

    private fun DecryptedFullBackup.accountTypes() = wallets.map { it.account.name to it.account.type }

    private fun curatedEthereumToken(tokenQueryId: String) = Token(
        coin = Coin(uid = tokenQueryId, name = "Curated", code = "CUR"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = requireNotNull(TokenQuery.fromId(tokenQueryId)).tokenType,
        decimals = 18
    )

    private fun resourceText(name: String) = String(resource(name), Charsets.UTF_8)

    private fun resourceBytes() = resource("golden_v4_full_dual.bin")

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream("backup/$name")).use { it.readBytes() }

    private companion object {
        // Test-only passwords and the public BIP-39 test vectors the fixtures were generated with.
        const val PRIMARY_PASSWORD = "golden-primary-password"
        const val HIDDEN_PASSWORD = "golden-hidden-password"
        const val MNEMONIC_NAME = "Golden Mnemonic"
        const val WATCH_NAME = "Golden Watch"
        const val HIDDEN_NAME = "Golden Hidden"
        const val ZCASH_BIRTHDAY = 2_500_000L
        const val ETH_QUERY_ID = "ethereum|native"
        const val USDT_QUERY_ID = "ethereum|eip20:0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val ZCASH_QUERY_ID = "zcash|native"

        val MNEMONIC = AccountType.Mnemonic(List(11) { "abandon" } + "about", "golden-passphrase")
        val WATCH = AccountType.EvmAddress("0x000000000000000000000000000000000000dEaD")
        val HIDDEN = AccountType.Mnemonic(
            "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" "),
            ""
        )
        val PRIMARY_WALLETS = listOf(
            MNEMONIC_NAME to ETH_QUERY_ID,
            MNEMONIC_NAME to USDT_QUERY_ID,
            MNEMONIC_NAME to ZCASH_QUERY_ID,
            WATCH_NAME to ETH_QUERY_ID,
        )
    }
}
