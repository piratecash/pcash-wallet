package cash.p.terminal.wallet.hardware

/** Converts BIP-32 paths between `"m/84'/0'/0'"` and the int form devices take (hardened bit `0x80000000`). */
object HardwareDerivationPath {

    private const val HARDENED_BIT = 0x80000000.toInt()

    /** `"m/84'/0'/0'"` -> `[0x80000054, 0x80000000, 0x80000000]`. */
    fun parse(path: String): List<Int> =
        path.split("/")
            .filter { it.isNotEmpty() && it != "m" }
            .map { segment ->
                val hardened = segment.endsWith("'")
                val value = segment.trimEnd('\'').toInt()
                if (hardened) value or HARDENED_BIT else value
            }

    /** `"m/84'/0'/0'"` and change address 5 -> `[.., 1, 5]`. */
    fun addressPath(accountPath: String, position: HardwareAddressPosition): List<Int> =
        parse(accountPath) + (if (position.change) 1 else 0) + position.index

    /** `[0x80000054, 0x80000000, 0x80000000]` -> `"m/84'/0'/0'"`. */
    fun format(path: List<Int>): String =
        buildString {
            append("m")
            for (segment in path) {
                append('/')
                if (segment and HARDENED_BIT != 0) {
                    append(segment and HARDENED_BIT.inv())
                    append('\'')
                } else {
                    append(segment)
                }
            }
        }
}
