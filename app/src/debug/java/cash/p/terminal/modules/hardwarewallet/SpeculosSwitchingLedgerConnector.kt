package cash.p.terminal.modules.hardwarewallet

import android.content.Context
import androidx.core.content.edit
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.SpeculosTransport
import kotlinx.coroutines.flow.StateFlow

class SpeculosSwitchingLedgerConnector(
    private val usb: LedgerConnector,
    private val settings: LedgerSpeculosSettings,
) : LedgerConnector {
    override val awaitingPermission: StateFlow<Boolean> get() = usb.awaitingPermission

    override val isDevicePresent: Boolean get() = settings.enabled || usb.isDevicePresent

    override val model: LedgerModel get() = if (settings.enabled) LedgerModel.Unknown else usb.model

    override suspend fun open(): LedgerTransport =
        if (settings.enabled) {
            SpeculosTransport.connect(settings.host, settings.port)
        } else {
            usb.open()
        }
}

class LedgerSpeculosSettings(context: Context) {
    private val preferences = context.getSharedPreferences("ledger_speculos", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    /** `host:port`; an Android emulator reaches the host machine at 10.0.2.2. */
    var address: String
        get() = preferences.getString(KEY_ADDRESS, null) ?: DEFAULT_ADDRESS
        set(value) = preferences.edit { putString(KEY_ADDRESS, value) }

    val host: String get() = address.substringBeforeLast(':')

    val port: Int get() = address.substringAfterLast(':').toIntOrNull() ?: DEFAULT_PORT

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ADDRESS = "address"
        const val DEFAULT_PORT = 9999
        const val DEFAULT_ADDRESS = "10.0.2.2:$DEFAULT_PORT"
    }
}
