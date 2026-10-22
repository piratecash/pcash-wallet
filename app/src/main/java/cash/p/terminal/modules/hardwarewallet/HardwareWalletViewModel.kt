package cash.p.terminal.modules.hardwarewallet

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.usecase.CreateHardwareWalletUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.core.usecase.RescanZcashUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.tangem.domain.TangemConfig
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.tangem.ui.HardwareWalletError
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import co.touchlab.kermit.Logger
import com.tangem.common.doOnFailure
import com.tangem.common.doOnSuccess
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal enum class RestoreHeightChain { Zcash, Monero }

internal class HardwareWalletViewModel(
    private val tangemScanUseCase: TangemScanUseCase,
    private val createHardwareWalletUseCase: CreateHardwareWalletUseCase,
    private val accountManager: IAccountManager,
    private val getMoneroWalletFilesName: IGetMoneroWalletFilesNameUseCase,
    private val rescanMonero: RescanMoneroUseCase,
    private val rescanZcash: RescanZcashUseCase,
    private val walletManager: IWalletManager,
    accountFactory: IAccountFactory,
) : ViewModel() {
    val defaultAccountName = accountFactory.getNextHardwareAccountName()
    var accountName: String = defaultAccountName
        get() = field.ifBlank { defaultAccountName }
        private set

    var success by mutableStateOf<Boolean>(false)

    private val _errorEvents = Channel<HardwareWalletError>(capacity = 1)
    val errorEvents = _errorEvents.receiveAsFlow()

    private val _restoreHeightRequests = Channel<RestoreHeightChain>(capacity = 1)
    val restoreHeightRequests = _restoreHeightRequests.receiveAsFlow()
    private val pendingRestoreHeightChains = ArrayDeque<RestoreHeightChain>()
    private var restoreHeightAccount: Account? = null

    // A hardware wallet is almost always an existing wallet, so each height-bearing chain asks where it starts.
    fun onHardwareSetupFinished(created: Boolean) = viewModelScope.launch {
        if (!created) return@launch
        val account = accountManager.activeAccount?.takeIf { it.type is AccountType.HardwareDevice }
        restoreHeightAccount = account
        pendingRestoreHeightChains.clear()
        account?.let { pendingRestoreHeightChains.addAll(restoreHeightChains(it)) }
        requestNextRestoreHeightOrFinish()
    }

    // The next question does not wait for the rescan; UNDISPATCHED enters its NonCancellable part at once,
    // so closing the screen cannot abort it.
    fun onRestoreHeightConfigured(chain: RestoreHeightChain, config: TokenConfig?) {
        restoreHeightAccount?.let { account ->
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { applyRestoreHeight(account, chain, config) }
        }
        requestNextRestoreHeightOrFinish()
    }

    private suspend fun restoreHeightChains(account: Account) = buildList {
        if (walletManager.getWallets(account).any { it.token.blockchainType == BlockchainType.Zcash }) {
            add(RestoreHeightChain.Zcash)
        }
        // Trezor Safe 5 asks the Monero height inline during its setup.
        if (account.type is AccountType.LedgerDevice && getMoneroWalletFilesName(account) != null) {
            add(RestoreHeightChain.Monero)
        }
    }

    private fun requestNextRestoreHeightOrFinish() {
        val next = pendingRestoreHeightChains.removeFirstOrNull()
        if (next == null) success = true else _restoreHeightRequests.trySend(next)
    }

    // "New wallet" keeps the Zcash setup checkpoint, which is never above the tip, so no erase is needed.
    private suspend fun applyRestoreHeight(account: Account, chain: RestoreHeightChain, config: TokenConfig?) {
        try {
            when (chain) {
                RestoreHeightChain.Zcash ->
                    config?.takeUnless { it.restoreAsNew }?.restoreHeight()?.let { rescanZcash(account, it) }

                RestoreHeightChain.Monero -> config?.restoreHeight()?.let { rescanMonero(account, it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to apply the $chain restore height" }
        }
    }

    private fun TokenConfig.restoreHeight() = birthdayHeight?.toLongOrNull()?.takeIf { it >= 0 }

    fun scanCard() = viewModelScope.launch {
        tangemScanUseCase.scanProduct(TangemConfig.getDefaultTokens)
            .doOnSuccess { scanResponse ->
                if (scanResponse.card.isAccessCodeSet && scanResponse.card.wallets.isNotEmpty()) {
                    // Card already activated, need to add tokens
                    createHardwareWalletUseCase(accountName, scanResponse)
                    success = true
                } else {
                    _errorEvents.trySend(HardwareWalletError.CardNotActivated)
                }
            }
            .doOnFailure {
                Log.d("CreateAccountViewModel", "Error scanning card: ${it.customMessage}")
            }
    }

    fun onChangeAccountName(name: String) {
        accountName = name
    }

    private companion object {
        val logger = Logger.withTag("HardwareWalletViewModel")
    }
}
