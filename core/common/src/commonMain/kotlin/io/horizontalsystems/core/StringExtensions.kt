package io.horizontalsystems.core

import java.math.BigDecimal

fun String.toBigDecimalOrNullExt(): BigDecimal? =
    toBigDecimalOrNull() ?: replace(',', '.').toBigDecimalOrNull()
