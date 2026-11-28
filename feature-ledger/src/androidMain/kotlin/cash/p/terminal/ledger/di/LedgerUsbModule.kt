package cash.p.terminal.ledger.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cash.p.terminal.ledger.domain.LedgerAttachments
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.usb.UsbLedgerAttachments
import cash.p.terminal.ledger.usb.UsbLedgerConnector
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/** The app binds the unqualified [LedgerConnector]; debug builds may put another one in front of USB. */
val LEDGER_USB_CONNECTOR = named("ledgerUsbConnector")

val ledgerUsbModule = module {
    single<LedgerConnector>(LEDGER_USB_CONNECTOR) { UsbLedgerConnector(get(), get()) }
    singleOf(::UsbLedgerAttachments) bind LedgerAttachments::class
    single<DataStore<Preferences>>(LEDGER_TOKEN_DESCRIPTORS) {
        PreferenceDataStoreFactory.create(
            // Only a cache of what CAL can serve again: a corrupt file must not stop the app.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { File(get<Context>().filesDir, "datastore/ledger_token_descriptors.preferences_pb") },
        )
    }
}
