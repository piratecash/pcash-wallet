package cash.p.terminal.core.managers

import io.horizontalsystems.core.entities.Currency

object Currencies {
    val all: List<Currency> = listOf(
        Currency("AUD", "A$", 2),
        Currency("ARS", "$", 2),
        Currency("BRL", "R$", 2),
        Currency("CAD", "C$", 2),
        Currency("CHF", "₣", 2),
        Currency("CNY", "¥", 2),
        Currency("EUR", "€", 2),
        Currency("GBP", "£", 2),
        Currency("HKD", "HK$", 2),
        Currency("HUF", "Ft", 2),
        Currency("ILS", "₪", 2),
        Currency("INR", "₹", 2),
        Currency("JPY", "¥", 2),
        Currency("NOK", "kr", 2),
        Currency("PHP", "₱", 2),
        Currency("RUB", "₽", 2),
        Currency("SGD", "S$", 2),
        Currency("USD", "$", 2),
        Currency("ZAR", "R", 2),
    )
}
