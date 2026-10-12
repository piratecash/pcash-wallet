package cash.p.terminal.modules.coin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.resources.Hud_Added_To_Watchlist
import cash.p.terminal.resources.Hud_Removed_from_Watchlist
import cash.p.terminal.resources.Res
import cash.p.terminal.wallet.SubscriptionManager
import io.horizontalsystems.core.MarketSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import org.jetbrains.compose.resources.StringResource

class CoinViewModel(
    private val service: CoinService,
    private val clearables: List<Clearable>,
    settings: MarketSettingsStore,
    private val subscriptionManager: SubscriptionManager
) : ViewModel() {

    val tabs: Array<CoinModule.Tab> = CoinModule.Tab.entries
        .filter { tab ->
            when (tab) {
                CoinModule.Tab.Details -> service.fullCoin.isAnalyticsEnabled()
                else -> true
            }
        }
        .toTypedArray()
    val fullCoin by service::fullCoin

    val isWatchlistEnabled = settings.marketsTabEnabled
    var isFavorite by mutableStateOf<Boolean>(false)
        private set
    var successMessage by mutableStateOf<StringResource?>(null)
        private set

    private var subscriptionInfoShown: Boolean = false

    init {
        viewModelScope.launch {
            val isFavoriteFlow: Flow<Boolean> = service.isFavorite.asFlow()
            isFavoriteFlow.collect {
                isFavorite = it
            }
        }
    }

    override fun onCleared() {
        clearables.forEach(Clearable::clear)
    }

    fun onFavoriteClick() {
        service.favorite()
        successMessage = Res.string.Hud_Added_To_Watchlist
    }

    fun onUnfavoriteClick() {
        service.unfavorite()
        successMessage = Res.string.Hud_Removed_from_Watchlist
    }

    fun onSuccessMessageShown() {
        successMessage = null
    }

    fun shouldShowSubscriptionInfo():Boolean {
        return !subscriptionManager.hasSubscription() && !subscriptionInfoShown
    }

    fun subscriptionInfoShown() {
        subscriptionInfoShown = true
    }

}
