package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashSingleUseAddressManager
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.storage.ZcashSingleUseAddressDao
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.entities.ZcashSingleUseAddress
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import java.math.BigDecimal

internal fun buildTestAccount(id: String) = Account(
    id = id,
    name = id,
    type = AccountType.EvmAddress("0x"),
    origin = AccountOrigin.Created,
    level = 0,
)

internal fun buildSwapProviderTransaction(
    provider: SwapProvider,
    transactionId: String,
) = SwapProviderTransaction(
    date = 1_000L,
    outgoingRecordUid = null,
    transactionId = transactionId,
    status = TransactionStatusEnum.NEW.name.lowercase(),
    provider = provider,
    coinUidIn = "coin-in",
    blockchainTypeIn = "ethereum",
    amountIn = BigDecimal.ONE,
    addressIn = "addr-in",
    coinUidOut = "coin-out",
    blockchainTypeOut = "bitcoin",
    amountOut = BigDecimal.TEN,
    addressOut = "addr-out",
    accountId = "test-account",
)

internal fun nativeTestToken(blockchainType: BlockchainType, code: String) = Token(
    coin = Coin(uid = code.lowercase(), name = code, code = code),
    blockchain = Blockchain(blockchainType, code, null),
    type = TokenType.Native,
    decimals = 8,
)

internal fun mockZcashToken(addressSpec: TokenType.AddressSpecType): Token =
    mockk {
        every { type } returns TokenType.AddressSpecTyped(addressSpec)
        every { blockchainType } returns BlockchainType.Zcash
    }

internal fun mockNonZcashNativeToken(blockchainType: BlockchainType = BlockchainType.Ethereum): Token =
    mockk {
        every { this@mockk.blockchainType } returns blockchainType
        every { type } returns TokenType.Native
    }

internal fun buildOffChainSwapProviderSupport(
    walletUseCase: WalletUseCase,
    accountManager: IAccountManager,
    storage: SwapProviderTransactionsStorage,
    marketKit: MarketKitWrapper,
    refundAddressReservation: ZcashRefundAddressReservation =
        buildZcashRefundAddressReservation(walletUseCase, accountManager, marketKit),
) = OffChainSwapProviderSupport(
    walletUseCase = walletUseCase,
    swapProviderTransactionsStorage = storage,
    adapterManager = mockk(relaxed = true),
    swapProviderTransactionFactory = SwapProviderTransactionFactory(walletUseCase, accountManager),
    refundAddressReservation = refundAddressReservation,
)

internal fun buildZcashRefundAddressReservation(
    walletUseCase: WalletUseCase,
    accountManager: IAccountManager,
    marketKit: MarketKitWrapper,
    dao: InMemoryZcashSingleUseAddressDao = InMemoryZcashSingleUseAddressDao(),
) = ZcashRefundAddressReservation(
    walletUseCase = walletUseCase,
    accountManager = accountManager,
    marketKit = marketKit,
    issuedAddresses = buildZcashSingleUseAddressStorage(dao),
)

internal fun buildZcashSingleUseAddressStorage(dao: ZcashSingleUseAddressDao) = ZcashSingleUseAddressStorage(
    router = mockk<StorageRouter> {
        every { account(any()) } returns mockk { every { zcashSingleUseAddressDao() } returns dao }
    },
    dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined)),
)

/** The row ZcashAdapter.generateOneTimeAddress writes on success. */
internal suspend fun InMemoryZcashSingleUseAddressDao.saveIssued(accountId: String, address: String) =
    ZcashSingleUseAddressManager(buildZcashSingleUseAddressStorage(this), accountId).saveNewAddress(address)

/** Stands in for the Room table, so a new reservation over the same instance sees what the last one wrote. */
internal class InMemoryZcashSingleUseAddressDao : ZcashSingleUseAddressDao {
    val rows = mutableListOf<ZcashSingleUseAddress>()

    override suspend fun getUnsentAddress(accountId: String) =
        rows.firstOrNull { it.accountId == accountId && it.useCount == 0 && !it.hadBalance }

    override suspend fun getAddressesWithoutBalance(accountId: String) =
        rows.filter { it.accountId == accountId && !it.hadBalance }

    override suspend fun getAllAddresses(accountId: String) = rows.filter { it.accountId == accountId }

    override suspend fun insert(address: ZcashSingleUseAddress) {
        rows.removeAll { it.accountId == address.accountId && it.address == address.address }
        rows += address
    }

    override suspend fun incrementUseCount(accountId: String, address: String) =
        update(accountId, address) { it.copy(useCount = it.useCount + 1) }

    override suspend fun updateHadBalance(accountId: String, address: String, hadBalance: Boolean) =
        update(accountId, address) { it.copy(hadBalance = hadBalance) }

    override suspend fun deleteByAccount(accountId: String) {
        rows.removeAll { it.accountId == accountId }
    }

    private fun update(accountId: String, address: String, change: (ZcashSingleUseAddress) -> ZcashSingleUseAddress) {
        rows.replaceAll { if (it.accountId == accountId && it.address == address) change(it) else it }
    }
}

internal fun MarketKitWrapper.stubZcashTransparentToken(token: Token) {
    every {
        this@stubZcashTransparentToken.token(match {
            it.blockchainType == BlockchainType.Zcash &&
                it.tokenType == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent)
        })
    } returns token
}

internal data class JettonSwapParties(val responseDestination: AddrStd, val receiver: AddrStd)

/** Reads who gets the excess TON and who gets the swap output from a STON.fi jetton swap transfer. */
internal fun Cell.stonFiJettonSwapParties(dexVersion: Int): JettonSwapParties {
    val transfer = beginParse()
    transfer.loadUInt(OPCODE_BITS)
    transfer.loadUInt(QUERY_ID_BITS)
    transfer.loadCoins()
    transfer.loadAddress()
    val responseDestination = transfer.loadAddress()
    transfer.loadBit()
    transfer.loadCoins()
    transfer.loadBit()
    val swap = transfer.loadRef().beginParse()
    swap.loadUInt(OPCODE_BITS)
    val receiver = if (dexVersion == 1) {
        swap.loadAddress()
        swap.loadCoins()
        swap.loadAddress()
    } else {
        repeat(3) { swap.loadAddress() }
        swap.loadUInt(DEADLINE_BITS)
        val crossSwap = swap.loadRef().beginParse()
        crossSwap.loadCoins()
        crossSwap.loadAddress()
    }
    return JettonSwapParties(responseDestination, receiver)
}

private fun CellSlice.loadCoins() = loadUInt(loadUInt(COINS_LENGTH_BITS).toInt() * Byte.SIZE_BITS)

private fun CellSlice.loadAddress(): AddrStd {
    loadUInt(ADDRESS_TAG_BITS)
    loadBit()
    val workchainId = loadInt(Byte.SIZE_BITS).toInt()
    return AddrStd(workchainId, loadBits(ADDRESS_BITS))
}

private const val OPCODE_BITS = 32
private const val QUERY_ID_BITS = 64
private const val DEADLINE_BITS = 64
private const val COINS_LENGTH_BITS = 4
private const val ADDRESS_TAG_BITS = 2
private const val ADDRESS_BITS = 256
