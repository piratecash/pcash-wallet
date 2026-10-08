package io.horizontalsystems.core.entities

import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.common.CommonIgnoredOnParcel
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal

@CommonParcelize
data class CurrencyValue(val currency: Currency, val value: BigDecimal) : CommonParcelable {
    @CommonIgnoredOnParcel
    private val numberFormatter: IAppNumberFormatter by inject(IAppNumberFormatter::class.java)
    fun getFormattedFull(): String {
        return numberFormatter.formatFiatFull(value, currency.symbol)
    }

    fun getFormattedShort(): String {
        return numberFormatter.formatFiatShort(value, currency.symbol, 2)
    }
}
