package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.AccountsStorage
import cash.p.terminal.entities.MoneroFileRecord
import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.vault.Argon2Params
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.SecretString
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal const val MAIN_PASSWORD = "Main-password1"
internal const val DURESS_PASSWORD = "Duress-password2"
internal const val NESTED_PASSWORD = "Nested-password3"
internal const val HIDDEN_PASSWORD = "Hidden-password4"

/** Not an Exception: nothing on the way up may treat it as a handled failure. */
internal class SimulatedCrash : Error("simulated crash")

/** Stops the real code path at the n-th time a step is reached; [onReached] acts during the operation. */
internal class TestCheckpoints : StorageCheckpoints {
    val reached = mutableListOf<StorageCheckpoint>()
    var onReached: (StorageCheckpoint) -> Unit = {}
    private var crashAt: StorageCheckpoint? = null
    private var occurrencesLeft = 0

    fun crashAt(checkpoint: StorageCheckpoint, occurrence: Int = 1) {
        crashAt = checkpoint
        occurrencesLeft = occurrence
    }

    override fun reached(checkpoint: StorageCheckpoint) {
        reached += checkpoint
        onReached(checkpoint)
        if (checkpoint == crashAt && --occurrencesLeft == 0) {
            crashAt = null
            throw SimulatedCrash()
        }
    }
}

/** Remembers which key each driver was made for, so a test can check what happened to one store's driver. */
internal class RecordingDriverFactory : SqlCipherDriverFactory {
    val drivers = mutableListOf<Pair<ByteArray, SqlCipherDriver>>()
    var failFor: ByteArray? = null

    fun driverFor(key: ByteArray): SqlCipherDriver = drivers.last { it.first.contentEquals(key) }.second

    override fun create(key: ByteArray): SqlCipherDriver {
        val driver = spyk(SqlCipherDriver(key))
        if (failFor?.contentEquals(key) == true) {
            every { driver.open(any()) } throws ExceptionInInitializerError(UnsatisfiedLinkError("no sqlcipher_jni"))
        }
        drivers += key.copyOf() to driver
        return driver
    }
}

/** Each crash point needs its own vault, and the fixture owns the global Koin graph. */
internal fun levelsScenario(block: suspend (VaultLevelsFixture) -> Unit) = runBlocking {
    val fixture = VaultLevelsFixture()
    try {
        block(fixture)
    } finally {
        fixture.tearDown()
    }
}

/** A vault on real SQLCipher files; [relogin] is a new process, as after a crash. */
internal class VaultLevelsFixture {
    private val base = DesktopVaultFixture()
    private val cheapArgon2 = mockk<Argon2Params> {
        every { memoryKib } returns 8
        every { iterations } returns 1
        every { parallelism } returns 1
    }

    val files = base.files
    val dataDirectory = base.dataDirectory
    val checkpoints = TestCheckpoints()
    val driverFactory = RecordingDriverFactory()
    val walletLayer = base.walletLayer
    val passwordVault: PasswordVault = PasswordVault(files.vaultFile, cheapArgon2)
    var vault: DesktopVault = newVault()
        private set
    private var decoys: Set<String> = emptySet()

    val router get() = DesktopStorageRouter(vault)
    val accountsStorage get() = AccountsStorage(router)
    val session get() = checkNotNull(vault.openSession())

    init {
        val encryption = object : IEncryptionManager {
            override fun encrypt(data: String) = DesktopSecretFieldsEncryption(vault).encrypt(data)
            override fun decrypt(data: String) = DesktopSecretFieldsEncryption(vault).decrypt(data)
        }
        startKoin { modules(module { single<IEncryptionManager> { encryption } }) }
    }

    suspend fun create(password: String = MAIN_PASSWORD) {
        assertEquals(CreateOutcome.SUCCESS, vault.create(password(password)))
        decoys = kitDirectories().map(File::getName).toSet()
    }

    suspend fun relogin(password: String = MAIN_PASSWORD): UnlockOutcome {
        vault.close()
        vault = newVault()
        return vault.unlock(password(password))
    }

    suspend fun reloginOk(password: String = MAIN_PASSWORD) = assertEquals(UnlockOutcome.Success, relogin(password))

    suspend fun setDuress(password: String = DURESS_PASSWORD) =
        assertEquals(LevelOutcome.SUCCESS, vault.setDuressPassword(password(password)))

    suspend fun createHidden(password: String = HIDDEN_PASSWORD) =
        assertEquals(LevelOutcome.SUCCESS, vault.createHiddenWallet(password(password)))

