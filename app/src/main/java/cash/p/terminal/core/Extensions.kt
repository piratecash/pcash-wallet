package cash.p.terminal.core

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.DocumentsContract
import androidx.compose.ui.platform.Clipboard
import cash.p.terminal.R
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.hodler.LockTimeInterval
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.util.Locale
import java.util.Optional

val <T> Optional<T>.orNull: T?
    get() = when {
        isPresent -> get()
        else -> null
    }

val String.coinIconUrl: String
    get() = "https://cdn.blocksdecoded.com/coin-icons/32px/$this@3x.png"

fun List<FullCoin>.sortedByFilter(filter: String): List<FullCoin> {
    val baseComparator = compareBy<FullCoin> {
        it.coin.priority ?: Int.MAX_VALUE
    }.thenBy {
        it.coin.marketCapRank ?: Int.MAX_VALUE
    }.thenBy {
        it.coin.name.lowercase(Locale.ENGLISH)
    }
    val comparator = if (filter.isNotBlank()) {
        val lowercasedFilter = filter.lowercase()
        compareByDescending<FullCoin> {
            it.coin.code.lowercase() == lowercasedFilter
        }.thenByDescending {
            it.coin.code.lowercase().startsWith(lowercasedFilter)
        }.thenByDescending {
            it.coin.name.lowercase().startsWith(lowercasedFilter)
        }.thenComparing(baseComparator)
    } else {
        baseComparator
    }

    return sortedWith(comparator)
}

// Intent & Parcelable Enum
fun Intent.putParcelableExtra(key: String, value: Parcelable) {
    putExtra(key, value)
}

fun LockTimeInterval?.stringResId(): Int {
    return when (this) {
        LockTimeInterval.hour -> R.string.Send_LockTime_Hour
        LockTimeInterval.month -> R.string.Send_LockTime_Month
        LockTimeInterval.halfYear -> R.string.Send_LockTime_HalfYear
        LockTimeInterval.year -> R.string.Send_LockTime_Year
        null -> R.string.Send_LockTime_Off
    }
}

suspend fun <T> retryWhen(
    times: Int,
    predicate: suspend (cause: Throwable) -> Boolean,
    block: suspend () -> T
): T {
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Throwable) {
            if (!predicate(e)) {
                throw e
            }
        }
        delay(1000)
    }
    return block()
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Safely opens an InputStream from a content URI, handling virtual documents
 * (e.g., files from Google Drive that don't exist locally).
 *
 * Virtual documents cannot be opened with openInputStream() directly and require
 * openTypedAssetFileDescriptor() to stream the content.
 */
fun ContentResolver.openInputStreamSafe(uri: Uri): java.io.InputStream? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isVirtualDocument(uri)) {
        openTypedAssetFileDescriptor(uri, "*/*", null)
            ?.createInputStream()
    } else {
        openInputStream(uri)
    }
}

private fun ContentResolver.isVirtualDocument(uri: Uri): Boolean {
    if (!DocumentsContract.isDocumentUri(App.instance, uri)) return false

    return try {
        val cursor = query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val flags = it.getInt(0)
                flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0
            } else false
        } ?: false
    } catch (_: Exception) {
        false
    }
}

fun String?.extractBigDecimal(): BigDecimal? =
    if (this == null) {
        null
    } else {
        "\\d+\\.\\d+".toRegex().find(this)?.value?.toBigDecimal()
    }

fun Context.hasNFC(): Boolean {
    val pm = getSystemService(Context.NFC_SERVICE) as android.nfc.NfcManager
    return pm.defaultAdapter?.isEnabled == true
}


suspend fun Clipboard.getText(): String? {
    val clip = this.getClipEntry()?.clipData
    return if (clip != null && clip.itemCount > 0) {
        val item = clip.getItemAt(0)
        item.text.toString().takeIf { it.isNotBlank() }
    } else {
        null
    }
}

/**
 * Restarts the app by launching MainActivity with clear flags.
 * Use this as a fallback when navigation state is corrupted after process death.
 */
fun Activity.restartMain() {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
}

fun Activity.fullRestart() {
    MainModule.startAsNewTask(this)
    exitProcess(0)
}
