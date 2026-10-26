package io.horizontalsystems.core

fun Double.smartFormat(): String {
    val hasDecimalPart = this % 1 != 0.0
    return if (hasDecimalPart) {
        String.format("%.1f", this)
    } else {
        String.format("%.0f", this)
    }
}
