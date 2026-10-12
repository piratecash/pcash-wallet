package cash.p.terminal.modules.market.posts

import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.Market_DaysAgo
import cash.p.terminal.resources.Market_HoursAgo
import cash.p.terminal.resources.Market_MinutesAgo
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.entities.ViewState
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.helpers.DateHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class MarketPostsViewModel(
    private val service: MarketPostService
) : ViewModelUiState<MarketPostsModule.UiState>() {

    private var items: List<MarketPostsModule.PostViewItem> = emptyList()
    private var viewState: ViewState = ViewState.Loading
    private var isRefreshing = false

    private fun getTimeAgo(timestamp: Long): String {
        val secondsAgo = DateHelper.getSecondsAgo(timestamp * 1000)
        val minutesAgo = secondsAgo / 60
        val hoursAgo = minutesAgo / 60

        return when {
            // interval from post in minutes
            minutesAgo < 60 -> Translator.getString(Res.string.Market_MinutesAgo, minutesAgo)
            hoursAgo < 24 -> Translator.getString(Res.string.Market_HoursAgo, hoursAgo)
            else -> {
                val daysAgo = hoursAgo / 24
                Translator.getString(Res.string.Market_DaysAgo, daysAgo)
            }
        }
    }

    init {
        viewModelScope.launch {
            service.stateObservable.asFlow().collect { state ->
                isRefreshing = false

                state.dataOrNull?.let { posts ->
                    items = posts.map {
                        MarketPostsModule.PostViewItem(
                            source = it.source.replaceFirstChar(Char::titlecase),
                            title = it.title,
                            body = it.body,
                            timeAgo = getTimeAgo(it.timestamp),
                            url = it.url
                        )
                    }
                }

                state.viewState?.let {
                    viewState = it
                }
                emitState()
            }
        }

        service.start()
    }

    override fun createState() = MarketPostsModule.UiState(
        items = items,
        viewState = viewState,
        isRefreshing = isRefreshing
    )

    override fun onCleared() {
        service.stop()
    }

    fun onErrorClick() {
        service.refresh()
    }

    fun refresh() {
        isRefreshing = true
        emitState()
        service.refresh()
    }
}
