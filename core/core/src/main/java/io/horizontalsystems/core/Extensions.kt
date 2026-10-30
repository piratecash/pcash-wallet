package io.horizontalsystems.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.IntentCompat
import cash.p.terminal.ui_compose.parcelable
import java.util.Locale

fun View.hideKeyboard(context: Context) {
    val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

//  String

fun String.hexToByteArray(): ByteArray {
    return ByteArray(this.length / 2) {
        this.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

//  ByteArray

@Deprecated("Use toRawHexString() instead", ReplaceWith("toRawHexString()"))
fun ByteArray.toHexString() = this.toRawHexString()

fun ByteArray.toRawHexString(): String {
    return this.joinToString(separator = "") {
        it.toInt().and(0xff).toString(16).padStart(2, '0')
    }
}

fun ByteArray.toHexStringWithLeadingZero(): String {
    val rawHex = this.toRawHexString()
    return "0x$rawHex"
}

fun ByteArray.toHexReversed(): String {
    val sb = StringBuilder(size * 2)
    var i = size - 1
    while (i >= 0) {
        sb.append(String.format(Locale.ROOT, "%02x", this[i--]))
    }
    return sb.toString()
}

//  Intent & Parcelable Enum

fun Intent.putParcelableExtra(key: String, value: Parcelable) {
    putExtra(key, value)
}

inline fun <reified T : Parcelable> Bundle.getInputX(): T? {
    return parcelable("input")
}

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)
