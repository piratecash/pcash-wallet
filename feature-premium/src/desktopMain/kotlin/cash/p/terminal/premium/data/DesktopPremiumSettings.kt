package cash.p.terminal.premium.data

import cash.p.terminal.premium.domain.PremiumSettings
import cash.p.terminal.wallet.managers.UserManager

/** Same keys and defaults as the Android preferences, per user level. */
internal class DesktopPremiumSettings(
    private val levelSettings: LevelSettings,
    private val userManager: UserManager,
) : PremiumSettings {

    override fun getAmlCheckReceivedEnabled(): Boolean = flag(AML_ENABLED) ?: false

    override fun setAmlCheckReceivedEnabled(enabled: Boolean) = setFlag(AML_ENABLED, enabled)

    override fun getAmlCheckShowAlert(): Boolean = flag(AML_SHOW_ALERT) ?: true

    override fun setAmlCheckShowAlert(show: Boolean) = setFlag(AML_SHOW_ALERT, show)

    private fun flag(key: String) = levelSettings.flag(userManager.getUserLevel(), key)

    private fun setFlag(key: String, value: Boolean) = levelSettings.setFlag(userManager.getUserLevel(), key, value)

    private companion object {
        const val AML_ENABLED = "aml_check_received_enabled"
        const val AML_SHOW_ALERT = "aml_check_received_show_alert"
    }
}
