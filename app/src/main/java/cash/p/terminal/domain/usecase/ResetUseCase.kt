package cash.p.terminal.domain.usecase

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import cash.p.terminal.core.managers.KeyStoreCleaner
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.tor.torcore.TorConstants
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.strings.helpers.LocaleHelper
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.widgets.MarketWidget
import cash.p.terminal.widgets.MarketWidgetStateDefinition
import cash.p.terminal.widgets.MarketWidgetWorker
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class ResetUseCase(
    private val context: Context,
    private val localStorage: ILocalStorage,
    private val appDatabase: AppDatabase,
    private val contactsRepository: ContactsRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val glanceManager: GlanceAppWidgetManager,
    private val appIconService: AppIconService,
    private val deletionPreflight: AccountDeletionPreflight,
    private val keyStoreCleaner: KeyStoreCleaner,
) {

    suspend operator fun invoke() {
        withContext(dispatcherProvider.io) {
            deletionPreflight.prepareExplicitReset()
            haltWalletConnect()

            clearKeystoreLinkage()

            purgeDatabases()
            purgeLocalePreferences()
            purgeFilesAndCaches()
            finishResetMarker()
        }
    }

    // The app is already wiped here: the BEAM key wrappers are shredded and the global clear ran.
    // A retained marker only means startup cleanup still has a leftover file to remove, so it must
    // not surface on the PIN screen as a failed reset.
    private suspend fun finishResetMarker() {
        try {
            deletionPreflight.finishExplicitReset()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "BEAM reset marker retained; startup cleanup will finish it")
        }
    }

    private suspend fun clearKeystoreLinkage() {
        val mainShowedOnce = localStorage.mainShowedOnce
        val appIcon = localStorage.appIcon
        val isSystemPinRequired = localStorage.isSystemPinRequired
        keyStoreCleaner.cleanExplicitReset()
        appIcon?.let(appIconService::setAppIcon)
        localStorage.mainShowedOnce = mainShowedOnce
        localStorage.isSystemPinRequired = isSystemPinRequired
    }

    private fun haltWalletConnect() {
        runCatching {
            WCDelegate.getActiveSessions().forEach { session ->
                WCDelegate.deleteSession(session.topic)
            }
            WCDelegate.deleteAllPairings()
        }.onFailure { Timber.w(it, "Failed clearing WalletConnect sessions") }
    }

    private fun purgeDatabases() {
        appDatabase.clearAllTables()

        runCatching { context.deleteDatabase(PREMIUM_DB_NAME) }
            .onFailure { Timber.w(it, "Failed deleting premium database file") }

        runCatching { context.deleteDatabase(CACHE_DB_NAME) }
            .onFailure { Timber.w(it, "Failed deleting cache database file") }

        runCatching { context.deleteDatabase(LOGGING_DB_NAME) }
            .onFailure { Timber.w(it, "Failed deleting logging database file") }
    }

    private fun purgeLocalePreferences() {
        runCatching {
            context.getSharedPreferences(LocaleHelper::class.java.name, Context.MODE_PRIVATE)
                .edit {
                    clear()
                }
        }.onFailure { Timber.w(it, "Failed clearing locale prefs") }
    }

    private suspend fun purgeFilesAndCaches() {
        runCatching {
            contactsRepository.clear()
            File(context.filesDir, CONTACTS_FILE_NAME).delete()
        }.onFailure { Timber.w(it, "Failed clearing contacts") }

        runCatching { clearWidgetState() }
            .onFailure { Timber.w(it, "Failed clearing widget state") }

        runCatching {
            context.getDir(TorConstants.DIRECTORY_TOR_DATA, Context.MODE_PRIVATE)
                .deleteRecursively()
        }.onFailure { Timber.w(it, "Failed clearing Tor data") }

        runCatching {
            File(context.filesDir, PHOTOS_DIR_NAME).deleteRecursively()
        }.onFailure { Timber.w(it, "Failed clearing login photos") }
    }

    private suspend fun clearWidgetState() {
        val glanceIds = runCatching { glanceManager.getGlanceIds(MarketWidget::class.java) }
            .getOrElse { emptyList() }

        glanceIds.forEach { glanceId ->
            runCatching {
                val file = MarketWidgetStateDefinition.getLocation(context, glanceId.toString())
                if (file.exists()) {
                    file.delete()
                }
            }.onFailure { Timber.w(it, "Failed deleting widget state for $glanceId") }
        }

        MarketWidgetWorker.cancel(context)
    }

    companion object {
        private const val CONTACTS_FILE_NAME = "UW_Contacts.json"
        private const val PREMIUM_DB_NAME = "premium_database"
        private const val CACHE_DB_NAME = "db_cache"
        private const val LOGGING_DB_NAME = "logging_database"
        private const val PHOTOS_DIR_NAME = "login_photos"
    }
}
