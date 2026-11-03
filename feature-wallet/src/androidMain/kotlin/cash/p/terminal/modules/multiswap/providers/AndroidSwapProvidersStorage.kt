package cash.p.terminal.modules.multiswap.providers

import android.content.SharedPreferences
import androidx.core.content.edit

class AndroidSwapProvidersStorage(private val preferences: SharedPreferences) : SwapProvidersStorage {
    override var disabledProviderIds: Set<String>
        get() = preferences.getStringSet(KEY_DISABLED_SWAP_PROVIDERS, emptySet()).orEmpty()
        set(value) = preferences.edit { putStringSet(KEY_DISABLED_SWAP_PROVIDERS, value) }

    override var backendProviders: String?
        get() = preferences.getString(KEY_BACKEND_SWAP_PROVIDERS, null)
        set(value) = preferences.edit { putString(KEY_BACKEND_SWAP_PROVIDERS, value) }

    private companion object {
        const val KEY_DISABLED_SWAP_PROVIDERS = "disabled_swap_providers"
        const val KEY_BACKEND_SWAP_PROVIDERS = "backend_swap_providers"
    }
}
