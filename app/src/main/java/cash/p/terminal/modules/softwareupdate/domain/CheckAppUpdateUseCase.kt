package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.core.managers.Version
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

class CheckAppUpdateUseCase(
    private val repository: AppUpdateRepository,
    private val systemInfoManager: ISystemInfoManager,
    private val localStorage: ILocalStorage,
    private val languageManager: LanguageManager,
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): UpdateStatus = withContext(dispatcherProvider.io) {
        try {
            val latest = repository.getLatestRelease()
            localStorage.latestKnownVersion = latest.version
            val versionComparison = Version(latest.version).compareTo(Version(systemInfoManager.appVersion))
            when {
                versionComparison > 0 -> {
                    val markdown = try {
                        repository.getChangelogMarkdown(
                            minor = latest.minor,
                            isActiveBranch = true,
                            tagName = latest.tagName,
                            language = languageManager.currentLanguage,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    UpdateStatus.Available(latest, ChangelogSnippetParser.parseLatest(markdown))
                }

                versionComparison == 0 -> UpdateStatus.UpToDate(latest)
                else -> UpdateStatus.UpToDate(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Update check failed")
            UpdateStatus.Error
        } finally {
            localStorage.lastUpdateCheckTimestamp = timeProvider.now()
        }
    }
}
