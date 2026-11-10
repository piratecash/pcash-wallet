package cash.p.terminal.modules.nft.renew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.adapters.DOMAIN_RENEW_AMOUNT
import cash.p.terminal.core.orHide
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.modules.nft.NftOwnerWalletResolver
import cash.p.terminal.modules.nft.operation.DomainRenewalOperation
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.nft.operation.TonNftOperationRunner
import cash.p.terminal.modules.nft.ui.HIDDEN_NAME
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.fee.feePrimaryText
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.helpers.DateHelper
import java.math.BigDecimal
import java.util.Calendar

private const val RENEW_UNTIL_PATTERN = "d MMMM yyyy"

data class NftDomainRenewUiState(
    val domainNames: List<String>,
    val renewUntil: String,
    val cost: String,
    val fee: String,
    val insufficientBalance: TranslatableString?,
    val canRenew: Boolean,
    val outcome: NftOperationOutcome,
)

/** [namesRevealed]: the user revealed these names on the page that opened the sheet, so hiding does not mask them. */
class NftDomainRenewViewModel(
    domains: List<NftDomainExpiry>,
    private val namesRevealed: Boolean,
    walletResolver: NftOwnerWalletResolver,
    adapterManager: IAdapterManager,
    balanceHiddenManager: IBalanceHiddenManager,
    offlineOperationGate: OfflineOperationGate,
    xRateService: XRateService,
    dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val renewable = domains.filterNot { it.isOnSale }
    private val renewUntil = renewUntilText()
    private val runner = TonNftOperationRunner(
        scope = viewModelScope,
        operation = DomainRenewalOperation,
        targets = renewable.groupBy({ it.owner }, { it.nftAddress }),
        walletResolver = walletResolver,
        adapterManager = adapterManager,
        balanceHiddenManager = balanceHiddenManager,
        offlineOperationGate = offlineOperationGate,
        xRateService = xRateService,
        dispatcherProvider = dispatcherProvider,
    )

    val uiState: NftDomainRenewUiState
        get() {
            val state = runner.state
            val token = state.token
            return NftDomainRenewUiState(
                domainNames = renewable.map { if (state.balanceHidden && !namesRevealed) HIDDEN_NAME else it.name },
                renewUntil = renewUntil,
                cost = feePrimaryText(token, token?.let(::renewalCost)).orHide(state.balanceHidden),
                fee = state.fee,
                insufficientBalance = state.insufficientBalance,
                canRenew = state.canRun,
                outcome = state.outcome,
            )
        }

    /** Call after the PIN gate. */
    fun onRenew() = runner.run()

    private fun renewalCost(token: Token): BigDecimal =
        DOMAIN_RENEW_AMOUNT.toBigDecimal(token.decimals) * renewable.size.toBigDecimal()
}

/** The date a renewal started now runs until. */
internal fun renewUntilText(): String =
    DateHelper.formatDate(Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.time, RENEW_UNTIL_PATTERN)
