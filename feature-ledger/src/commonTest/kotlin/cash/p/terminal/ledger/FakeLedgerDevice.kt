package cash.p.terminal.ledger

import cash.p.terminal.ledger.domain.LedgerAppKeyFetcher
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor

/** Speaks the dashboard protocol; every app switch needs a fresh transport, as after USB re-enumeration. */
class FakeLedgerDevice(
    private val installedApps: Map<String, String>,
    private val rejectedApps: Set<String> = emptySet(),
    private val rejectedReopens: Set<String> = emptySet(),
    private val openedInstead: String? = null,
    private val keyPrefix: String = "",
) {
    var currentApp = DASHBOARD
        private set

    private val openedApps = mutableSetOf<String>()
    var unplugged = false
    var opens = 0
        private set

    val provider = LedgerTransportProvider { Transport().also { opens++ } }

    private inner class Transport : LedgerTransport {
        override suspend fun exchange(apdu: ByteArray): ByteArray {
            if (unplugged) throw LedgerException.TransportClosed()
            return when ((apdu[0].toInt() and 0xFF) to (apdu[1].toInt() and 0xFF)) {
                0xB0 to 0x01 -> byteArrayOf(1) + field(currentApp) + field(installedApps[currentApp] ?: "1.0.0") + OK
                0xB0 to 0xA7 -> OK.also { currentApp = DASHBOARD }
                0xE0 to 0xD8 -> openApp(apdu.decodeToString(5, apdu.size))
                0xE0 to 0x02 -> keyPrefix.encodeToByteArray() + OK
                else -> OK
            }
        }

        override fun close() = Unit
    }

    private fun openApp(name: String): ByteArray = when {
        name in rejectedApps -> status(StatusWord.USER_REJECTED)
        name in rejectedReopens && !openedApps.add(name) -> status(StatusWord.USER_REJECTED)
        name !in installedApps -> status(StatusWord.APP_NOT_INSTALLED)
        else -> OK.also { currentApp = openedInstead ?: name }
    }

    private fun field(value: String) = byteArrayOf(value.length.toByte()) + value.encodeToByteArray()

    private companion object {
        const val DASHBOARD = "BOLOS"
        val OK = status(StatusWord.OK)

        fun status(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
    }
}

/** Returns the configured key, prefixed by the answering device, and records which device app was open. */
class FakeKeyFetcher(
    override val appName: String,
    private val device: FakeLedgerDevice,
    private val keys: Map<TokenQuery, String>,
) : LedgerAppKeyFetcher {
    val openAppOnFetch = mutableListOf<String>()

    override fun supports(query: TokenQuery): Boolean = query in keys

    override suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey {
        val response = transport.exchange(byteArrayOf(0xE0.toByte(), 0x02, 0, 0, 0))
        openAppOnFetch += device.currentApp
        val spec = checkNotNull(HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger))
        val devicePrefix = response.decodeToString(0, response.size - 2)
        return LedgerKey(spec, devicePrefix + keys.getValue(query), byteArrayOf(1))
    }
}
