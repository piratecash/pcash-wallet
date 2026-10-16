package cash.p.terminal.premium.data

import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DesktopSettingsStore

/** Same keys and defaults as the Android preferences, per user level. */
internal class DesktopPremiumSettings(
    private val settingsStore: DesktopSettingsStore,
    private val userManager: UserManager,
) : PremiumSettings {

    override fun getAmlCheckReceivedEnabled(): Boolean = settingsStore.flag(levelKey(AML_ENABLED)) ?: false

    override fun setAmlCheckReceivedEnabled(enabled: Boolean) = settingsStore.setFlag(levelKey(AML_ENABLED), enabled)

    override fun getAmlCheckShowAlert(): Boolean = settingsStore.flag(levelKey(AML_SHOW_ALERT)) ?: true

    override fun setAmlCheckShowAlert(show: Boolean) = settingsStore.setFlag(levelKey(AML_SHOW_ALERT), show)

    private fun levelKey(key: String) = "${key}_${userManager.getUserLevel()}"

    private companion object {
        const val AML_ENABLED = "aml_check_received_enabled"
        const val AML_SHOW_ALERT = "aml_check_received_show_alert"
    }
}