    /** Main with a duress holding [duressAccount] and a main-only [mainAccount]. */
    suspend fun mainWithDuress(mainAccount: String = MAIN_ACCOUNT, duressAccount: String = DURESS_ACCOUNT) {
        create()
        setDuress()
        addAccount(mainAccount)
        addAccount(duressAccount)
        router.setLevel(listOf(duressAccount), 1)
    }

    /** One more level than [mainWithDuress], entered through the duress password. */
    suspend fun mainWithNestedDuress() {
        mainWithDuress()
        reloginOk(DURESS_PASSWORD)
        setDuress(NESTED_PASSWORD)
        addAccount(NESTED_ACCOUNT)
        router.setLevel(listOf(NESTED_ACCOUNT), 1)
        reloginOk()
    }

    fun addAccount(id: String) = accountsStorage.save(account(id))

    fun store(id: String): AccountDaos = checkNotNull(router.account(id)) { "No store for $id" }

    /** What a running kit keeps writing into its account's store. */
    suspend fun writeKitRows(id: String, tag: String) {
        store(id).walletsDao().insert(EnabledWallet(tokenQueryId = "evm-token-$tag", accountId = id, coinImage = null))
        val secret = SecretString("monero-$tag")
        store(id).moneroFileDao().insert(MoneroFileRecord(id, secret, secret))
    }

    /** The one row with a Room flow over it. */
    suspend fun writeSwap(id: String) = writeSwapTo(store(id), id)

    suspend fun writeSwapTo(store: AccountDaos, id: String) {
        val amount = BigDecimal.ONE
        store.pendingMultiSwapDao().insert(
            PendingMultiSwap(
                id = "swap-$id", accountId = id, createdAt = 0, coinUidIn = "in", blockchainTypeIn = "in",
                amountIn = amount, coinUidIntermediate = "mid", blockchainTypeIntermediate = "mid", coinUidOut = "out",
                blockchainTypeOut = "out", leg1ProviderId = "provider", leg1IsOffChain = false,
                leg1TransactionId = null, leg1AmountOut = null, leg1Status = PendingMultiSwap.STATUS_PENDING,
                leg2ProviderId = null, leg2IsOffChain = null, leg2TransactionId = null, leg2AmountOut = null,
                leg2Status = PendingMultiSwap.STATUS_PENDING, expectedAmountOut = amount,
            )
        )
    }

    fun visibleAccounts(): Map<String, Int> = accountsStorage.allAccounts(0).associate { it.id to it.level }

    /** Contents only: file times are changed on purpose and say nothing about the data. */
    fun accountStoreHashes(): Map<String, String> = files.kitsDirectory.walkTopDown()
        .filter { it.isFile && !it.name.endsWith(".lck") }
        .associate { it.path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).contentToString() }

    fun pointerCount(accountId: String): Int =
        session.chain.safes.sumOf { safe -> safe.dao.pointers().count { it.accountId == accountId } }

    fun storeKey(accountId: String): ByteArray = session.chain.safes.flatMap { it.dao.pointers() }
        .first { it.accountId == accountId }.storeKey

    fun storeDirectory(accountId: String): File {
        val pointer = session.chain.safes.flatMap { it.dao.pointers() }.first { it.accountId == accountId }
        return files.accountStoreDirectory(pointer.storageId)
    }

    fun accountStoreDirectories(): Int = kitDirectories().count { it.name !in decoys }

    private fun kitDirectories() = files.kitsDirectory.listFiles().orEmpty().filter(File::isDirectory)

    fun opensWith(file: File, key: ByteArray): Boolean = try {
        SqlCipherDriver(key).use { driver ->
            driver.open(file.absolutePath).use { connection ->
                connection.prepare("SELECT count(*) FROM sqlite_master").use { it.step() }
            }
        }
        true
    } catch (e: Exception) {
        false
    }

    fun vaultDirectoryNames(): Set<String> = files.vaultDirectory.listFiles().orEmpty().map(File::getName).toSet()

    fun assertCrashes(block: () -> Unit) {
        assertFailsWith<SimulatedCrash> { block() }
    }

    suspend fun tearDown() {
        vault.close()
        stopKoin()
        base.tearDown()
    }

    private fun newVault() =
        base.vault(driverFactory = driverFactory, passwordVault = passwordVault, checkpoints = checkpoints)

    private fun account(id: String) = Account(
        id = id,
        name = id,
        type = AccountType.EvmAddress("0x$id"),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    companion object {
        const val MAIN_ACCOUNT = "main-account"
        const val DURESS_ACCOUNT = "duress-account"
        const val NESTED_ACCOUNT = "nested-account"
    }
}
