package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.viewModelScope
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamNetwork
import cash.p.terminal.R
import cash.p.terminal.wallet.IAdapterManager
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
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

class ReceiveAddressViewModel internal constructor(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val beamAddressProvider: BeamReceiveAddressProvider? = null,
) : ViewModelUiState<ReceiveModule.UiState>() {

    private var viewState: ViewState = ViewState.Loading
    private var address = ""
    private var usedAddresses: List<UsedAddress> = listOf()
    private var usedChangeAddresses: List<UsedAddress> = listOf()
    private var isAddressHistorySupported = false
    private var uri = ""
    private var amount: BigDecimal? = null
    private var accountActive = true
    private var blockchainName: String? = null
    private var addressFormat: String? = null
    private var mainNet = true
    private var watchAccount = wallet.account.isWatchAccount
    private var alertText: ReceiveModule.AlertText? = getAlertText(watchAccount)
    private var beamAddressType = beamAddressProvider?.let { BeamAddressType.PublicOffline }
    private var beamRequestGeneration = 0L
    private var beamRequest: Job? = null

    init {
        if (beamAddressProvider == null) {
            viewModelScope.launch(dispatcherProvider.io) {
                // One collector: both triggers fire on the same init, and concurrent
                // setData() would race two isAddressActive requests against each other.
                merge(
                    adapterManager.adaptersReadyObservable.asFlow(),
                    adapterManager.initializationInProgressFlow,
                ).collect {
                    setData()
                }
            }
        } else {
            viewModelScope.launch {
                beamAddressProvider.changes(wallet.account).collect {
                    requestBeamAddress(checkNotNull(beamAddressType), beamAddressProvider)
                }
            }
        }
        setNetworkName()
    }

    override fun createState() = ReceiveModule.UiState(
        viewState = viewState,
        address = address,
        usedAddresses = usedAddresses,
        usedChangeAddresses = usedChangeAddresses,
        isAddressHistorySupported = isAddressHistorySupported,
        showTronAlert = !accountActive,
        beamAddressType = beamAddressType,
        uri = uri,
        watchAccount = watchAccount,
        additionalItems = getAdditionalData(),
        amount = amount,
        alertText = alertText,
        mainNet = mainNet,
        blockchainName = blockchainName,
        addressFormat = addressFormat,
    )

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

    private suspend fun setData() {
        val adapter = adapterManager.getReceiveAdapterForWallet(wallet)
        if (adapter != null) {
            address = adapter.receiveAddress
            usedAddresses = adapter.usedAddresses(false)
            usedChangeAddresses = adapter.usedAddresses(true)
            isAddressHistorySupported = adapter.isAddressHistorySupported
            uri = getUri()
            mainNet = adapter.isMainNet
            viewState = ViewState.Success

            // Unknown activation state must not hide a valid address behind the TRON warning
            accountActive = tryOrNull { adapter.isAddressActive(adapter.receiveAddress) } ?: true
        } else {
            val fallbackAddress = adapterManager.getReceiveAddressForWallet(wallet)
            if (fallbackAddress != null) {
                address = fallbackAddress
                uri = getUri()
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
    }

    private fun getUri(): String {
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

    private fun getAdditionalData(): List<AdditionalData> {
        val items = mutableListOf<AdditionalData>()

        if (!accountActive) {
            items.add(AdditionalData.AccountNotActive)
        }

        return items
    }

    fun onErrorClick() {
        val provider = beamAddressProvider
        val type = beamAddressType
        if (provider != null && type != null) {
            requestBeamAddress(type, provider)
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            setData()
        }
    }

    fun onBeamAddressTypeSelect(type: BeamAddressType) {
        val provider = beamAddressProvider ?: return
        requestBeamAddress(type, provider)
    }

    private fun requestBeamAddress(type: BeamAddressType, provider: BeamReceiveAddressProvider) {
        beamRequest?.cancel()
        val requestGeneration = ++beamRequestGeneration
        beamAddressType = type
        clearBeamAddress(ViewState.Loading)
        beamRequest = viewModelScope.launch {
            try {
                val received = provider.receiveAddress(wallet.account, type)
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

    private fun showBeamAddress(received: BeamReceiveAddress, type: BeamAddressType) {
        val result = received.address
        check(result.type == type) { "Unexpected BEAM receive address type" }
        address = result.token
        uri = result.token
        mainNet = result.network == BeamNetwork.Mainnet
        viewState = ViewState.Success
        emitState()
    }

    private fun isCurrentBeamRequest(generation: Long, type: BeamAddressType) =
        generation == beamRequestGeneration && type == beamAddressType

    private fun clearBeamAddress(state: ViewState) {
        address = ""
        uri = ""
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
        uri = getUri()
        emitState()
    }

}
