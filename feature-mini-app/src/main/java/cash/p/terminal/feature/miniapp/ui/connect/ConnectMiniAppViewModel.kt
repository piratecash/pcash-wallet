package cash.p.terminal.feature.miniapp.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.feature.miniapp.data.api.MiniAppApiException
import cash.p.terminal.feature.miniapp.domain.model.CoinType
import cash.p.terminal.feature.miniapp.domain.model.SpecialProposalData
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.feature.miniapp.domain.usecase.CheckRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.ConnectMiniAppWalletUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.GetSpecialProposalDataUseCase
import cash.p.terminal.feature.miniapp.domain.usecase.NoEvmSignerException
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.strings.R
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.BuildConfig
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.title
import cash.p.terminal.wallet.balance.BalanceService
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ConnectMiniAppViewModel(
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val getSpecialProposalDataUseCase: GetSpecialProposalDataUseCase,
    private val checkRequiredTokensUseCase: CheckRequiredTokensUseCase,
    private val createRequiredTokensUseCase: CreateRequiredTokensUseCase,
    private val connectMiniAppWalletUseCase: ConnectMiniAppWalletUseCase,
    private val accountManager: IAccountManager,
    private val marketKitWrapper: MarketKitWrapper,
    private val balanceService: BalanceService,
    private val uniqueCodeStorage: IUniqueCodeStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val logger = AppLogger("ConnectMiniApp")

    companion object {
        const val STEP_WALLET = 1
        const val STEP_SPECIAL_PROPOSAL = 2
        const val STEP_FINISH = 3
    }

    private fun Token.nameWithBadge(): String {
        val badgeText = badge
        return if (badgeText != null) "${coin.name} $badgeText" else coin.name
    }

    private val input: ConnectMiniAppDeeplinkInput? = savedStateHandle["input"]
    val jwt: String? = input?.jwt
    val endpoint: String = input?.endpoint ?: "https://p.cash/"

    var uiState by mutableStateOf(ConnectMiniAppUiState())
        private set

    init {
        viewModelScope.launch {
            accountManager.accountsFlow.collect {
                checkWalletStatus()
            }
        }
        checkWalletStatus()
    }

    fun checkWalletStatus() {
        if (uiState.currentStep > STEP_WALLET) {
            return
        }

        uiState.chosenAccountId?.let { chosenAccountId ->
            // Already have selected account
            viewModelScope.launch {
                val account = accountManager.account(chosenAccountId)
                checkSingleWallet(account)
            }
            return
        }

        val eligibleAccounts = getEligibleAccountsToChoose()
        when {
            eligibleAccounts.size > 1 -> checkWalletsMultiple(eligibleAccounts)
            eligibleAccounts.isEmpty() -> {
                uiState = uiState.copy(
                    currentStep = STEP_WALLET,
                    isLoading = false,
                    needsBackup = false,
                    walletItems = emptyList(),
                    chosenAccountId = null
                )
            }

            else -> checkSingleWallet(eligibleAccounts.firstOrNull())
        }
    }

    private fun checkWalletsMultiple(eligibleAccounts: List<Account>) {
        viewModelScope.launch {
            val walletItems = eligibleAccounts.map { account ->
                WalletViewItem(
                    accountId = account.id,
                    name = account.name,
                    isPremium = checkPremiumUseCase.checkPremiumByBalanceForAccount(account)
                        .isPremium()
                )
            }
            val storedAccountId = uniqueCodeStorage.connectedAccountId
            uiState = uiState.copy(
                walletItems = walletItems,
                isLoading = false,
                preselectedAccountId = storedAccountId
                    .takeIf { it.isNotBlank() && walletItems.any { item -> item.accountId == storedAccountId } }
                    ?: walletItems.firstOrNull { it.isPremium }?.accountId
                    ?: walletItems.firstOrNull()?.accountId
            )
        }
    }

    private fun checkSingleWallet(account: Account?) {
        if (account == null) {
            uiState = uiState.copy(
                currentStep = STEP_WALLET,
                needsBackup = false,
                walletItems = emptyList(),
                isLoading = false,
                chosenAccountId = null
            )
            return
        }

        if (account.hasAnyBackup) {
            // Set active account and check token availability
            accountManager.setActiveAccountId(account.id)
            uiState = uiState.copy(
                isLoading = false,
                chosenAccountId = account.id
            )
            checkTokenAvailability()
        } else {
            uiState = uiState.copy(
                currentStep = STEP_WALLET,
                isLoading = false,
                needsBackup = true,
                chosenAccountId = account.id
            )
        }
    }

    private fun getEligibleAccountsToChoose(): List<Account> {
        return accountManager.accounts.filter { account ->
            !account.isWatchAccount && (account.type.canAddTokens || account.isHardwareWalletAccount)
        }
    }

    fun onWalletSelected(accountId: String) {
        uiState = uiState.copy(preselectedAccountId = accountId)
    }

    fun onConfirmWalletSelectedClick() {
        uiState.preselectedAccountId?.let { accountId ->
            uiState = uiState.copy(chosenAccountId = accountId)
            accountManager.setActiveAccountId(accountId)
        }
        checkTokenAvailability()
    }

    private fun checkTokenAvailability() {
        val accountId = uiState.chosenAccountId ?: return
        val account = accountManager.account(accountId) ?: return

        if (!account.hasAnyBackup && account.supportsBackup) { // Waiting for a backup
            checkWalletStatus()
            return
        }

        uiState = uiState.copy(
            isCheckingTokens = true,
            missingTokenNames = emptyList(),
            missingTokenQueries = emptyList(),
            tokenCheckError = null
        )

        viewModelScope.launch {
            runCatching {
                checkRequiredTokensUseCase(account)
            }.onSuccess { result ->
                val allTokensText = result.allTokens.joinToString(", ") { it.nameWithBadge() }
                if (result.allTokensExist) {
                    uiState = uiState.copy(
                        isCheckingTokens = false,
                        allTokensText = allTokensText,
                        currentStep = STEP_SPECIAL_PROPOSAL,
                        // The step loads its data from a resume effect, i.e. after the first
                        // composition: without this the empty state is drawn for a frame.
                        isSpecialProposalLoading = true
                    )
                } else {
                    // Catalog metadata can be absent while the requirement stands (e.g. a hardware
                    // account missing its BSC key), so fall back to the chain title.
                    val missingNames = result.missingTokens.map { it.nameWithBadge() }
                        .ifEmpty { result.missingTokenQueries.map { it.blockchainType.title } }
                    uiState = uiState.copy(
                        isCheckingTokens = false,
                        allTokensText = allTokensText,
                        missingTokenNames = missingNames,
                        missingTokenQueries = result.missingTokenQueries
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "Failed to check token availability")
                uiState = uiState.copy(
                    isCheckingTokens = false,
                    tokenCheckError = Translator.getString(R.string.connect_mini_app_error_token_check)
                )
            }
        }
    }

    fun onRetryTokenCheck() {
        uiState = uiState.copy(tokenCheckError = null)
        checkTokenAvailability()
    }

    fun onAddTokensClick() {
        val accountId = uiState.chosenAccountId ?: return
        val account = accountManager.account(accountId) ?: return
        val missingQueries = uiState.missingTokenQueries

        if (missingQueries.isEmpty()) return

        uiState = uiState.copy(isAddingTokens = true)

        viewModelScope.launch {
            runCatching {
                createRequiredTokensUseCase(account, missingQueries)
            }.onSuccess {
                uiState = uiState.copy(isAddingTokens = false)
                checkTokenAvailability() // Re-check after creation
            }.onFailure { error ->
                Timber.e(error, "Failed to add tokens")
                uiState = uiState.copy(isAddingTokens = false)
            }
        }
    }

    // Special Proposal methods
    fun loadSpecialProposalData() {
        val currentJwt = jwt ?: return
        val selectedAccountId = uiState.chosenAccountId ?: return

        uiState = uiState.copy(isSpecialProposalLoading = true, specialProposalError = null)

        viewModelScope.launch {
            runCatching {
                getSpecialProposalDataUseCase(
                    selectedAccountId = selectedAccountId,
                    jwt = currentJwt,
                    endpoint = endpoint,
                    currencyCode = balanceService.baseCurrency.code
                )
            }.onSuccess { data ->
                uiState = uiState.copy(
                    specialProposalData = data,
                    selectedCoinTab = data.cheaperOption,
                    isPremiumUser = data.hasPremium,
                    isSpecialProposalLoading = false
                )
            }.onFailure { error ->
                Timber.e(error, "Failed to load special proposal data")
                if (error is MiniAppApiException && error.isJwtExpired) {
                    uiState = uiState.copy(
                        isSpecialProposalLoading = false,
                        isJwtExpired = true
                    )
                } else {
                    val errorMessage = when (error) {
                        is MiniAppApiException -> error.message
                        else -> "Failed to load data"
                    }
                    uiState = uiState.copy(
                        isSpecialProposalLoading = false,
                        specialProposalError = errorMessage
                    )
                }
            }
        }
    }

    fun onCoinTabSelected(coinType: CoinType) {
        uiState = uiState.copy(selectedCoinTab = coinType)
    }

    suspend fun getTokenForSwap(): Token? {
        val contractAddress = when (uiState.selectedCoinTab) {
            CoinType.PIRATE -> BuildConfig.PIRATE_CONTRACT
            CoinType.COSA -> BuildConfig.COSANTA_CONTRACT
        }

        val tokenQuery = TokenQuery.eip20(BlockchainType.BinanceSmartChain, contractAddress)
        return withContext(Dispatchers.IO) {
            marketKitWrapper.token(tokenQuery)
        }
    }

    fun connectWallet() {
        val currentJwt = jwt ?: return
        val accountId = uiState.chosenAccountId ?: return

        uiState = uiState.copy(currentStep = STEP_FINISH, finishState = FinishState.Loading)

        viewModelScope.launch {
            val log = logger.getScopedUnique()
            runCatching {
                val account = accountManager.account(accountId)
                    ?: throw IllegalStateException("Account not found")
                log.info("accountType: ${account.type::class.simpleName}")
                connectMiniAppWalletUseCase(account, currentJwt, endpoint)
            }.onSuccess {
                log.info("success")
                uiState = uiState.copy(finishState = FinishState.Success)
            }.onFailure { error ->
                log.warning("failed", error)
                Timber.e(error, "Failed to submit pcash wallet")
                uiState = uiState.copy(
                    finishState = when {
                        error is MiniAppApiException && error.isJwtExpired -> FinishState.JwtExpired
                        error is NoEvmSignerException -> FinishState.Error(
                            Translator.getString(R.string.connect_mini_app_error_no_evm_key)
                        )
                        else -> FinishState.Error(
                            when (error) {
                                is MiniAppApiException -> error.message
                                else -> error.message ?: "Connection failed"
                            }
                        )
                    }
                )
            }
        }
    }

    fun onRetryClick() {
        connectWallet()
    }

    fun onFinishClose() {
        uiState = uiState.copy(closeEvent = true)
    }
}

sealed class FinishState {
    data object Loading : FinishState()
    data object Success : FinishState()
    data object JwtExpired : FinishState()
    data class Error(val message: String?) : FinishState()
}

data class ConnectMiniAppUiState(
    val currentStep: Int = 1,
    val needsBackup: Boolean = false,
    val isLoading: Boolean = true,
    val walletItems: List<WalletViewItem> = emptyList(),
    val chosenAccountId: String? = null,
    // for UI selection only
    val preselectedAccountId: String? = null,
    // Token checking state
    val isCheckingTokens: Boolean = false,
    val allTokensText: String = "",
    val missingTokenNames: List<String> = emptyList(),
    val missingTokenQueries: List<TokenQuery> = emptyList(),
    val isAddingTokens: Boolean = false,
    val tokenCheckError: String? = null,
    val isJwtExpired: Boolean = false,
    // Special Proposal state
    val specialProposalData: SpecialProposalData? = null,
    val selectedCoinTab: CoinType = CoinType.PIRATE,
    val isSpecialProposalLoading: Boolean = false,
    val isPremiumUser: Boolean = false,
    val specialProposalError: String? = null,
    // Finish state
    val finishState: FinishState? = null,
    val closeEvent: Boolean = false
)

data class WalletViewItem(
    val accountId: String,
    val name: String,
    val isPremium: Boolean
)
