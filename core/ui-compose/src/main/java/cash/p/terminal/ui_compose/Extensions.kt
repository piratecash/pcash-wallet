package cash.p.terminal.ui_compose

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

fun TextFieldValue.withLeadingZeroIfDecimal(): TextFieldValue {
    val text = this.text
    return if (text.startsWith(",") || text.startsWith(".")) {
        copy(
            text = "0$text",
            selection = TextRange(selection.start + 1, selection.end + 1)
        )
    } else {
        this
    }
}
