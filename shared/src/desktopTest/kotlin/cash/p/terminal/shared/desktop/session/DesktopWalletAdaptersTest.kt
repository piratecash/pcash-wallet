package cash.p.terminal.shared.desktop.session

import cash.p.terminal.core.managers.DeletedAccountsCleanup
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.shared.desktop.DesktopAppExit
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.vault.CreateOutcome
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.desktop.vault.password
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop wallet services over the real graph; the EVM network stays paused, so no kit goes online. */
class DesktopWalletAdaptersTest {

    private val graph = DesktopWalletGraph(createTempDirectory("desktop-adapters").toFile()).start()

    private val adapterManager: IAdapterManager get() = graph.koin.get()
    private val ethereumKits: File get() = File(graph.koin.get<DesktopVaultFiles>().kitsDirectory, "ethereum")

    @AfterTest
    fun tearDown() = runBlocking<Unit> {
        graph.stop()
        graph.dataDirectory.deleteRecursively()
    }

    @Test
    fun unlock_evmAndBitcoinWallets_startsOnlyTheEvmAdapter() = runBlocking<Unit> {
        val account = createWithOfflineEthereumAccount()
        val ethereum = wallet(ETHEREUM, account)
        val bitcoin = wallet(BITCOIN, account)

        graph.activeWallets.value = listOf(ethereum, bitcoin)

        assertNotNull(adapterManager.awaitAdapterForWallet<IBalanceAdapter>(ethereum, ADAPTER_TIMEOUT_MS))
        assertNull(adapterManager.getAdapterForWallet<IAdapter>(bitcoin))
    }

    @Test
    fun receiveAddress_mnemonicAccount_isTheAndroidSignerAddress() = runBlocking<Unit> {
        val account = createWithOfflineEthereumAccount()
        val ethereum = wallet(ETHEREUM, account)
        graph.activeWallets.value = listOf(ethereum)
        adapterManager.awaitAdapterForWallet<IBalanceAdapter>(ethereum, ADAPTER_TIMEOUT_MS)

        val address = adapterManager.getReceiveAdapterForWallet(ethereum)?.receiveAddress

        val seed = (account.type as AccountType.Mnemonic).seed
        assertEquals(Signer.address(seed, Chain.Ethereum).eip55, address)
        assertEquals(BIP39_VECTOR_ETHEREUM_ADDRESS, address)
    }

    @Test
    fun wipe_runningEvmAdapter_erasesTheVaultAndRequestsARelaunch() = runBlocking<Unit> {
        val account = createWithOfflineEthereumAccount()
        val ethereum = wallet(ETHEREUM, account)
        graph.activeWallets.value = listOf(ethereum)
        assertNotNull(adapterManager.awaitAdapterForWallet<IBalanceAdapter>(ethereum, ADAPTER_TIMEOUT_MS))
        val files = graph.koin.get<DesktopVaultFiles>()
        graph.checkPremiumUseCase.stopWarmUp()

        graph.koin.get<DesktopWipeCoordinator>().wipe()
        withTimeout(WIPE_TIMEOUT_MS) { graph.koin.get<DesktopAppExit>().requested.first { it } }

        verify { graph.relauncher.relaunch(wipeFailed = false) }
        assertTrue(files.slotFiles().none(File::exists), "a vault slot outlived the wipe")
        assertTrue(files.databaseFiles().none(File::exists), "the database outlived the wipe")
        // A kit still writing after the wipe leaves files behind: the next vault starts without them.
        File(ethereumKits.apply { mkdirs() }, "residual.db").writeText("left over")
        graph.restart()
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password()))
        assertFalse(File(ethereumKits, "residual.db").exists(), "the new vault kept a residual kit file")
    }

    @Test
    fun deletedAccountsCleanup_deletedEvmAccount_deletesItsKitDatabasesOnTheNextLaunch() = runBlocking<Unit> {
        val account = createWithOfflineEthereumAccount()
        val ethereum = wallet(ETHEREUM, account)
        graph.activeWallets.value = listOf(ethereum)
        assertNotNull(adapterManager.awaitAdapterForWallet<IBalanceAdapter>(ethereum, ADAPTER_TIMEOUT_MS))
        // Only the databases: the kit's clear() leaves the SQLCipher lock files behind.
        val kitFiles = ethereumKits.listFiles().orEmpty().filterNot { it.extension in LOCK_FILE_EXTENSIONS }
        assertTrue(kitFiles.isNotEmpty(), "the EVM adapter created no kit database")

        graph.accountManager.delete(account.id)
        graph.activeWallets.value = emptyList()
        graph.restart()
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password()))
        graph.koin.get<DeletedAccountsCleanup>().invoke()

        assertTrue(kitFiles.none(File::exists), "kit files of the deleted account: ${kitFiles.filter(File::exists)}")
    }

    /** Offline before any adapter exists, so the EVM kit never starts its sync. */
    private suspend fun createWithOfflineEthereumAccount(): Account {
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password()))
        val account = mnemonicAccount(isBackedUp = true)
        graph.accountManager.save(account)
        graph.koin.get<OfflineModeManager>().persistAndPublish(account.id, BlockchainType.Ethereum, offline = true)
        return account
    }

    private fun wallet(token: Token, account: Account): Wallet =
        requireNotNull(graph.koin.get<WalletFactory>().create(token, account, null))

    private companion object {
        const val ADAPTER_TIMEOUT_MS = 10_000L
        const val WIPE_TIMEOUT_MS = 10_000L
        val LOCK_FILE_EXTENSIONS = setOf("lck", "lock")

        // m/44'/60'/0'/0/0 of the BIP-39 "abandon ... about" phrase.
        const val BIP39_VECTOR_ETHEREUM_ADDRESS = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

        val ETHEREUM = Token(
            coin = Coin(uid = "ethereum", name = "Ethereum", code = "ETH"),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = TokenType.Native,
            decimals = 18,
        )
        val BITCOIN = Token(
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
            type = TokenType.Derived(TokenType.Derivation.Bip84),
            decimals = 8,
        )
    }
}
