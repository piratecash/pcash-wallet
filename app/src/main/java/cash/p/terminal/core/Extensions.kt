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
import cash.p.beam.BeamAddressType
import cash.p.terminal.R
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.backuplocal.fullbackup.BackupFileValidator
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.modules.main.MainModule
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.ethereumkit.core.toRawHexString
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hodler.LockTimeInterval
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.math.BigDecimal
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Locale
import java.util.Optional

fun Throwable.isNoInternetException(): Boolean {
    return generateSequence(this) { it.cause }
        .any { throwable ->
            throwable is UnknownHostException ||
                throwable is NoRouteToHostException ||
                throwable is UnresolvedAddressException ||
                throwable.message.equals("No internet connection", ignoreCase = true)
        }
}

fun String.orHide(hidden: Boolean, hideValue: String = "*****"): String =
    if (hidden) hideValue else this

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

val Language.displayNameStringRes: Int
    get() = when (this) {
        Language.English -> R.string.language_english
        Language.Japanese -> R.string.language_japanese
        Language.Korean -> R.string.language_korean
        Language.Spanish -> R.string.language_spanish
        Language.SimplifiedChinese -> R.string.language_simplified_chinese
        Language.TraditionalChinese -> R.string.language_traditional_chinese
        Language.French -> R.string.language_french
        Language.Italian -> R.string.language_italian
        Language.Czech -> R.string.language_czech
        Language.Portuguese -> R.string.language_portuguese
    }

// String

fun String.hexToByteArray(): ByteArray {
    return ByteArray(this.length / 2) {
        this.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

// ByteArray

fun ByteArray.toRawHexString(): String {
    return this.joinToString(separator = "") {
        it.toInt().and(0xff).toString(16).padStart(2, '0')
    }
}

fun ByteArray?.to0xHexString(): String {
    val rawHex = this?.toRawHexString() ?: return ""
    return "0x$rawHex"
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

fun BeamAddressType.titleResId(): Int = when (this) {
    BeamAddressType.Offline -> R.string.beam_send_offline
    BeamAddressType.PublicOffline -> R.string.beam_send_public_offline
    BeamAddressType.MaxPrivacy -> R.string.beam_send_max_privacy
    BeamAddressType.Regular -> R.string.beam_address_type_regular
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

inline fun <reified T : Enum<T>> Enum.Companion.valueOrDefault(index: Int, default: T): T {
    return enumValues<T>().getOrNull(index) ?: default
}

fun String.splitToAddresses(): List<String> {
    return split(",")
        .map { it.split("\n") }.flatten()
        .map { it.replace(Regex(":\\d+"), "") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/**
 * Normalizes a private key byte array to exactly [size] bytes.
 * BigInteger.toByteArray() may return fewer bytes (leading zeros stripped)
 * or more bytes (sign byte prepended). This ensures a fixed-size output.
 */
fun ByteArray.toFixedSize(size: Int): ByteArray = when {
    this.size > size -> copyOfRange(this.size - size, this.size)
    this.size < size -> ByteArray(size - this.size) + this
    else -> this
}

fun writeBackupToTempFile(data: ByteArray): String {
    val file = File.createTempFile("backup_", ".tmp", App.instance.cacheDir)
    file.writeBytes(data)
    return file.absolutePath
}

fun validateAndSaveBackup(bytes: ByteArray): String {
    val validator = BackupFileValidator()
    if (BackupLocalModule.BackupV4Binary.isBinaryFormat(bytes)) {
        validator.validateBinary(bytes)
    } else {
        validator.validate(String(bytes, Charsets.UTF_8))
    }
    return writeBackupToTempFile(bytes)
}

fun Context.hasNFC(): Boolean {
    val pm = getSystemService(Context.NFC_SERVICE) as android.nfc.NfcManager
    return pm.defaultAdapter?.isEnabled == true
}

fun HSNavigation.premiumAction(block: () -> Unit) {
    val checkPremiumUseCase: CheckPremiumUseCase by inject(CheckPremiumUseCase::class.java)
    if (checkPremiumUseCase.getPremiumType().isPremium()) {
        block.invoke()
    } else {
        slideFromBottomForResult<AboutPremiumPage.Result>(
            AboutPremiumPage(AboutPremiumPage.CloseOnPremiumInput())
        ) {
            block.invoke()
        }
    }
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
