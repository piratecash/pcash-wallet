package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.viewModelScope
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamNetwork
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.entities.UsedAddress
import cash.p.terminal.core.factories.uriScheme
import cash.p.terminal.core.paymentUriTokenUid
import cash.p.terminal.core.title
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.core.utils.AddressUriParser
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.receive.ReceiveModule
import cash.p.terminal.modules.receive.ReceiveModule.AdditionalData
import io.horizontalsystems.core.Translator
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.wallet.accountTypeDerivation
import cash.p.terminal.wallet.bitcoinCashCoinType
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.tonAsset
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
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
import co.touchlab.kermit.Logger
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.beam_receive_regular_alert

private val logger = Logger.withTag("ReceiveAddressViewModel")

class ReceiveAddressViewModel internal constructor(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val beamAddressProvider: BeamReceiveAddressProvider? = null,
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
        val alertText: ReceiveModule.AlertText? = null,
        val address: String = "",
        val accountActive: Boolean = true,
    )

    @Volatile
    private var shown = Shown()

    /** Confined to the collector below: it is the only writer, so a plain field is enough. */
    private var freshAddressJob: Job? = null

    /** The collector and [freshAddressJob] both replace [shown]; never interleaved. */
    private val renderMutex = Mutex()

    /** Collapses a burst of Retry taps into the one retry that matters. */
    private val retries = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var beamReceiveType = beamAddressProvider?.let { ReceiveModule.BeamReceiveType.Regular }
    private var beamRequestGeneration = 0L
    private var beamRequest: Job? = null

    init {
        if (beamAddressProvider == null) {
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
        } else {
            viewModelScope.launch {
                beamAddressProvider.changes(wallet.account).collect {
                    requestBeamAddress(checkNotNull(beamReceiveType), beamAddressProvider)
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
            beamReceiveType = beamReceiveType,
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
                addressFormat = wallet.token.title
            }

            else -> {
                blockchainName = wallet.token.blockchain.name
                // A watched address may be any version, while its token is always the plain Native one.
                addressFormat = wallet.token.tonAsset?.version
                    ?.takeIf { wallet.account.type !is AccountType.TonAddress }?.title
            }
        }
        emitState()
    }

    private fun getBeamAlertText(type: BeamAddressType): ReceiveModule.AlertText? =
        if (type == BeamAddressType.Regular) {
            ReceiveModule.AlertText.Normal(Translator.getString(Res.string.beam_receive_regular_alert))
        } else {
            null
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
            shown = Shown(address = fallbackAddress)
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
            logger.e(e) { "Fresh receive address failed" }
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
        shown = Shown(address = address, accountActive = shown.accountActive)
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
            wallet.token.tokenQuery.paymentUriTokenUid?.let { addressUri.parameters[AddressUri.Field.TokenUid] = it }
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
        val provider = beamAddressProvider
        val type = beamReceiveType
        if (provider != null && type != null) {
            requestBeamAddress(type, provider)
            return
        }
        retries.tryEmit(Unit)
    }

    fun onBeamReceiveTypeSelect(type: ReceiveModule.BeamReceiveType) {
        val provider = beamAddressProvider ?: return
        requestBeamAddress(type, provider)
    }

    private fun requestBeamAddress(type: ReceiveModule.BeamReceiveType, provider: BeamReceiveAddressProvider) {
        beamRequest?.cancel()
        val requestGeneration = ++beamRequestGeneration
        beamReceiveType = type
        shown = shown.copy(alertText = getBeamAlertText(type.addressType))
        clearBeamAddress(ViewState.Loading)
        beamRequest = viewModelScope.launch {
            try {
                val received = provider.receiveAddress(wallet.account, type.addressType)
                currentCoroutineContext().ensureActive()
                if (!isCurrentBeamRequest(requestGeneration, type)) return@launch
                check(received.isCurrent()) { "BEAM receive session changed" }
                showBeamAddress(received, type)
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (!isCurrentBeamRequest(requestGeneration, type)) return@launch
                clearBeamAddress(ViewState.Error(error))
            } catch (_: BeamReceivePendingException) {
                if (!isCurrentBeamRequest(requestGeneration, type)) return@launch
                clearBeamAddress(ViewState.Loading)
            } catch (error: Exception) {
                if (!isCurrentBeamRequest(requestGeneration, type)) return@launch
                clearBeamAddress(ViewState.Error(error))
            }
        }
    }

    private fun showBeamAddress(received: BeamReceiveAddress, type: ReceiveModule.BeamReceiveType) {
        val result = received.address
        check(result.type == type.addressType) { "Unexpected BEAM receive address type" }
        val address = if (type == ReceiveModule.BeamReceiveType.Sbbs) {
            checkNotNull(result.sbbsAddress)
        } else {
            result.token
        }
        shown = shown.copy(address = address)
        mainNet = result.network == BeamNetwork.Mainnet
        viewState = ViewState.Success
        emitState()
    }

    private fun isCurrentBeamRequest(generation: Long, type: ReceiveModule.BeamReceiveType) =
        generation == beamRequestGeneration && type == beamReceiveType

    private fun clearBeamAddress(state: ViewState) {
        shown = shown.copy(address = "")
        viewState = state
        emitState()
    }

    fun setAmount(amount: BigDecimal?) {
        // BEAM receive tokens are opaque; a generic amount URI would make the QR invalid.
        if (wallet.token.blockchainType == BlockchainType.Beam) return
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
