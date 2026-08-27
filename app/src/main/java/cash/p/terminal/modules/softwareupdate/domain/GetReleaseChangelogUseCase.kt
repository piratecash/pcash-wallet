package cash.p.terminal.modules.softwareupdate.domain

import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.network.github.domain.repository.AppUpdateRepository
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

data class ChangelogRequest(
    val minor: String,
    val isActiveBranch: Boolean,
    val tagName: String?,
) {
    companion object {
        fun active(minor: String, tagName: String?) = ChangelogRequest(minor, true, tagName)

        fun archived(minor: String) = ChangelogRequest(minor, false, null)
    }
}

class GetReleaseChangelogUseCase(
    private val repository: AppUpdateRepository,
    private val languageManager: LanguageManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(request: ChangelogRequest): String? =
        withContext(dispatcherProvider.io) {
            repository.getChangelogMarkdown(
                minor = request.minor,
                isActiveBranch = request.isActiveBranch,
                tagName = request.tagName,
                language = languageManager.currentLanguage,
            )
        }
}
