package cash.p.terminal.modules.paycore

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AndroidPayCorePreferences(context: Context) : PayCorePreferences {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("paycore_secure_prefs", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putStringDurably(key: String, value: String) = prefs.edit(commit = true) { putString(key, value) }
}
