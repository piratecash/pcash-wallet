package cash.p.terminal.core

import cash.p.beam.BeamAddressType
import cash.p.terminal.resources.beam_address_type_regular
import cash.p.terminal.resources.beam_send_max_privacy
import cash.p.terminal.resources.beam_send_offline
import cash.p.terminal.resources.beam_send_public_offline
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.language_czech
import cash.p.terminal.resources.language_english
import cash.p.terminal.resources.language_french
import cash.p.terminal.resources.language_italian
import cash.p.terminal.resources.language_japanese
import cash.p.terminal.resources.language_korean
import cash.p.terminal.resources.language_portuguese
import cash.p.terminal.resources.language_simplified_chinese
import cash.p.terminal.resources.language_spanish
import cash.p.terminal.resources.language_traditional_chinese
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.backuplocal.fullbackup.BackupFileValidator
import io.horizontalsystems.hdwalletkit.Language
import org.jetbrains.compose.resources.StringResource
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

fun BeamAddressType.titleResId(): StringResource = when (this) {
    BeamAddressType.Offline -> Res.string.beam_send_offline
    BeamAddressType.PublicOffline -> Res.string.beam_send_public_offline
    BeamAddressType.MaxPrivacy -> Res.string.beam_send_max_privacy
    BeamAddressType.Regular -> Res.string.beam_address_type_regular
}

fun String.orHide(hidden: Boolean, hideValue: String = "*****"): String =
    if (hidden) hideValue else this

fun String.hexToByteArray(): ByteArray {
    return ByteArray(this.length / 2) {
        this.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

fun ByteArray.toRawHexString(): String {
    return this.joinToString(separator = "") {
        it.toInt().and(0xff).toString(16).padStart(2, '0')
    }
}

private val NON_BASE64_CHARS = Regex("[^A-Za-z0-9+/=]")

/** Decodes like android.util.Base64 did: every non-alphabet char is skipped, even between padding chars. */
fun String.decodeBase64Lenient(): ByteArray = Base64.getDecoder().decode(replace(NON_BASE64_CHARS, ""))

/** Same output as android.util.Base64 with NO_WRAP. */
fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)

private val NON_BASE64_URL_CHARS = Regex("[^A-Za-z0-9_=-]")

/** URL-safe twin of [decodeBase64Lenient]: decodes like android.util.Base64 with URL_SAFE did. */
fun String.decodeBase64UrlLenient(): ByteArray = Base64.getUrlDecoder().decode(replace(NON_BASE64_URL_CHARS, ""))

/** Same output as android.util.Base64 with URL_SAFE, NO_PADDING and NO_WRAP. */
fun ByteArray.encodeBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

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

val Language.displayNameStringRes: StringResource
    get() = when (this) {
        Language.English -> Res.string.language_english
        Language.Japanese -> Res.string.language_japanese
        Language.Korean -> Res.string.language_korean
        Language.Spanish -> Res.string.language_spanish
        Language.SimplifiedChinese -> Res.string.language_simplified_chinese
        Language.TraditionalChinese -> Res.string.language_traditional_chinese
        Language.French -> Res.string.language_french
        Language.Italian -> Res.string.language_italian
        Language.Czech -> Res.string.language_czech
        Language.Portuguese -> Res.string.language_portuguese
    }

/** On Android the default temp directory is the app cache directory; a desktop one may be shared. */
fun writeBackupToTempFile(data: ByteArray): String {
    val path = if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
        Files.createTempFile("backup_", ".tmp", PosixFilePermissions.asFileAttribute(OWNER_ONLY))
    } else {
        Files.createTempFile("backup_", ".tmp")
    }
    Files.write(path, data)
    return path.toAbsolutePath().toString()
}

private val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")

fun validateAndSaveBackup(bytes: ByteArray): String {
    val validator = BackupFileValidator()
    if (BackupLocalModule.BackupV4Binary.isBinaryFormat(bytes)) {
        validator.validateBinary(bytes)
    } else {
        validator.validate(String(bytes, Charsets.UTF_8))
    }
    return writeBackupToTempFile(bytes)
}
