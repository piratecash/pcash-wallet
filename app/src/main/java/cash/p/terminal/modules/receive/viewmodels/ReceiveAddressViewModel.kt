package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.entities.UsedAddress
import cash.p.terminal.core.factories.uriScheme
import cash.p.terminal.core.title
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.receive.ReceiveModule
import cash.p.terminal.modules.receive.ReceiveModule.AdditionalData
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.math.BigDecimal

class ReceiveAddressViewModel(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModelUiState<ReceiveModule.UiState>() {

    @Volatile
    private var viewState: ViewState = ViewState.Loading
    private var usedAddresses: List<UsedAddress> = listOf()
    private var usedChangeAddresses: List<UsedAddress> = listOf()
    private var isAddressHistorySupported = false
    private var amount: BigDecimal? = null
    private var blockchainName: String? = null
    private var addressFormat: String? = null
    private var mainNet = true
    private var watchAccount = wallet.account.isWatchAccount

    /** The address and what must be published with it; replaced whole, so no frame can mix two answers. */
    private data class Shown(
        val alertText: ReceiveModule.AlertText?,
        val address: String = "",
        val accountActive: Boolean = true,
    )

    @Volatile
    private var shown = Shown(alertText = getAlertText(watchAccount))

    /** Confined to the collector below: it is the only writer, so a plain field is enough. */
    private var freshAddressJob: Job? = null

    /** The collector and [freshAddressJob] both replace [shown]; never interleaved. */
    private val renderMutex = Mutex()

    /** Collapses a burst of Retry taps into the one retry that matters. */
    private val retries = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            // One collector: every trigger fires on the same init, and concurrent
            // setData() would race two isAddressActive requests against each other.
            merge(
                adapterManager.adaptersReadyObservable.asFlow(),
                adapterManager.initializationInProgressFlow,
                retries,
            ).collect {
                val adapter = setData()
                freshAddressJob?.cancel()
                freshAddressJob = adapter?.let { a ->
                    launch { a.freshReceiveAddressChanges.collect { loadFreshReceiveAddress(a) } }
                }
            }
        }
        setNetworkName()
    }

    override fun createState(): ReceiveModule.UiState {
        // Success is written only after a non-empty tuple, so reading it first can never pair
        // Success with an empty address (the reverse order could).
        val viewState = viewState
        val shown = shown
        return ReceiveModule.UiState(
            viewState = viewState,
            address = shown.address,
            usedAddresses = usedAddresses,
            usedChangeAddresses = usedChangeAddresses,
            isAddressHistorySupported = isAddressHistorySupported,
            showTronAlert = !shown.accountActive,
            uri = getUri(shown.address),
            watchAccount = watchAccount,
            additionalItems = getAdditionalData(shown.accountActive),
            amount = amount,
            alertText = shown.alertText,
        mainNet = mainNet,
            blockchainName = blockchainName,
            addressFormat = addressFormat,
        )
    }

    private fun setNetworkName() {
        when (val tokenType = wallet.token.type) {
            is TokenType.Derived -> {
                val derivation = tokenType.derivation.accountTypeDerivation
                addressFormat = "${derivation.addressType} (${derivation.rawName})"
            }

            is TokenType.AddressTyped -> {
                addressFormat = tokenType.type.bitcoinCashCoinType.title
            }

            TokenType.Mweb -> {
                addressFormat = tokenType.title
            }

            else -> {
                blockchainName = wallet.token.blockchain.name
            }
        }
        emitState()
    }

    private fun getAlertText(watchAccount: Boolean): ReceiveModule.AlertText? {
        return if (watchAccount) ReceiveModule.AlertText.Normal(
            Translator.getString(R.string.Balance_Receive_WatchAddressAlert)
        )
        else null
    }

    /**
     * Renders twice: the first frame never touches the SDK, so a slow fresh-address lookup cannot
     * delay it. Returns the resolved adapter so the caller can (re)start the job that fetches the
     * verified answer; `null` when there is none to subscribe to.
     */
    private suspend fun setData(): IReceiveAdapter? = try {
        setDataOrThrow()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        viewState = ViewState.Error(e)
        emitState()
        null
    }

    private suspend fun setDataOrThrow(): IReceiveAdapter? {
        val adapter = adapterManager.getReceiveAdapterForWallet(wallet)
        if (adapter != null) {
            usedAddresses = adapter.usedAddresses(false)
            usedChangeAddresses = adapter.usedAddresses(true)
            isAddressHistorySupported = adapter.isAddressHistorySupported
            mainNet = adapter.isMainNet
            applyFreshAddress(adapter, adapter.receiveAddress, checkActivation = true)
        } else {
            val fallbackAddress = adapterManager.getReceiveAddressForWallet(wallet)
            if (fallbackAddress != null) {
                showFallback(fallbackAddress)
                viewState = ViewState.Success
            } else {
                viewState = if (adapterManager.initializationInProgressFlow.value) {
                    ViewState.Loading
                } else {
                    ViewState.Error(NullPointerException())
                }
            }
        }
        emitState()
        return adapter
    }

    /** Only fills an empty screen: an adapter's answer already shown outranks a derived address. */
    private suspend fun showFallback(fallbackAddress: String) = renderMutex.withLock {
        if (shown.address.isEmpty()) {
            shown = Shown(alertText = getAlertText(watchAccount), address = fallbackAddress)
        }
    }

    /**
     * The job [freshAddressJob] runs: fetches the verified answer and re-emits it in place. A
     * failure here leaves the already-rendered (disclosed) address standing rather than turning it
     * into an error page.
     */
    private suspend fun loadFreshReceiveAddress(adapter: IReceiveAdapter) {
        try {
            val fresh = adapter.freshReceiveAddress()
            applyFreshAddress(adapter, fresh, checkActivation = fresh != adapter.receiveAddress)
            emitState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e)
        }
    }

    /**
     * Puts the address on screen, then marks the screen ready, then probes activation — the probe
     * is the only suspension point, so no frame can show Success without its address.
     * [checkActivation] is false only for an address the first frame of this render already probed.
     */
    private suspend fun applyFreshAddress(
        adapter: IReceiveAdapter,
        address: String,
        checkActivation: Boolean,
    ) = renderMutex.withLock {
        // A cancelled fresh-address job must not publish over the render that replaced it.
        currentCoroutineContext().ensureActive()
        shown = Shown(getAlertText(watchAccount), address, shown.accountActive)
        viewState = ViewState.Success
        if (checkActivation) {
            // Unknown activation state must not hide a valid address behind the TRON warning
            val accountActive = tryOrNull { adapter.isAddressActive(address) } ?: true
            shown = shown.copy(accountActive = accountActive)
        }
    }

    private fun getUri(address: String): String {
        var newUri = address
        amount?.let {
            val parser = AddressUriParser(wallet.token.blockchainType, wallet.token.type)
            val addressUri = AddressUri(wallet.token.blockchainType.uriScheme ?: "")
            addressUri.address = newUri
            addressUri.parameters[AddressUri.Field.amountField(wallet.token.blockchainType)] = it.toString()
            addressUri.parameters[AddressUri.Field.BlockchainUid] = wallet.token.blockchainType.uid
            if (wallet.token.type !is TokenType.Derived && wallet.token.type !is TokenType.AddressTyped) {
                addressUri.parameters[AddressUri.Field.TokenUid] = wallet.token.type.id
            }
            newUri = parser.uri(addressUri)
        }

        return newUri
    }

    private fun getAdditionalData(accountActive: Boolean): List<AdditionalData> {
        val items = mutableListOf<AdditionalData>()

        if (!accountActive) {
            items.add(AdditionalData.AccountNotActive)
        }

        return items
    }

    fun onErrorClick() {
        retries.tryEmit(Unit)
    }

    fun setAmount(amount: BigDecimal?) {
        amount?.let {
            if (it <= BigDecimal.ZERO) {
                this.amount = null
                emitState()
                return
            }
        }
        this.amount = amount
        emitState()
    }

}
