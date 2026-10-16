package cash.p.terminal.shared.desktop.session

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.premium.domain.usecase.SeedToEvmAddressUseCase
import cash.p.terminal.shared.desktop.vault.CreateOutcome
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import cash.p.terminal.shared.desktop.vault.password
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.managers.UserManager
import cash.p.terminal.wallet.useCases.MoneroWalletFiles
import io.mockk.coEvery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWalletStorageTest {

    private val graph = DesktopWalletGraph(createTempDirectory("desktop-wallet").toFile()).start()

    @AfterTest
    fun tearDown() = runBlocking<Unit> {
        graph.stop()
        graph.dataDirectory.deleteRecursively()
    }

    @Test
    fun create_unlockedVault_loadsTheWalletAtLevelZero() = runBlocking<Unit> {
        create()

        assertEquals(0, graph.koin.get<UserManager>().getUserLevel())
        assertIs<ActiveAccountState.ActiveAccount>(graph.accountManager.activeAccountStateFlow.first())
    }

    @Test
    fun lock_unlockedWallet_keepsWritingAndTheUnlockShowsEverything() = runBlocking<Unit> {
        create()
        val first = save(mnemonicAccount(isBackedUp = true))

        graph.vault.lock()
        val second = save(mnemonicAccount(isBackedUp = true))
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password()))

        assertEquals(setOf(first.id, second.id), graph.accountManager.accounts.map(Account::id).toSet())
    }

    @Test
    fun wipe_loadedWallet_unloadsAccountsAndClosesBothDatabases() = runBlocking<Unit> {
        create()
        save(mnemonicAccount(isBackedUp = true))

        assertEquals(WipeOutcome.SUCCESS, graph.wipe())

        assertTrue(graph.accountManager.accounts.isEmpty())
        assertNull(graph.accountManager.activeAccount)
        assertEquals(ActiveAccountState.NotLoaded, graph.accountManager.activeAccountStateFlow.first())
        assertEquals(UserManager.DEFAULT_USER_LEVEL, graph.koin.get<UserManager>().getUserLevel())
        assertFailsWith<StorageLockedException> { graph.koin.get<AppDatabaseProvider>().database }
        assertFailsWith<StorageLockedException> { graph.koin.get<PremiumDatabaseProvider>().database }
    }

    @Test
    fun wipe_accountWithoutBackup_dropsTheBackupAlertAndTheNextWalletStartsWithoutIt() = runBlocking<Unit> {
        create()
        val unprotected = save(mnemonicAccount(isBackedUp = false))
        assertEquals(unprotected, graph.accountManager.newAccountBackupRequiredFlow.value)

        graph.wipe()
        assertNull(graph.accountManager.newAccountBackupRequiredFlow.value)

        create()
        save(mnemonicAccount(isBackedUp = true))
        assertNull(graph.accountManager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun create_afterWipe_writesLandInTheNewVault() = runBlocking<Unit> {
        create()
        save(mnemonicAccount(isBackedUp = true))
        graph.wipe()
        create()
        val restored = save(mnemonicAccount(isBackedUp = true).copy(origin = AccountOrigin.Restored))

        graph.restart()
        assertFailsWith<StorageLockedException> { graph.koin.get<AppDatabaseProvider>().database }
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password()))

        assertEquals(listOf(restored.id), graph.accountManager.accounts.map(Account::id))
    }

    @Test
    fun restart_trialPremium_survivesTheRestartIsEncryptedAndIsGoneAfterWipe() = runBlocking<Unit> {
        coEvery { graph.piratePlaceRepository.activateTrialPremium(any()) } returns TrialPremiumResult.DemoActive(7)
        create()
        val account = save(mnemonicAccount(isBackedUp = true))
        assertIs<TrialPremiumResult.DemoActive>(graph.checkPremiumUseCase.activateTrialPremium(account.id))

        graph.restart()
        graph.vault.unlock(password())

        assertIs<TrialPremiumResult.DemoActive>(graph.checkPremiumUseCase.checkTrialPremiumStatus())
        graph.closeVault()
        val raw = graph.dataDirectory.rawBytes()
        assertFalse(raw.contains(SeedToEvmAddressUseCase()(TEST_WORDS)), "the BNB address is stored in plaintext")
        assertFalse(raw.contains(TEST_WORDS.first()), "the mnemonic is stored in plaintext")

        graph.vault.unlock(password())
        graph.wipe()
        create()
        save(mnemonicAccount(isBackedUp = true))
        assertEquals(TrialPremiumResult.NeedPremium, graph.checkPremiumUseCase.checkTrialPremiumStatus())
    }

    @Test
    fun save_moneroAccount_storesTheWalletPasswordEncrypted() = runBlocking<Unit> {
        create()
        val walletPassword = graph.koin.get<MoneroWalletStorage>().newWalletPassword()
        val monero = save(moneroAccount(walletPassword))

        graph.restart()
        graph.vault.unlock(password())
        val stored = graph.accountManager.account(monero.id)?.type as? AccountType.MnemonicMonero

        assertEquals(walletPassword, stored?.password)
        graph.closeVault()
        assertFalse(graph.dataDirectory.rawBytes().contains(walletPassword), "the Monero password is stored in plaintext")
    }

    @Test
    fun delete_moneroAccount_removesItsWalletFiles() = runBlocking<Unit> {
        create()
        val monero = save(moneroAccount(walletPassword = "file password"))
        val files = MoneroWalletFiles(File(graph.koin.get<MoneroWalletStorage>().walletsRoot(), WALLET_INNER_NAME))
        files.all.forEach { it.writeText("wallet") }

        graph.accountManager.delete(monero.id)

        assertTrue(files.all.none(File::exists), "the Monero wallet files outlived the account")
        assertNull(graph.accountManager.account(monero.id))
    }

    private suspend fun create() = assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password()))

    private fun save(account: Account): Account = account.also { graph.accountManager.save(it) }

    private fun moneroAccount(walletPassword: String) = Account(
        id = UUID.randomUUID().toString(),
        name = "Monero",
        type = AccountType.MnemonicMonero(
            words = List(MONERO_WORDS) { "monerotestword$it" },
            password = walletPassword,
            height = 0,
            walletInnerName = WALLET_INNER_NAME,
        ),
        origin = AccountOrigin.Restored,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        const val MONERO_WORDS = 25
        const val WALLET_INNER_NAME = "monero-wallet"
    }
}
