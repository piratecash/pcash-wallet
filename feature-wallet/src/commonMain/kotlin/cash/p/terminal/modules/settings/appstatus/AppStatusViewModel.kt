package cash.p.terminal.modules.settings.appstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.AppVersionStorage
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.BtcBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.EvmBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.MoneroBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.SolanaBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.StatusItem
import cash.p.terminal.modules.blockchainstatus.StatusReport
import cash.p.terminal.modules.blockchainstatus.StatusSection
import cash.p.terminal.modules.blockchainstatus.StellarBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.ThorchainBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.TonBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.TronBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.ZcashBlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.appendStatusSection
import cash.p.terminal.modules.blockchainstatus.buildCopyText
import cash.p.terminal.modules.blockchainstatus.buildLogGroups
import cash.p.terminal.modules.settings.appstatus.AppStatusModule.BlockContent
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_AppStatus
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class AppStatusViewModel(
    private val systemInfoManager: ISystemInfoManager,
    private val appVersionStorage: AppVersionStorage,
    private val platformInfo: AppStatusPlatformInfo,
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager,
    private val marketKit: MarketKitWrapper,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val moneroKitManager: MoneroKitManager,
    private val stellarKitManager: StellarKitManager,
    private val thorchainKitManagers: ThorchainKitManagers,
    private val tronKitManager: TronKitManager,
    private val tonKitManager: TonKitManager,
    private val solanaKitManager: SolanaKitManager,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val networkErrorTracker: NetworkErrorTracker,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppStatusModule.UiState(
            appStatusAsText = null,
            blockViewItems = emptyList(),
            logGroups = emptyList(),
            blockchainStatusSections = emptyList(),
            loading = true,
            shareInProgress = false,
            report = null,
        )
    )
    val uiState: StateFlow<AppStatusModule.UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            val logGroups = buildLogGroups(AppLog.getLog())

            val blockViewItems = listOf<AppStatusModule.BlockData>()
                .asSequence()
                .plus(getAppInfoBlock())
                .plus(getEnabledBlockchainsBlock())
                .plus(getVersionHistoryBlock())
                .plus(getWalletsStatusBlock())
                .plus(getMarketLastSyncTimestampsBlock())
                .toList()

            val blockchainStatusSections = getBlockchainStatusSections()

            val appStatusAsText = buildCopyText(buildStatusText(blockchainStatusSections), logGroups)

            _uiState.value = AppStatusModule.UiState(
                appStatusAsText = appStatusAsText,
                blockViewItems = blockViewItems,
                logGroups = logGroups,
                blockchainStatusSections = blockchainStatusSections,
                loading = false,
                shareInProgress = false,
                report = buildReport(blockchainStatusSections),
            )
        }
    }

    /** Ignored while a previous share is still running. */
    suspend fun share(export: suspend (StatusReport) -> Unit) {
        if (_uiState.getAndUpdate { it.copy(shareInProgress = true) }.shareInProgress) return
        try {
            val report = withContext(dispatcherProvider.io) {
                buildReport(_uiState.value.blockchainStatusSections)
            }
            export(report)
        } finally {
            _uiState.update { it.copy(shareInProgress = false) }
        }
    }

    private fun buildReport(blockchainStatusSections: List<StatusSection>) = StatusReport(
        fileName = REPORT_FILE_NAME,
        header = buildShareHeader(),
        logTag = null,
        footer = buildShareFooter(blockchainStatusSections),
        shareText = Res.string.Settings_AppStatus,
    )

    private fun buildStatusText(blockchainStatusSections: List<StatusSection>): String = buildString {
        formatMapToString(headerSections() + footerSections())?.let { append(it) }
        appendBlockchainStatus(blockchainStatusSections)
    }

    /** App Info, Enabled Blockchains, Version History, Wallets Status — shared by Copy and Share. */
    private fun headerSections(): Map<String, Any> = linkedMapOf(
        "App Info" to getAppInfo(),
        "Enabled Blockchains" to getEnabledBlockchains(),
        "Version History" to getVersionHistory(),
        "Wallets Status" to getWalletsStatus(),
    )

    /** Market Last Sync Timestamps — shared by Copy and Share. */
    private fun footerSections(): Map<String, Any> = linkedMapOf(
        "Market Last Sync Timestamps" to getMarketLastSyncTimestamps(),
    )

    private fun buildShareHeader(): String = buildString {
        formatMapToString(headerSections())?.let { append(it) }
        appendLine()
        appendLine("App Log:")
    }

    private fun buildShareFooter(blockchainStatusSections: List<StatusSection>): String = buildString {
        appendLine()
        formatMapToString(footerSections())?.let { append(it) }
        appendBlockchainStatus(blockchainStatusSections)
    }

    private fun StringBuilder.appendBlockchainStatus(blockchainStatusSections: List<StatusSection>) {
        if (blockchainStatusSections.isEmpty()) return
        appendLine()
        appendLine("Blockchain Status")
        blockchainStatusSections.forEach { appendStatusSection(it) }
    }

    private fun getBlockchainStatusSections(): List<StatusSection> {
        val sections = mutableListOf<StatusSection>()

        // BTC-like chains — grouped by blockchain type
        btcBlockchainManager.blockchainTypes.forEach { blockchainType ->
            val hasActiveWallets =
                walletManager.activeWallets.any { it.token.blockchainType == blockchainType }
            if (!hasActiveWallets) return@forEach

            val blockchain = btcBlockchainManager.blockchain(blockchainType) ?: return@forEach
            collectProviderSections(
                BtcBlockchainStatusProvider(
                    blockchain,
                    btcBlockchainManager,
                    walletManager,
                    adapterManager
                ), sections
            )
        }

        // EVM chains
        evmBlockchainManager.allBlockchains.forEach { blockchain ->
            collectProviderSections(
                EvmBlockchainStatusProvider(blockchain, evmBlockchainManager),
                sections
            )
        }

        // Other chains
        collectProviderSections(TronBlockchainStatusProvider(tronKitManager), sections)
        collectProviderSections(TonBlockchainStatusProvider(tonKitManager), sections)
        collectProviderSections(StellarBlockchainStatusProvider(stellarKitManager), sections)
        thorchainKitManagers.all.forEach { kitManager ->
            collectProviderSections(ThorchainBlockchainStatusProvider(kitManager), sections)
        }
        collectProviderSections(SolanaBlockchainStatusProvider(solanaKitManager), sections)
        collectProviderSections(
            ZcashBlockchainStatusProvider(walletManager, adapterManager, networkErrorTracker),
            sections
        )
        collectProviderSections(MoneroBlockchainStatusProvider(moneroKitManager), sections)

        return sections
    }

    private fun collectProviderSections(
        provider: BlockchainStatusProvider,
        into: MutableList<StatusSection>
    ) {
        val status = provider.getStatus()
        status.sections.forEachIndexed { index, section ->
            if (section.items.isEmpty()) return@forEachIndexed
            if (index == 0) {
                into.add(
                    section.copy(
                        items = listOf(
                            StatusItem.KeyValue(
                                "Kit Version",
                                provider.kitVersion
                            )
                        ) + section.items
                    )
                )
            } else {
                into.add(section)
            }
        }
        status.sharedSection?.let { into.add(it) }
    }

    private fun getVersionHistory(): Map<String, Any> {
        val versions = LinkedHashMap<String, Date>()

        appVersionStorage.appVersions.sortedBy { it.timestamp }.forEach { version ->
            versions[version.version] = Date(version.timestamp)
        }
        return versions
    }

    private fun getVersionHistoryBlock(): AppStatusModule.BlockData {
        val versions = mutableListOf<BlockContent.TitleValue>()
        appVersionStorage.appVersions.sortedBy { it.timestamp }.forEach { version ->
            versions.add(
                BlockContent.TitleValue(
                    DateHelper.formatDate(Date(version.timestamp), "MMM d, yyyy, HH:mm"),
                    version.version,
                )
            )
        }

        return AppStatusModule.BlockData("Version History", versions)
    }

    private fun getWalletsStatus(): Map<String, Any> {
        val wallets = LinkedHashMap<String, Any>()

        for (account in accountManager.accounts) {
            val title = account.name

            wallets[title] = getAccountDetails(account)
        }
        return wallets
    }

    private fun getWalletsStatusBlock(): List<AppStatusModule.BlockData> {
        val walletBlocks = mutableListOf<AppStatusModule.BlockData>()

        accountManager.accounts.forEachIndexed { index, account ->
            val title = if (index == 0) "Wallet Status" else null
            val origin = getAccountOrigin(account)

            walletBlocks.add(
                AppStatusModule.BlockData(
                    title,
                    listOf(
                        BlockContent.TitleValue("Name", account.name),
                        BlockContent.TitleValue("Origin", origin),
                        BlockContent.TitleValue("Type", account.type.description),
                    )
                )
            )
        }
        return walletBlocks
    }

    private fun marketLastSyncLines(): List<Pair<String, String>> {
        val syncInfo = marketKit.syncInfo()
        return buildList {
            add("Coins List Downloaded" to (syncInfo.listDownloadedAt?.let(::formatSyncDate) ?: ""))
            add("Ranks Downloaded" to (syncInfo.ranksDownloadedAt?.let(::formatSyncDate) ?: ""))
            syncInfo.coinsCount?.let { add("Coins Count" to it.toString()) }
            syncInfo.blockchainsCount?.let { add("Blockchains Count" to it.toString()) }
            syncInfo.tokensCount?.let { add("Tokens Count" to it.toString()) }
            syncInfo.serverAvailable?.let { add("Server Available" to if (it) "Yes" else "No") }
        }
    }

    private fun formatSyncDate(timestamp: Long) = DateHelper.formatDate(Date(timestamp), "MMM d, yyyy, HH:mm")

    private fun getMarketLastSyncTimestamps(): Map<String, Any> =
        marketLastSyncLines().toMap()

    private fun getMarketLastSyncTimestampsBlock(): AppStatusModule.BlockData =
        AppStatusModule.BlockData(
            title = "Market Sync Info",
            content = marketLastSyncLines().map { (title, value) -> BlockContent.TitleValue(title, value) }
        )

    private fun getAppInfo(): Map<String, Any> = getAppInfoRows().toMap()

    private fun getAppInfoBlock() = AppStatusModule.BlockData(
        title = "App Info",
        content = getAppInfoRows().map { (title, value) -> BlockContent.TitleValue(title, value) }
    )

    private fun getAppInfoRows(): List<Pair<String, String>> = buildList {
        addAll(platformInfo.buildInfo().toList())
        systemInfoManager.getSigningCertFingerprint()?.let { add("App Signature" to it) }
        add("Device Model" to systemInfoManager.deviceModel)
        add("OS Version" to systemInfoManager.osVersion)
        addAll(platformInfo.deviceInfo().toList())
        add("Premium Status" to checkPremiumUseCase.getPremiumType().name)
        platformInfo.uniqueCode()?.let { add("Unique Code" to it) }
    }

    private fun getEnabledBlockchainsString(): String {
        val names = walletManager.activeWallets
            .map { it.token.blockchainType }
            .distinct()
            .mapNotNull { marketKit.blockchain(it.uid)?.name }
            .sorted()

        return if (names.isEmpty()) "None" else names.joinToString(", ")
    }

    private fun getEnabledBlockchainsBlock(): AppStatusModule.BlockData {
        return AppStatusModule.BlockData(
            title = "Enabled Blockchains",
            content = listOf(
                BlockContent.TitleValue("Blockchains", getEnabledBlockchainsString()),
            )
        )
    }

    private fun getEnabledBlockchains(): Map<String, Any> {
        return linkedMapOf(
            "Blockchains" to getEnabledBlockchainsString(),
        )
    }

    private fun getAccountDetails(account: cash.p.terminal.wallet.Account): LinkedHashMap<String, Any> {
        val accountDetails = LinkedHashMap<String, Any>()

        accountDetails["Origin"] = getAccountOrigin(account)
        accountDetails["Type"] = account.type.description

        return accountDetails
    }

    private fun getAccountOrigin(account: cash.p.terminal.wallet.Account): String {
        return if (account.isWatchAccount) "Watched" else account.origin.value
    }

    private fun formatMapToString(status: Map<String, Any>?): String? {
        if (status == null) return null
        val list = convertNestedMapToStringList(status)
        return list.joinToString("\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertNestedMapToStringList(
        map: Map<String, Any>,
        bullet: String = "",
        level: Int = 0,
    ): List<String> {
        val resultList = mutableListOf<String>()
        map.forEach { (key, value) ->
            val indent = "  ".repeat(level)
            when (value) {
                is Map<*, *> -> {
                    resultList.add("$indent$bullet$key:")
                    resultList.addAll(
                        convertNestedMapToStringList(
                            map = value as Map<String, Any>,
                            bullet = " - ",
                            level = level + 1
                        )
                    )
                    if (level < 2) resultList.add("")
                }

                is Date -> {
                    val date = DateHelper.formatDate(value, "MMM d, yyyy, HH:mm")
                    resultList.add("$indent$bullet$key: $date")
                }

                else -> resultList.add("$indent$bullet$key: $value")
            }
        }
        return resultList
    }

    private companion object {
        const val REPORT_FILE_NAME = "app_status_report.txt"
    }
}
