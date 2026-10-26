package cash.p.terminal.core.managers

object RawTransactionHex {
    // Covers Base64 of a 1 MiB zlib stream (including overhead), and raw hex of 1 MiB bytes.
    const val MAX_INPUT_CHARACTERS = 2 * 1024 * 1024
    private const val MIN_LENGTH = 20

    fun isValid(text: String): Boolean =
        text.length <= MAX_INPUT_CHARACTERS && text.trim().let { value ->
            value.length >= MIN_LENGTH && isHex(value)
        }

    fun isHex(value: String): Boolean =
        value.isNotEmpty() && value.length % 2 == 0 &&
                value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
