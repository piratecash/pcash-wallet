package cash.p.terminal.core.managers

import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DefaultCurrencyManager(
    private val storage: BaseCurrencyStorage
) : CurrencyManager {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _baseCurrencyUpdatedFlow: MutableSharedFlow<Unit> = MutableSharedFlow()
    override val baseCurrencyUpdatedFlow: SharedFlow<Unit> = _baseCurrencyUpdatedFlow.asSharedFlow()

    override var baseCurrency = getInitialCurrency()
        set(value) {
            field = value

            storage.baseCurrencyCode = value.code
            baseCurrencyUpdatedSignal.onNext(Unit)
            scope.launch {
                _baseCurrencyUpdatedFlow.emit(Unit)
            }
        }

    private val defaultCurrency: Currency
        get() = Currencies.all.first { it.code == "USD" }

    private fun getInitialCurrency(): Currency {
        return storage.baseCurrencyCode?.let { code ->
            Currencies.all.find { it.code == code }
        } ?: defaultCurrency
    }

    override val currencies: List<Currency> = Currencies.all

    override val baseCurrencyUpdatedSignal = PublishSubject.create<Unit>()

    override fun setBaseCurrencyCode(baseCurrencyCode: String) {
        val newCurrency = Currencies.all.find { it.code == baseCurrencyCode }
        baseCurrency = newCurrency ?: defaultCurrency
    }
}
