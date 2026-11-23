package cash.p.terminal.shared.desktop.session

import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashWalletOpener
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.core.managers.KitStorageNaming
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.TonConnectStorage
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.usecase.MoneroWalletStorage
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.shared.desktop.vault.CreateOutcome
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.shared.desktop.vault.KitDecoys
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.desktop.vault.awaitLevelRelease
import cash.p.terminal.shared.desktop.vault.password
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real kits and native SDKs on the real graph, offline: what a person with the data directory would read. */
class DesktopKitFilesScanTest {
    private val graph = DesktopWalletGraph(createTempDirectory("desktop-kit-scan").toFile()).start()
    private val files get() = graph.koin.get<DesktopVaultFiles>()
    private val kits get() = files.kitsDirectory
    private val session get() = checkNotNull(graph.vault.openSession())

    @AfterTest
    fun tearDown() = runBlocking<Unit> {
        // The native session is process-wide: a next test could not open its own.
        graph.koin.get<BeamSessionOwner>().close()
        graph.stop()
        graph.dataDirectory.deleteRecursively()
    }

    @Test
    fun kitFiles_walletsOfEveryType_namesSayNothingAboutChainCoinOrAccount() = runBlocking<Unit> {
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password()))
        val account = offlineAccount()
        val seen = allPaths().toMutableSet()

        KIT_TOKENS.forEach { (label, token) ->
            assertNotNull(adapter(token, account), "no adapter for $label")
            assertTrue(allPaths().filter(seen::add).any(File::isFile), "$label created no file")
        }
        withTimeout(NATIVE_TIMEOUT_MS) { graph.koin.get<ZcashWalletOpener>().open(wallet(native(Zcash), account)) }
        assertTrue(allPaths().filter(seen::add).any(File::isFile), "zcash created no file")
        assertIs<AccountType.MnemonicMonero>(
            withTimeout(NATIVE_TIMEOUT_MS) { graph.koin.get<MoneroWalletUseCase>().createNew() },
        )
        assertTrue(allPaths().filter(seen::add).any(File::isFile), "monero created no file")

        val named = allPaths().map { it.relativeTo(kits).path }
        assertEquals(emptyList(), named.filter { path -> path.split(File.separatorChar).any { !isAllowedName(it) } })
        assertTrue(named.none { account.id in it })
    }

    @Test
    fun kitFiles_everyKitOfAnAccount_liesInItsDirectory() = runBlocking<Unit> {
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password()))
        val first = offlineAccount()
        val second = offlineAccount()
        val outsideBefore = allPaths()
        val firstDirectory = accountDirectory(first)
        val secondDirectory = accountDirectory(second)

        KIT_TOKENS.forEach { (_, token) -> adapter(token, first) }
        graph.koin.get<ZcashDatabaseFiles>().databaseDir(first.id).mkdirs()
        graph.koin.get<TonConnectStorage>().platformContext(first.id)

        assertEquals(emptyList(), (allPaths() - outsideBefore).filterNot { it.startsWith(firstDirectory) })
        assertTrue(secondDirectory.walkTopDown().toSet().size < firstDirectory.walkTopDown().toSet().size)
        assertTrue(graph.koin.get<BeamStorageLocator>().storagePath(first.id).startsWith(firstDirectory))
        assertEquals(KitDecoys.MAX_KIT_DIRECTORIES, firstDirectory.listFiles().orEmpty().count(File::isDirectory))
    }

    /** The hard requirement: moving a wallet between levels must never make it sync again. */
    @Test
    fun setLevel_narrowingAndCollapse_keepEveryKitsKeyAndFilesWithoutResync() = runBlocking<Unit> {
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password(MAIN_PASSWORD)))
        assertEquals(LevelOutcome.SUCCESS, graph.vault.setDuressPassword(password(DURESS_PASSWORD)))
        val account = offlineAccount()
        val monero = moneroAccount()
        KIT_TOKENS.forEach { adapter(it.second, account) }
        graph.koin.get<ZcashDbKeyProvider>().keyFor(account.id)
        val kept = snapshotOf(account, monero)
        writeProgress(kept.evmDatabase, keyOf(account, "evm"))

        moveBothTo(1, account, monero)
        val running = session
        graph.vault.lock()
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password(DURESS_PASSWORD)))
        graph.vault.awaitLevelRelease()
        assertSame(running, session, "a narrowing reopened the session")
        assertKept(kept, account, monero)

        relogin(DURESS_PASSWORD)
        assertKept(kept, account, monero)

        moveBothTo(0, account, monero)
        relogin(DURESS_PASSWORD)
        assertKept(kept, account, monero)

        relogin(MAIN_PASSWORD)
        graph.koin.get<StorageRouter>().collapseLevelsAbove(0)
        assertKept(kept, account, monero)
    }

    private suspend fun moneroAccount(): Account {
        val type = withTimeout(NATIVE_TIMEOUT_MS) { graph.koin.get<MoneroWalletUseCase>().createNew() }
        return mnemonicAccount(isBackedUp = true).copy(type = checkNotNull(type)).also { graph.accountManager.save(it) }
    }

    private fun moveBothTo(level: Int, account: Account, monero: Account) =
        graph.koin.get<StorageRouter>().setLevel(listOf(account.id, monero.id), level)

    private suspend fun relogin(password: String) {
        // The native session is process-wide, and a restart in one JVM does not end it.
        graph.koin.get<BeamSessionOwner>().close()
        graph.restart()
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password(password)))
    }

    private fun snapshotOf(account: Account, monero: Account) = KeptKits(
        keys = KIT_KEY_NAMES.associateWith { name ->
            assertTrue(hasKey(account, name), "no wallet of the test issued the key of $name")
            keyOf(account, name)
        },
        names = KIT_KEY_NAMES.associateWith(resolverOf(account)::resolve),
        files = durableFiles(account),
        zcashDatabase = graph.koin.get<ZcashDatabaseFiles>().databaseFile(account.id).path,
        moneroFiles = moneroFilesOf(monero),
        evmDatabase = kitDatabases(account).first(),
    )

    private fun assertKept(kept: KeptKits, account: Account, monero: Account) {
        kept.keys.forEach { (name, key) ->
            assertContentEquals(key, keyOf(account, name), "a level change replaced the key of $name")
            assertTrue(session.kitKey(account.id, name)?.isNew == false, "a level change reissued the key of $name")
        }
        assertEquals(kept.names, KIT_KEY_NAMES.associateWith(resolverOf(account)::resolve))
        assertEquals(kept.zcashDatabase, graph.koin.get<ZcashDatabaseFiles>().databaseFile(account.id).path)
        assertEquals(emptySet(), kept.files - durableFiles(account))
        assertEquals(kept.moneroFiles, moneroFilesOf(monero))
        assertEquals(PROGRESS, readProgress(kept.evmDatabase, keyOf(account, "evm")), "the kit started over")
    }

    /** A journal or a WAL comes and goes with an open connection. */
    private fun durableFiles(account: Account): Set<String> = accountDirectory(account).walkTopDown()
        .map { it.path }.filterNot { it.endsWith("-journal") || it.endsWith("-wal") || it.endsWith("-shm") }.toSet()

    private fun resolverOf(account: Account) = graph.koin.get<KitStorageNaming>().resolverFor(account.id)

    private fun hasKey(account: Account, name: String) = checkNotNull(session.account(account.id)).hasKitKey(name)

    private fun keyOf(account: Account, name: String) = checkNotNull(session.kitKey(account.id, name)).bytes.copyOf()

    private fun moneroFilesOf(account: Account): Set<String> {
        val name = (account.type as AccountType.MnemonicMonero).walletInnerName
        val root = graph.koin.get<MoneroWalletStorage>().walletsRoot()
        return root.listFiles().orEmpty().map(File::getPath).filter { "/$name" in it }.toSet().also {
            assertTrue(it.isNotEmpty(), "the Monero wallet has no files")
        }
    }

    private class KeptKits(
        val keys: Map<String, ByteArray>,
        val names: Map<String, String>,
        val files: Set<String>,
        val zcashDatabase: String,
        val moneroFiles: Set<String>,
        val evmDatabase: File,
    )

    private suspend fun offlineAccount(): Account {
        val account = mnemonicAccount(isBackedUp = true)
        graph.accountManager.save(account)
        (KIT_TOKENS.map { it.second } + native(Zcash)).map { it.blockchainType }.distinct().forEach {
            graph.koin.get<OfflineModeManager>().persistAndPublish(account.id, it, offline = true)
        }
        return account
    }

    private suspend fun adapter(token: Token, account: Account) =
        graph.koin.get<AdapterFactory>().getAdapterOrNull(wallet(token, account))

    private fun wallet(token: Token, account: Account): Wallet =
        requireNotNull(graph.koin.get<WalletFactory>().create(token, account, null))

    private fun accountDirectory(account: Account): File =
        files.accountStoreDirectory(checkNotNull(session.account(account.id)).storageId)

    private fun kitDatabases(account: Account): List<File> = files
        .kitDirectory(checkNotNull(session.account(account.id)).storageId, "ethereum")
        .listFiles().orEmpty()
        .filter { it.isFile && it.name.matches(Regex("[0-9a-f]{32}")) }
        .sortedBy(File::getName)

    private fun allPaths(): List<File> = kits.walkTopDown().filter { it != kits }.toList()

    private fun writeProgress(database: File, key: ByteArray) = sql(database, key) { execute ->
        execute("CREATE TABLE sync_progress (height TEXT)")
        execute("INSERT INTO sync_progress VALUES ('$PROGRESS')")
    }

    private fun readProgress(database: File, key: ByteArray): String = SqlCipherDriver(key).use { driver ->
        driver.open(database.absolutePath).use { connection ->
            connection.prepare("SELECT height FROM sync_progress").use { statement ->
                check(statement.step())
                statement.getText(0)
            }
        }
    }

    private fun sql(database: File, key: ByteArray, block: ((String) -> Unit) -> Unit) =
        SqlCipherDriver(key).use { driver ->
            driver.open(database.absolutePath).use { connection ->
                block { query -> connection.prepare(query).use { it.step() } }
            }
        }

    internal companion object {
        const val NATIVE_TIMEOUT_MS = 120_000L
        const val PROGRESS = "synced-to-block-42"

        val Zcash = BlockchainType.Zcash

        /** What the SDKs fix themselves carries no chain, coin or account either. */
        val OPAQUE = Regex("\\.?[0-9a-f]{32}(-sqlcipher\\.lock|\\.lck|-wal|-shm|-journal)?")
        val SDK_FIXED = Regex(
            "wallet\\.db(-journal|-wal|-shm)?|wallet_\\d+(\\.keys|\\.address\\.txt)?|" +
                "neutrino\\.db|peers\\.json|block_headers\\.bin|reg_filter_headers\\.bin|contacts\\.enc",
        )

        fun isAllowedName(name: String) = OPAQUE.matches(name) || SDK_FIXED.matches(name)

        fun native(type: BlockchainType) = token(type, TokenType.Native)

        fun token(type: BlockchainType, tokenType: TokenType) = Token(
            coin = Coin(uid = type.uid, name = type.uid, code = type.uid),
            blockchain = Blockchain(type, type.uid, null),
            type = tokenType,
            decimals = 8,
        )

        val KIT_TOKENS = listOf(
            "ethereum" to native(BlockchainType.Ethereum),
            "erc20" to token(BlockchainType.Ethereum, TokenType.Eip20("0xdAC17F958D2ee523a2206206994597C13D831ec7")),
            "bsc" to native(BlockchainType.BinanceSmartChain),
            "solana" to native(BlockchainType.Solana),
            "tron" to native(BlockchainType.Tron),
            "ton" to native(BlockchainType.Ton),
            "stellar" to native(BlockchainType.Stellar),
            "thorchain" to native(BlockchainType.Thorchain),
            "maya" to native(BlockchainType.Mayachain),
            "bitcoin" to token(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84)),
            "bch" to token(BlockchainType.BitcoinCash, TokenType.AddressTyped(TokenType.AddressType.Type145)),
            "litecoin" to token(BlockchainType.Litecoin, TokenType.Derived(TokenType.Derivation.Bip84)),
            "mweb" to token(BlockchainType.Litecoin, TokenType.Mweb),
            "dash" to native(BlockchainType.Dash),
            "doge" to native(BlockchainType.Dogecoin),
            "ecash" to native(BlockchainType.ECash),
            "cosanta" to native(BlockchainType.Cosanta),
            "piratecash" to native(BlockchainType.PirateCash),
            "beam" to native(BlockchainType.Beam),
        )

        /** Every name a desktop kit keeps a key under; Thorchain and MWEB have no database key of their own. */
        val KIT_KEY_NAMES = listOf("evm", "solana", "tron", "ton", "stellar", "bitcoin", "beam-mainnet", "zcash")
    }
}
