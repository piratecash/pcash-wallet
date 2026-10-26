package cash.p.terminal.ui_compose

import android.os.Bundle
import android.os.Parcelable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.os.BundleCompat

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? =
    BundleCompat.getParcelable(this, key, T::class.java)

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
