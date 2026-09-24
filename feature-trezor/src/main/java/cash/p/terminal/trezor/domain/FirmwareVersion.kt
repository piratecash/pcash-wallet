package cash.p.terminal.trezor.domain

/** Structured `major.minor.patch` firmware version, comparable so admission policies can gate by a minimum. */
internal data class FirmwareVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<FirmwareVersion> {
    override fun compareTo(other: FirmwareVersion): Int =
        compareValuesBy(this, other, FirmwareVersion::major, FirmwareVersion::minor, FirmwareVersion::patch)

    companion object {
        fun parse(value: String): FirmwareVersion? {
            val parts = value.split(".")
            if (parts.size != 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            return FirmwareVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
