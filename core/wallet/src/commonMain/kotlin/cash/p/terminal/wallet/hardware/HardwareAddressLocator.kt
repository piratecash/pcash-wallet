package cash.p.terminal.wallet.hardware

/** Where an address sits under its account key: `<account path>/<change>/<index>`. */
data class HardwareAddressPosition(val change: Boolean, val index: Int)

/** A receive adapter that can place its own addresses, so a device can show one for verification. */
interface HardwareAddressLocator {
    /** Null when [address] is not one of this wallet's addresses. */
    suspend fun locate(address: String): HardwareAddressPosition?
}
