package cash.p.terminal.modules.watchaddress

import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.adapters.zcash.ZcashAddressDeriver
import cash.p.terminal.core.adapters.zcash.ZcashKey
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.BitcoinAddress
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.entities.tokenType
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.address.ZcashKeyParser
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.modules.enablecoin.restoresettings.zcashBirthdayHeight
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class WatchAddressViewModel(
    private val watchAddressService: WatchAddressService,
    private val addressParserChain: AddressParserChain
) : ViewModelUiState<WatchAddressUiState>() {

    private val zcashAddressDeriver: ZcashAddressDeriver by inject(ZcashAddressDeriver::class.java)
    private val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)
    private val zcashBirthdayProvider: ZcashBirthdayProvider by inject(ZcashBirthdayProvider::class.java)

    private var accountCreated = false
    private var submitButtonType: SubmitButtonType = SubmitButtonType.Next(false)
    private var type = Type.Unsupported
    private var address: Address? = null
    private var xPubKey: String? = null
    private var zcashKey: String? = null
    private var accountType: AccountType? = null
    private var accountNameEdited = false
    private var inputState: DataState<String>? = null
    private var parseAddressJob: Job? = null
    private var zcashHeightRequested = false

    var enteredInput: String = ""
        private set

    val defaultAccountName = watchAddressService.nextWatchAccountName()
    var accountName: String = defaultAccountName
        get() = field.ifBlank { defaultAccountName }
        private set

    override fun createState() = WatchAddressUiState(
        accountCreated = accountCreated,
        submitButtonType = submitButtonType,
        accountType = accountType,
        accountName = accountName,
        inputState = inputState,
        zcashHeightRequested = zcashHeightRequested
    )

    fun onEnterAccountName(v: String) {
        accountNameEdited = v.isNotBlank()
        accountName = v
    }

    fun onEnterInput(v: String) {
        enteredInput = v
        parseAddressJob?.cancel()
        address = null
        xPubKey = null
        zcashKey = null

        if (v.isBlank()) {
            inputState = null
            accountName = defaultAccountName
            syncSubmitButtonType()
            emitState()
        } else {
            inputState = DataState.Loading
            syncSubmitButtonType()
            emitState()

            val vTrimmed = v.trim()
            parseAddressJob = viewModelScope.launch(dispatcherProvider.io) {
                val handler = addressParserChain.supportedHandler(vTrimmed)

                if (handler == null) {
                    ensureActive()
                    if (parseZcashKey(vTrimmed)) {
                        return@launch
                    }
                    withContext(dispatcherProvider.main) {
                        setXPubKey(vTrimmed)
                    }
                    return@launch
                } else {
                    try {
                        val parsedAddress = handler.parseAddress(vTrimmed)
                        ensureActive()
                        withContext(dispatcherProvider.main) {
                            setAddress(parsedAddress)
                        }
                    } catch (t: Throwable) {
                        ensureActive()
                        withContext(dispatcherProvider.main) {
                            inputState = DataState.Error(t)
                            syncSubmitButtonType()
                            emitState()
                        }
                    }
                }
            }
        }
    }

    private fun setAddress(address: Address) {
        this.address = address
        if (!accountNameEdited) {
            accountName = address.domain ?: defaultAccountName
        }

        type = addressType(address)
        inputState = DataState.Success(address.hex)

        syncSubmitButtonType()
        emitState()
    }

    /** Returns true when the input is a Zcash key — the screen state is then already set for it. */
    private suspend fun parseZcashKey(input: String): Boolean {
        if (ZcashKeyParser.isSaplingSpendingKey(input)) {
            setKeyError(PrivateKeyNotWatchable)
            return true
        }

        val keyType = when {
            ZcashKeyParser.isUfvk(input) -> Type.ZcashUfvk
            ZcashKeyParser.isSaplingViewingKey(input) -> Type.ZcashSaplingVk
            else -> return false
        }

        val addresses = tryOrNull { zcashAddressDeriver.addresses(ZcashKey.ViewingKey(input)) }
        // tryOrNull also swallows CancellationException, so the job's state is re-checked here.
        currentCoroutineContext().ensureActive()

        if (addresses == null) {
            setKeyError(UnsupportedAddress)
            return true
        }

        zcashKey = input
        type = keyType
        inputState = DataState.Success(input)
        withContext(dispatcherProvider.main) {
            syncSubmitButtonType()
            emitState()
        }
        return true
    }

    private suspend fun setKeyError(error: Throwable) = withContext(dispatcherProvider.main) {
        inputState = DataState.Error(error)
        type = Type.Unsupported
        syncSubmitButtonType()
        emitState()
    }

    private fun setXPubKey(input: String) {
        val hdKey = tryOrNull { HDExtendedKey(input) }

        when {
            hdKey == null -> {
                xPubKey = null
                inputState = DataState.Error(UnsupportedAddress)
                type = Type.Unsupported
            }

            hdKey.isPublic -> {
                xPubKey = input
                inputState = DataState.Success(input)
                type = Type.XPubKey
            }

            else -> {
                xPubKey = null
                inputState = DataState.Error(PrivateKeyNotWatchable)
                type = Type.Unsupported
            }
        }

        syncSubmitButtonType()
        emitState()
    }

    private fun addressType(address: Address) = when (address.blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dogecoin,
        BlockchainType.Cosanta,
        BlockchainType.PirateCash,
        BlockchainType.Dash -> Type.BitcoinAddress

        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
        BlockchainType.ArbitrumOne,
        BlockchainType.Gnosis,
        BlockchainType.Fantom -> Type.EvmAddress

        BlockchainType.Solana -> Type.SolanaAddress
        BlockchainType.Tron -> Type.TronAddress
        BlockchainType.Ton -> Type.TonAddress
        BlockchainType.Stellar -> Type.StellarAddress

        BlockchainType.Zcash,
        BlockchainType.Monero,
        is BlockchainType.Unsupported,
        null -> Type.Unsupported
    }

    fun blockchainSelectionOpened() {
        accountType = null

        emitState()
    }

    fun onClickNext() {
        accountType = getAccountType()

        emitState()
    }

    fun onClickWatch() {
        if (zcashHeightRequested) return

        if (type == Type.ZcashUfvk || type == Type.ZcashSaplingVk) {
            zcashHeightRequested = true
            emitState()
            return
        }

        createWatchAccount(zcashBirthdayHeight = null)
    }

    fun zcashHeightRequestOpened() {
        zcashHeightRequested = false

        emitState()
    }

    fun onZcashHeightEntered(config: TokenConfig?) {
        if (config == null) return

        createWatchAccount(config.zcashBirthdayHeight(zcashBirthdayProvider))
    }

    private fun createWatchAccount(zcashBirthdayHeight: Long?) {
        try {
            val accountType = getAccountType() ?: throw Exception()

            watchAddressService.watchAll(accountType, accountName, zcashBirthdayHeight)

            accountCreated = true
            emitState()
        } catch (_: Exception) {

        }
    }

    private fun syncSubmitButtonType() {
        submitButtonType = when (type) {
            Type.EvmAddress -> SubmitButtonType.Next(address != null)
            Type.XPubKey -> SubmitButtonType.Next(xPubKey != null)
            Type.SolanaAddress -> SubmitButtonType.Watch(address != null)
            Type.TronAddress -> SubmitButtonType.Watch(address != null)
            Type.BitcoinAddress -> SubmitButtonType.Watch(address != null)
            Type.TonAddress -> SubmitButtonType.Watch(address != null)
            Type.StellarAddress -> SubmitButtonType.Watch(address != null)
            Type.ZcashUfvk,
            Type.ZcashSaplingVk -> SubmitButtonType.Watch(zcashKey != null)
            Type.Unsupported -> SubmitButtonType.Watch(false)
        }
    }

    private fun getAccountType() = when (type) {
        Type.EvmAddress -> address?.let { AccountType.EvmAddress(it.hex) }
        Type.SolanaAddress -> address?.let { AccountType.SolanaAddress(it.hex) }
        Type.TronAddress -> address?.let { AccountType.TronAddress(it.hex) }
        Type.XPubKey -> xPubKey?.let { AccountType.HdExtendedKey(it) }
        Type.ZcashUfvk -> zcashKey?.let { AccountType.ZCashUfvKey(it) }
        Type.ZcashSaplingVk -> zcashKey?.let { AccountType.ZCashSaplingKey(it) }
        Type.BitcoinAddress -> address?.let {
            if (it is BitcoinAddress) {
                AccountType.BitcoinAddress(
                    address = it.hex,
                    blockchainType = it.blockchainType!!,
                    tokenType = it.tokenType
                )
            } else {
                throw IllegalStateException("Unsupported address type")
            }
        }

        Type.TonAddress -> address?.let {
            AccountType.TonAddress(it.hex)
        }

        Type.StellarAddress -> address?.let {
            AccountType.StellarAddress(it.hex)
        }

        Type.Unsupported -> throw IllegalStateException("Unsupported address type")
    }

    enum class Type {
        EvmAddress,
        TronAddress,
        SolanaAddress,
        XPubKey,
        BitcoinAddress,
        TonAddress,
        StellarAddress,
        Unsupported,
        ZcashUfvk,
        ZcashSaplingVk
    }
}

data class WatchAddressUiState(
    val accountCreated: Boolean,
    val submitButtonType: SubmitButtonType,
    val accountType: AccountType?,
    val accountName: String?,
    val inputState: DataState<String>?,
    val zcashHeightRequested: Boolean
)

sealed class SubmitButtonType {
    data class Watch(val enabled: Boolean) : SubmitButtonType()
    data class Next(val enabled: Boolean) : SubmitButtonType()
}

object UnsupportedAddress :
    Exception(Translator.getString(R.string.Watch_Error_InvalidAddressFormat))

object PrivateKeyNotWatchable :
    Exception(Translator.getString(R.string.watch_address_is_private_key))
