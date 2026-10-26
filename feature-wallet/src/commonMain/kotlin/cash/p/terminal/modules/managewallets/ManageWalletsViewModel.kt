package cash.p.terminal.modules.managewallets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.restoreaccount.restoreblockchains.CoinViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.add_via_trezor
import cash.p.terminal.resources.error_hardware_wallet_not_supported
import cash.p.terminal.resources.error_hardware_wallet_some_tokens_not_found
import cash.p.terminal.resources.error_no_active_account
import cash.p.terminal.resources.offline_mode_asset_added
import cash.p.terminal.resources.platform_not_supported_yet
import cash.p.terminal.resources.scan_card_to_add
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.serves
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.wallet.isMonero
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.policy.PlatformChainSupport
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = Logger.withTag("ManageWalletsViewModel")

class ManageWalletsViewModel(
    private val service: ManageWalletsService,
    private val clearables: List<Clearable>
) : ViewModel(), ManageWalletsCallback {

    private val accountManager: IAccountManager = getKoinInstance()
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage = getKoinInstance()
    private val hardwareWalletTokenPolicy: HardwareWalletTokenPolicy = getKoinInstance()
    private val keyProvisioner: HardwareWalletKeyProvisioner = getKoinInstance()
    private val offlineOperationGate: OfflineOperationGate = getKoinInstance()
    private val platformChainSupport: PlatformChainSupport = getKoinInstance()

    private val _groupsList = MutableStateFlow<List<CoinGroup>>(emptyList())
    override var groupsList: StateFlow<List<CoinGroup>> = _groupsList.asStateFlow()

    private val awaitingEnabledTokens = mutableSetOf<Token>()
    private val expandedGroups = mutableSetOf<String>()
    private var existingPublicKeys: List<HardwarePublicKey>? = null
    private var closeAfterSuccessfulScan = false
    private var scanToAddInProgress = false
    private var scanToAddJob: Job? = null

    override var showScanToAddButton by mutableStateOf(false)
        private set

    override var hardwareActionButtonText by mutableStateOf(
        when (accountManager.activeAccount?.type) {
            is AccountType.TrezorDevice -> Translator.getString(Res.string.add_via_trezor)
            else -> Translator.getString(Res.string.scan_card_to_add)
        }
    )
        private set

    override var errorMsg by mutableStateOf<String?>(null)
        private set

    override var offlineNoticeMsg by mutableStateOf<String?>(null)
        private set

    override var closeScreen by mutableStateOf(false)
        private set

    init {
        loadData()
    }

    private fun loadData() {
        if (isHardwareWallet()) {
            viewModelScope.launch {
                accountManager.activeAccount?.let { account ->
                    existingPublicKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)
                }
            }
        }
        viewModelScope.launch {
            service.itemsFlow.collect {
                sync(it)
            }
        }
        viewModelScope.launch {
            service.restoreSettingsRejectedFlow.collect(::handleRestoreSettingsRejected)
        }
        viewModelScope.launch {
            service.enabledTokensFlow.collect(::notifyIfNetworkOffline)
        }
    }

    fun requestScanToAddTokens(closeAfterSuccess: Boolean): Job {
        if (scanToAddInProgress) return checkNotNull(scanToAddJob)
        scanToAddInProgress = true
        return viewModelScope.launch {
            val account = accountManager.activeAccount
            if (account?.isHardwareWalletAccount == true) {
                requestHardwareKeys(account, closeAfterSuccess)
            } else {
                showError(Translator.getString(Res.string.error_no_active_account))
                finishScanToAdd()
            }
        }.also { scanToAddJob = it }
    }

    private suspend fun requestHardwareKeys(account: Account, closeAfterSuccess: Boolean) {
        when (val result = keyProvisioner.provisionPublicKeys(account, awaitingTokenQueries())) {
            is ProvisionResult.Success -> try {
                applyFetchedKeys(result.keys, result.account, closeAfterSuccess)
            } catch (e: Exception) {
                finishScanToAdd()
                logger.e(e) { "Failed to apply hardware wallet public keys" }
                showError(e.message)
            }
            ProvisionResult.Cancelled -> finishScanToAdd()
            is ProvisionResult.Failed -> {
                finishScanToAdd()
                showError(result.message)
            }
        }
    }

    private fun awaitingTokenQueries(): List<TokenQuery> =
        awaitingEnabledTokens.map {
            TokenQuery(blockchainType = it.blockchainType, tokenType = it.type)
        }.distinct()

    private suspend fun applyFetchedKeys(
        publicKeys: List<HardwarePublicKey>,
        account: Account,
        closeAfterSuccess: Boolean
    ) {
        val pendingMoneroTokens = awaitingEnabledTokens.filter(Token::isMonero)
        val (addedTokens, notFoundTokens) = awaitingEnabledTokens
            .filterNot(Token::isMonero)
            .partition { token ->
                publicKeys.any {
                    it.blockchainType == token.blockchainType.uid && it.tokenType == token.type
                }
            }
        replaceAwaitingTokens(notFoundTokens + pendingMoneroTokens)

        errorMsg = if (notFoundTokens.isNotEmpty()) {
            Translator.getString(Res.string.error_hardware_wallet_some_tokens_not_found)
        } else {
            null
        }

        hardwarePublicKeyStorage.save(publicKeys)
        existingPublicKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)

        updateCloseAfterScan(closeAfterSuccess, pendingMoneroTokens)
        if (pendingMoneroTokens.isEmpty()) finishScanToAdd()
        addedTokens.forEach { service.enable(it) }
        pendingMoneroTokens.forEach(service::enable)
    }

    private fun replaceAwaitingTokens(tokens: List<Token>) {
        awaitingEnabledTokens.clear()
        awaitingEnabledTokens.addAll(tokens)
        updateNeedToShowScanToAddButton()
    }

    private fun updateCloseAfterScan(closeAfterSuccess: Boolean, pendingMoneroTokens: List<Token>) {
        closeAfterSuccessfulScan =
            closeAfterSuccess && errorMsg == null && pendingMoneroTokens.isNotEmpty()
        closeScreen =
            closeScreen ||
                (errorMsg == null && closeAfterSuccess && pendingMoneroTokens.isEmpty())
    }

    private fun showError(msg: String?) {
        errorMsg = msg
        if (msg == null) return

        viewModelScope.launch {
            delay(SnackbarDuration.LONG.value.toLong())
            errorMsg = null
        }
    }

    override fun onOfflineNoticeShown() {
        offlineNoticeMsg = null
    }

    private fun notifyIfNetworkOffline(token: Token) {
        val account = accountManager.activeAccount ?: return
        if (!offlineOperationGate.isBlocked(account, token.blockchainType)) return

        offlineNoticeMsg = Translator.getString(
            Res.string.offline_mode_asset_added,
            token.coin.code,
            token.blockchain.name
        )
    }

    private fun sync(items: List<ManageWalletsService.Item>) {
        val enabledMoneroTokens = awaitingEnabledTokens.filter { token ->
            token.isMonero() && items.any { it.token == token && it.enabled }
        }
        if (enabledMoneroTokens.isNotEmpty()) {
            awaitingEnabledTokens.removeAll(enabledMoneroTokens.toSet())
            finishScanToAdd()
            updateNeedToShowScanToAddButton()
        }
        if (closeAfterSuccessfulScan && awaitingEnabledTokens.isEmpty()) {
            closeAfterSuccessfulScan = false
            closeScreen = true
        }

        val viewItems = items.map { viewItem(it) }

        val groups = viewItems.groupBy { it.item.coin.uid }
            .map { (coinUid, groupItems) ->
                val coinName = groupItems.first().item.coin.name
                CoinGroup(
                    coinName = coinName,
                    coinUid = coinUid,
                    items = groupItems,
                    isExpanded = expandedGroups.contains(coinUid)
                )
            }
        _groupsList.tryEmit(groups)
    }

    private fun handleRestoreSettingsRejected(token: Token) {
        if (!awaitingEnabledTokens.remove(token)) return
        finishScanToAdd()
        closeAfterSuccessfulScan = false
        updateNeedToShowScanToAddButton()
        sync(service.itemsFlow.value)
    }

    private fun finishScanToAdd() {
        scanToAddInProgress = false
    }

    private fun viewItem(
        item: ManageWalletsService.Item,
    ) = CoinViewItem(
        item = item.token,
        imageSource = ImageSource.Remote(
            item.token.coin.imageUrl,
            item.token.iconPlaceholder,
            item.token.coin.alternativeImageUrl
        ),
        title = item.token.coin.code,
        subtitle = item.token.coin.name,
        enabled = item.enabled || awaitingEnabledTokens.contains(item.token),
        hasInfo = item.hasInfo,
        label = item.token.badge
    )

    override fun enable(token: Token) {
        if (rejectedOnPlatform(token)) return
        val account = accountManager.activeAccount
        if (!isHardwareWallet() || hasPublicKey(token)) {
            service.enable(token)
            return
        }
        if (account != null && !hardwareWalletTokenPolicy.isSupported(account, token)) {
            showError(Translator.getString(Res.string.error_hardware_wallet_not_supported))
            return
        }
        if (existingPublicKeys?.any { it.serves(token.blockchainType, token.type) } == true) {
            service.enable(token)
            return
        }
        awaitingEnabledTokens.add(token)
        updateNeedToShowScanToAddButton()
        sync(service.itemsFlow.value)
    }

    override fun disable(token: Token) {
        if (rejectedOnPlatform(token)) return
        service.disable(token)
        if (isHardwareWallet()) {
            if (awaitingEnabledTokens.remove(token)) {
                sync(service.itemsFlow.value)
            }
            updateNeedToShowScanToAddButton()
        }
    }

    private fun rejectedOnPlatform(token: Token): Boolean {
        if (platformChainSupport.isSupported(token.blockchainType)) return false
        showError(Translator.getString(Res.string.platform_not_supported_yet))
        return true
    }

    override fun updateFilter(text: String) {
        service.setFilter(text)
    }

    override fun toggleGroupExpansion(coinUid: String) {
        if (expandedGroups.contains(coinUid)) {
            expandedGroups.remove(coinUid)
        } else {
            expandedGroups.add(coinUid)
        }
        sync(service.itemsFlow.value)
    }

    private fun updateNeedToShowScanToAddButton() {
        showScanToAddButton = isHardwareWallet() && awaitingEnabledTokens.isNotEmpty()
    }

    private fun hasPublicKey(token: Token): Boolean =
        existingPublicKeys?.any {
            it.blockchainType == token.blockchainType.uid && it.tokenType == token.type
        } == true

    override val addTokenEnabled: Boolean
        get() = service.accountType?.canAddTokens == true

    override fun onCleared() {
        clearables.forEach(Clearable::clear)
    }

    private fun isHardwareWallet() = accountManager.activeAccount?.isHardwareWalletAccount == true
}

data class CoinGroup(
    val coinName: String,
    val coinUid: String,
    val items: List<CoinViewItem<Token>>,
    val isExpanded: Boolean = false
) {
    val isSingleOption: Boolean
        get() = items.size == 1
}

interface ManageWalletsCallback {
    val groupsList: StateFlow<List<CoinGroup>>
    val addTokenEnabled: Boolean
    val showScanToAddButton: Boolean
    val hardwareActionButtonText: String
    val errorMsg: String?
    val offlineNoticeMsg: String?
    val closeScreen: Boolean

    fun updateFilter(text: String)
    fun onOfflineNoticeShown()
    fun enable(token: Token)
    fun disable(token: Token)
    fun toggleGroupExpansion(coinUid: String)
}
