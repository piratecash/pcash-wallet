package cash.p.terminal.modules.nft.burn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.nft.owner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.nft.NftOwnerWalletResolver
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.nft.operation.TonNftOperationRunner
import cash.p.terminal.modules.nft.shortNftAddress
import cash.p.terminal.modules.nft.ui.HIDDEN_NAME
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.DispatcherProvider

/** [name] is null for an unnamed NFT. */
data class NftBurnUiState(
    val name: String?,
    val isDomain: Boolean,
    val burnAddress: String,
    val fee: String,
    val insufficientBalance: TranslatableString?,
    val canBurn: Boolean,
    val outcome: NftOperationOutcome,
)

/** [domain]: the item is a TON DNS domain with this name; [namesRevealed] as in the renewal sheet. */
class NftBurnViewModel(
    item: NftItemRecord,
    private val domain: String?,
    private val namesRevealed: Boolean,
    walletResolver: NftOwnerWalletResolver,
    adapterManager: IAdapterManager,
    balanceHiddenManager: IBalanceHiddenManager,
    offlineOperationGate: OfflineOperationGate,
    xRateService: XRateService,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val name = domain ?: item.name?.takeUnless { it.isBlank() }
    private val runner = TonNftOperationRunner(
        scope = viewModelScope,
        operation = NftBurnOperation,
        targets = mapOf(item.owner to listOf(item.address)),
        walletResolver = walletResolver,
        adapterManager = adapterManager,
        balanceHiddenManager = balanceHiddenManager,
        offlineOperationGate = offlineOperationGate,
        xRateService = xRateService,
        dispatcherProvider = dispatcherProvider,
    )

    val uiState: NftBurnUiState
        get() {
            val state = runner.state
            return NftBurnUiState(
                name = if (state.balanceHidden && !namesRevealed) HIDDEN_NAME else name,
                isDomain = domain != null,
                burnAddress = TON_BURN_ADDRESS.shortNftAddress(),
                fee = state.fee,
                insufficientBalance = state.insufficientBalance,
                canBurn = state.canRun,
                outcome = state.outcome,
            )
        }

    /** Call after the PIN gate. */
    fun onBurn() = runner.run()
}
