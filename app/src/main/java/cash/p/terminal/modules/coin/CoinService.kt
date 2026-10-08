package cash.p.terminal.modules.coin

import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CoinService(
    val fullCoin: FullCoin,
    private val marketFavoritesManager: MarketFavoritesManager,
) : Clearable {

    private val _isFavorite = BehaviorSubject.create<Boolean>()
    val isFavorite: Observable<Boolean>
        get() = _isFavorite

    private val disposables = CompositeDisposable()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        coroutineScope.launch {
            marketFavoritesManager.favoriteCoinUids
                .map { fullCoin.coin.uid in it }
                .distinctUntilChanged()
                .collect(_isFavorite::onNext)
        }
    }

    override fun clear() {
        disposables.clear()
        coroutineScope.cancel()
    }

    fun favorite() {
        coroutineScope.launch { marketFavoritesManager.add(fullCoin.coin.uid) }
    }

    fun unfavorite() {
        coroutineScope.launch { marketFavoritesManager.remove(fullCoin.coin.uid) }
    }
}
