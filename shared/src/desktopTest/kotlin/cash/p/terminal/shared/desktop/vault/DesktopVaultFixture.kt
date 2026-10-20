package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.pin.core.LockoutManager
import cash.p.terminal.modules.pin.core.LockoutUntilDateFactory
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.vault.PasswordVault
import io.horizontalsystems.core.CurrentDateProvider
import io.horizontalsystems.core.ILockoutStorage
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.io.path.createTempDirectory

internal class FakeLockoutStorage : ILockoutStorage {
    override var failedAttempts: Int? = null
    override var lockoutUptime: Long? = null
}

internal class FixedUptimeProvider(var current: Long = 1_000) : UptimeProvider {
    override val uptime: Long
        get() = current
}

/** A vault on a private temp directory; the collaborators a test does not look at are mocks. */
internal class DesktopVaultFixture(
    val dataDirectory: File = createTempDirectory("desktop-vault").toFile(),
    val preferencesReset: DesktopPreferencesReset = mockk(relaxed = true),
    val lockoutStorage: ILockoutStorage = FakeLockoutStorage(),
) {
    val files = DesktopVaultFiles(dataDirectory)
    val uptimeProvider = FixedUptimeProvider()
    val lockoutManager = LockoutManager(
        lockoutStorage,
        uptimeProvider,
        LockoutUntilDateFactory(CurrentDateProvider()),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun vault(
        driverFactory: SqlCipherDriverFactory = DefaultSqlCipherDriverFactory(),
        passwordVault: PasswordVault = PasswordVault(files.vaultFile),
        io: CoroutineDispatcher = Dispatchers.Unconfined,
    ): DesktopVault = DesktopVault(
        files = files,
        passwordVault = passwordVault,
        lockoutManager = lockoutManager,
        preferencesReset = preferencesReset,
        dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, scope, io = io),
        driverFactory = driverFactory,
    )

    fun tearDown() {
        dataDirectory.deleteRecursively()
    }
}

internal const val PASSWORD = "Correct-horse1"
internal const val NEW_PASSWORD = "Battery-staple2"

internal fun password(value: String = PASSWORD) = value.toCharArray()
