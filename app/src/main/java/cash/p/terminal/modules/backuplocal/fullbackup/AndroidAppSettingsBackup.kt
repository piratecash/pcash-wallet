package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.core.managers.LanguageManager
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.settings.appearance.AppIcon
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppSettingsBackup(
    private val localStorage: ILocalStorage,
    private val languageManager: LanguageManager,
    private val appIconService: AppIconService,
    private val chartIndicatorsBackup: ChartIndicatorsBackup,
    private val baseTokenManager: BaseTokenManager,
    private val currencyManager: CurrencyManager,
    private val contactsRepository: ContactsRepository,
    private val appearanceSettingsBackup: AppearanceSettingsBackup,
    private val blockchainSettingsBackup: BlockchainSettingsBackup,
) : AppSettingsBackup {

    private val logger = Logger.withTag("AndroidAppSettingsBackup")

    override val contacts: List<Contact>
        get() = contactsRepository.contacts

    override val contactsJson: String
        get() = contactsRepository.asJsonString

    override fun parseContacts(json: String): List<Contact> = contactsRepository.parseFromJson(json)

    override fun restoreContacts(contacts: List<Contact>) {
        contactsRepository.restore(contacts)
    }

    override fun customRpcCount(): Int? = blockchainSettingsBackup.customRpcCount()

    override suspend fun restore(settings: Settings, passphrase: String) {
        appearanceSettingsBackup.restore(settings)

        withContext(Dispatchers.Main) {
            try {
                languageManager.currentLocaleTag = settings.language
            } catch (e: Exception) {
                logger.e(e) { "Failed to restore language settings" }
            }
        }

        chartIndicatorsBackup.restore(settings)

        settings.conversionTokenQueryId?.let { baseTokenManager.setBaseTokenQueryId(it) }

        currencyManager.setBaseCurrencyCode(settings.baseCurrency)

        blockchainSettingsBackup.restore(settings, passphrase)

        AppIcon.fromTitle(settings.appIcon)
            ?.let(::sanitizeRestoredAppIcon)
            ?.takeIf { it != (localStorage.appIcon ?: AppIcon.Main) }
            ?.let { appIconService.setAppIcon(it) }
    }

    private fun sanitizeRestoredAppIcon(icon: AppIcon): AppIcon {
        if (icon != AppIcon.Calculator) return icon
        val premiumActive = getKoinInstance<CheckPremiumUseCase>().getPremiumType().isPremium()
        val pinSet = getKoinInstance<IPinComponent>().isPinSet
        return if (premiumActive && pinSet) icon else AppIcon.Main
    }

    override fun currentSettings(cachedKey: ByteArray, passphrase: String): Settings = Settings(
        balanceViewType = appearanceSettingsBackup.balanceViewType,
        appIcon = localStorage.appIcon?.titleText ?: AppIcon.Main.titleText,
        currentTheme = appearanceSettingsBackup.theme,
        chartIndicatorsEnabled = chartIndicatorsBackup.enabled,
        chartIndicators = chartIndicatorsBackup.indicators(),
        balanceAutoHidden = appearanceSettingsBackup.balanceAutoHidden,
        conversionTokenQueryId = baseTokenManager.token?.tokenQuery?.id,
        language = languageManager.currentLocaleTag,
        launchScreen = appearanceSettingsBackup.launchScreen,
        marketsTabEnabled = appearanceSettingsBackup.marketsTabEnabled,
        balanceHideButtons = appearanceSettingsBackup.balanceHideButtons,
        baseCurrency = currencyManager.baseCurrency.code,
        btcModes = blockchainSettingsBackup.btcModes(),
        priceChangeMode = appearanceSettingsBackup.priceChangeMode,
        evmSyncSources = blockchainSettingsBackup.evmSyncSources(cachedKey, passphrase),
        solanaSyncSource = blockchainSettingsBackup.solanaSyncSource(),
    )
}
