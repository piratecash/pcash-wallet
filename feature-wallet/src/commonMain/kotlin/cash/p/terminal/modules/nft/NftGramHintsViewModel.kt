package cash.p.terminal.modules.nft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.nft.NftGramHintsRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class NftVersionHint(val version: TonWalletVersion, val count: Int)

/** [rows] offer the not-added GRAM versions that hold NFTs; [showAddGramBlock] is the hint for an account without GRAM. */
data class NftGramHintsUiState(
    val rows: List<NftVersionHint> = emptyList(),
    val showAddGramBlock: Boolean = false,
    val hidden: Boolean = false,
    val openCoinManager: Boolean = false,
)

class NftGramHintsViewModel(
    accountManager: IAccountManager,
    walletManager: IWalletManager,
    private val repository: NftGramHintsRepository,
    settings: BalanceSettingsStorage,
    balanceHiddenManager: IBalanceHiddenManager,
) : ViewModel() {

    private val hiddenFlow = NftCollectionSource.AllWallets.hiddenFlow(balanceHiddenManager)
    private val checks = MutableStateFlow(0)
    private var input: HintsInput? = null

    var uiState by mutableStateOf(NftGramHintsUiState(hidden = hiddenFlow.value))
        private set

    init {
        viewModelScope.launch { hiddenFlow.collect { uiState = uiState.copy(hidden = it) } }
        val activeAccount = accountManager.activeAccountStateFlow
            .filterIsInstance<ActiveAccountState.ActiveAccount>()
            .map { it.account }
        viewModelScope.launch {
            combine(
                activeAccount,
                walletManager.activeWalletsFlow,
                settings.showNftCollectionsFlow,
                checks,
            ) { account, wallets, show, _ -> account?.takeIf { show }?.let { hintsInput(it, wallets) } }
                .onEach { current ->
                    input = current
                    showHints(current, current?.let { repository.cachedNftCounts(it.account, it.pending) }.orEmpty())
                }
                // The count read is blocking I/O that ignores cancellation: conflate keeps the state above
                // current while an old read is still joined, and a late result for a stale input is dropped.
                .conflate()
                .collectLatest { current ->
                    current ?: return@collectLatest
                    val counts = repository.nftCounts(current.account, current.pending)
                    if (input === current) showHints(current, counts)
                }
        }
    }

    fun onResume() = recheck()

    fun add(version: TonWalletVersion) {
        val account = input?.account ?: return
        viewModelScope.launch {
            if (!repository.add(account, version)) uiState = uiState.copy(openCoinManager = true)
        }
    }

    fun addDefault() {
        val eligible = input?.eligible.orEmpty()
        (eligible.firstOrNull { it == TonWalletVersion.V4R2 } ?: eligible.firstOrNull())?.let(::add)
    }

    fun dismiss(version: TonWalletVersion) {
        val account = input?.account ?: return
        repository.dismiss(account, version)
        recheck()
    }

    fun onCoinManagerOpened() {
        uiState = uiState.copy(openCoinManager = false)
    }

    private fun recheck() {
        checks.value++
    }

    private fun hintsInput(account: Account, wallets: List<Wallet>): HintsInput? {
        val eligible = repository.eligibleVersions(account)
        if (eligible.isEmpty()) return null
        val added = repository.addedVersions(wallets.filter { it.account.id == account.id })
        val pending = eligible.filter { it !in added && !repository.isDismissed(account, it) }
        return HintsInput(account, eligible, pending, hasGram = added.isNotEmpty())
    }

    private fun showHints(input: HintsInput?, counts: Map<TonWalletVersion, Int>) {
        val rows = input?.pending.orEmpty().mapNotNull { version ->
            counts[version]?.takeIf { it > 0 }?.let { NftVersionHint(version, it) }
        }
        uiState = uiState.copy(rows = rows, showAddGramBlock = input?.hasGram == false && rows.isEmpty())
    }

    private class HintsInput(
        val account: Account,
        val eligible: List<TonWalletVersion>,
        val pending: List<TonWalletVersion>,
        val hasGram: Boolean,
    )
}
