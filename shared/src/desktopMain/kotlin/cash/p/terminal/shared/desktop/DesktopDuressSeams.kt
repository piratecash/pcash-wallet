package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cash.p.terminal.feature.wallet.PinStateInfo
import cash.p.terminal.modules.pin.DuressNote
import cash.p.terminal.modules.pin.DuressSecretController
import cash.p.terminal.modules.pin.DuressSecretPages
import cash.p.terminal.modules.pin.DuressWording
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_duress_password_description
import cash.p.terminal.resources.desktop_duress_password_note_change_description
import cash.p.terminal.resources.desktop_duress_password_note_change_title
import cash.p.terminal.resources.desktop_hidden_wallet_password_terms
import cash.p.terminal.resources.desktop_hidden_wallet_password_terms_warning
import cash.p.terminal.resources.ic_edit_24
import cash.p.terminal.shared.desktop.lock.HiddenWalletPasswordPage
import cash.p.terminal.shared.desktop.lock.SetDuressPasswordPage
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DuressAvailability
import cash.p.terminal.shared.desktop.vault.HiddenPasswordResolution
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.shared.main.MainTabStorage
import cash.p.terminal.wallet.managers.UserManager
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Carries "open the wallet creation" from the session that made a hidden wallet to the one that enters it.
 * Only that wallet acts on it: any other one shown first drops the request, so nothing leaks into it.
 */
class DesktopHiddenWalletEntry(private val mainTabStorage: MainTabStorage) {
    private val pending = AtomicBoolean(false)
    private val entries = MutableStateFlow(0)

    @Volatile
    private var enteredRootSlot: Int? = null

    /** True only while an entry runs; the lock screen shows a loading state instead of the unlock form. */
    val running: Flow<Boolean> = entries.map { it > 0 }

    fun request() {
        mainTabStorage.mainTab = MainDestination.Balance
        enteredRootSlot = null
        pending.set(true)
    }

    fun enteredAt(rootSlot: Int) {
        enteredRootSlot = rootSlot
    }

    fun cancel() = pending.set(false)

    /** Until then the wallet the entry opens is not named yet. */
    suspend fun awaitEntered() = entries.first { it == 0 }

    /** True once per request, and only for the wallet it was made for. */
    fun consume(rootSlot: Int): Boolean = pending.getAndSet(false) && rootSlot == enteredRootSlot

    suspend fun <T> whileEntering(block: suspend () -> T): T {
        entries.update { it + 1 }
        try {
            return block()
        } finally {
            entries.update { it - 1 }
        }
    }
}

class DesktopDuressSecretController(
    private val vault: DesktopVault,
    private val userManager: UserManager,
    private val hiddenWalletEntry: DesktopHiddenWalletEntry,
    private val dispatcherProvider: DispatcherProvider,
) : DuressSecretController {
    private val logger = Logger.withTag("DuressSecretController")

    override suspend fun setDuressSecret(secret: CharArray): LevelOutcome = vault.setDuressPassword(secret)

    override suspend fun disableDuress() {
        try {
            withContext(dispatcherProvider.io) { userManager.disallowAccountsForDuress() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Removing the duress level failed" }
        }
    }

    /** An empty hidden wallet is entered again with its password, as on Android. */
    override suspend fun createHiddenWallet(secret: CharArray): LevelOutcome = try {
        val created = when (vault.resolveHiddenPassword(secret.copyOf())) {
            HiddenPasswordResolution.NEW -> vault.createHiddenWallet(secret.copyOf())
            HiddenPasswordResolution.REUSE_EMPTY -> LevelOutcome.SUCCESS
            HiddenPasswordResolution.IN_USE -> LevelOutcome.PASSWORD_IN_USE
            HiddenPasswordResolution.FAILED -> LevelOutcome.FAILED
        }
        if (created == LevelOutcome.SUCCESS) enterHiddenWallet(secret.copyOf())
        created
    } finally {
        secret.fill('\u0000')
    }

    override suspend fun isDuressSet(): Boolean = vault.duressAvailability() == DuressAvailability.SET

    override suspend fun isDuressAvailable(): Boolean = vault.duressAvailability() != DuressAvailability.UNAVAILABLE

    /** An unlock does nothing while unlocked, so the session is locked first; the lock screen shows a spinner meanwhile. */
    private suspend fun enterHiddenWallet(password: CharArray) {
        hiddenWalletEntry.request()
        hiddenWalletEntry.whileEntering {
            vault.lock()
            val unlocked = vault.unlock(password) == UnlockOutcome.Success
            // On failure the wallet exists and its password enters it from the lock screen.
            val rootSlot = vault.openSession()?.rootSlot
            if (unlocked && rootSlot != null) hiddenWalletEntry.enteredAt(rootSlot) else hiddenWalletEntry.cancel()
        }
    }
}

class DesktopDuressSecretPages : DuressSecretPages {
    override val wording = DuressWording(
        introDescription = Res.string.desktop_duress_password_description,
        introNotes = listOf(
            DuressNote(
                Res.drawable.ic_edit_24,
                Res.string.desktop_duress_password_note_change_title,
                Res.string.desktop_duress_password_note_change_description,
            )
        ),
        termsWarning = Res.string.desktop_hidden_wallet_password_terms_warning,
        termsCheckboxes = Res.array.desktop_hidden_wallet_password_terms,
    )

    override fun setDuress(accountIds: List<String>?): HSPage = SetDuressPasswordPage(accountIds.orEmpty())

    @Composable
    override fun rememberOpenHiddenWalletSecret(navigation: HSNavigation): () -> Unit =
        remember(navigation) { { navigation.slideFromRight(HiddenWalletPasswordPage()) } }
}

/** The password is the only secret on desktop, and it always exists while a session is open. */
class DesktopPinStateInfo(private val vault: DesktopVault) : PinStateInfo {
    override val isPinSet = true

    override fun isDuressPinSet() = vault.openSession()?.levels?.duressAvailability == DuressAvailability.SET

    override fun getDuressLevel() = (vault.openSession()?.rootLevel ?: 0) + 1

    override fun validateCurrentLevel(pin: String) = false
}
