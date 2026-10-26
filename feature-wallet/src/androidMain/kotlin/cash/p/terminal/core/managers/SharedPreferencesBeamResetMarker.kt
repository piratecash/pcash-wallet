package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.wallet.AccountDeletionBlockedException

class SharedPreferencesBeamResetMarker(context: Context) : BeamResetMarker {
    // Outside the wallet directory and independent of encrypted account decoding.
    private val preferences = context.getSharedPreferences("beam_deletion", Context.MODE_PRIVATE)

    override val isSet: Boolean get() = preferences.contains(KEY)

    override fun set() {
        if (!preferences.edit().putBoolean(KEY, true).commit()) {
            throw AccountDeletionBlockedException()
        }
    }

    override fun clear() {
        if (!preferences.edit().remove(KEY).commit()) {
            // SharedPreferences can publish a failed commit in memory.
            preferences.edit().putBoolean(KEY, true).commit()
            throw AccountDeletionBlockedException()
        }
    }

    private companion object {
        const val KEY = "reset_pending"
    }
}
