package io.horizontalsystems.core

import java.io.File

fun File?.sizeInMb(): String {
    if (this == null || !this.exists()) {
        return "n/a"
    }

    val sizeInMb = this.length().toDouble() / (1024 * 1024)
    val roundedSize = String.format("%.2f", sizeInMb)

    return "$roundedSize MB"
}
