package cash.p.terminal.modules.releasenotes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ReleaseNotesStorage
import cash.p.terminal.core.managers.ReleaseNotesManager
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.feature.wallet.LocalizedAssetReader
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.ui_compose.entities.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ReleaseNotesViewModel(
    private val localizedAssetReader: LocalizedAssetReader,
    private val storage: ReleaseNotesStorage,
    private val releaseNotesManager: ReleaseNotesManager,
    private val connectivityManager: IConnectivityManager,
) : ViewModel() {

    var uiState by mutableStateOf(
        ReleaseNotesUiState(
            twitterUrl = SharedAppConfig.appTwitterLink,
            telegramUrl = SharedAppConfig.appTelegramLink,
            redditUrl = SharedAppConfig.appRedditLink,
            showChangelogAfterUpdate = storage.showChangelogAfterUpdate,
        )
    )
        private set

    init {
        loadContent()

        connectivityManager.networkAvailabilityFlow
            .onEach {
                if (connectivityManager.isConnected.value && uiState.viewState is ViewState.Error) {
                    retry()
                }
            }
            .launchIn(viewModelScope)
    }

    fun retry() {
        uiState = uiState.copy(viewState = ViewState.Loading)
        loadContent()
    }

    fun whatsNewShown() {
        releaseNotesManager.updateShownAppVersion()
    }

    private fun loadContent() {
        viewModelScope.launch {
            try {
                val content = localizedAssetReader.read(CHANGELOG_PREFIX)
                uiState = uiState.copy(
                    viewState = ViewState.Success,
                    markdownContent = content
                )
            } catch (e: Exception) {
                uiState = uiState.copy(viewState = ViewState.Error(e))
            }
        }
    }

    fun setShowChangeLogAfterUpdate() {
        storage.showChangelogAfterUpdate = !storage.showChangelogAfterUpdate
        uiState = uiState.copy(showChangelogAfterUpdate = storage.showChangelogAfterUpdate)
    }

    private companion object {
        const val CHANGELOG_PREFIX = "changelog_"
    }
}

data class ReleaseNotesUiState(
    val viewState: ViewState = ViewState.Loading,
    val markdownContent: String? = null,
    val twitterUrl: String,
    val telegramUrl: String,
    val redditUrl: String,
    val showChangelogAfterUpdate: Boolean
)
