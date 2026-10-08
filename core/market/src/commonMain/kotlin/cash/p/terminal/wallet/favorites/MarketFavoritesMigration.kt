package cash.p.terminal.wallet.favorites

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

/**
 * Platform-owned seeding of the favorites DataStore from a legacy store. Implementations are
 * optional: the platform module passes the one it finds, if any.
 */
interface MarketFavoritesMigration : DataMigration<Preferences>
