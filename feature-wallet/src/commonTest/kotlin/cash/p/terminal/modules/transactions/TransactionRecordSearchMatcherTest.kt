package cash.p.terminal.modules.transactions

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.adapters.BitcoinReplacementLink
import cash.p.terminal.core.adapters.BitcoinReplacementRegistry
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.IncomingSwapMatch
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture
import cash.p.terminal.core.managers.localizedEvmMethodLabel
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_Approve
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.Transactions_Receive
import cash.p.terminal.resources.Transactions_Send
import cash.p.terminal.resources.Transactions_Swap
import cash.p.terminal.resources.transaction_swap_status_confirming
import cash.p.terminal.resources.transaction_swap_status_created_or_wait_user
import cash.p.terminal.resources.transaction_swap_status_exchanging
import cash.p.terminal.resources.transaction_swap_status_new
import cash.p.terminal.resources.transaction_swap_status_refunded
import cash.p.terminal.resources.transaction_swap_status_sending
import cash.p.terminal.resources.transaction_swap_status_unknown
import cash.p.terminal.resources.transaction_swap_status_verifying
import cash.p.terminal.resources.transaction_swap_status_waiting
import cash.p.terminal.resources.transactions_liquidity_add_failed
import cash.p.terminal.resources.transactions_liquidity_removed
import cash.p.terminal.resources.transactions_liquidity_search_key
import cash.p.terminal.resources.transactions_swap_failed
import io.horizontalsystems.core.Translator
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import io.horizontalsystems.ethereumkit.models.Transaction as EvmTransaction

/** Search over a wallet without provider swaps, for tests that do not exercise provider titles. */
internal fun testSearchMatcher(
    swapStorage: SwapProviderTransactionsStorage = mockk(relaxed = true),
) = TransactionRecordSearchMatcher(testSwapProviderRecordResolver(swapStorage), swapStorage)

internal fun testSearchVisibility(
    swapStorage: SwapProviderTransactionsStorage = mockk(relaxed = true),
    spamManager: SpamManager = mockk {
        every { shouldHide(any()) } returns false
        every { hideSuspiciousTx } returns true
    },
) = TransactionSearchVisibility(spamManager, swapStorage, testSwapProviderRecordResolver(swapStorage))

private fun testSwapProviderRecordResolver(swapStorage: SwapProviderTransactionsStorage) =
    SwapProviderRecordResolver(swapStorage, mockk { every { findMatch(any()) } returns null })

internal fun testProviderSwap(status: TransactionStatusEnum, outgoingRecordUid: String?) = SwapProviderTransaction(
    date = 1_000L,
    outgoingRecordUid = outgoingRecordUid,
    transactionId = "provider-order",
    status = status.name.lowercase(),
    provider = SwapProvider.CHANGENOW,
    coinUidIn = "binancecoin",
    blockchainTypeIn = "binance-smart-chain",
    amountIn = BigDecimal.ONE,
    addressIn = "addr-in",
    coinUidOut = "litecoin",
    blockchainTypeOut = "litecoin",
    amountOut = BigDecimal.ONE,
    addressOut = "addr-out",
    accountId = "account",
)

private val RUSSIAN_TITLES = mapOf(
    Res.string.Transactions_Send to "Отправлено",
    Res.string.Transactions_Receive to "Получено",
    Res.string.Transactions_Approve to "Разрешено",
    Res.string.Transactions_Swap to "Обмен выполнен",
    Res.string.transactions_swap_failed to "Обмен не выполнен",
    Res.string.Transactions_Failed to "Не удалось",
    Res.string.transaction_swap_status_new to "Новая",
    Res.string.transaction_swap_status_waiting to "В ожидании",
    Res.string.transaction_swap_status_confirming to "Подтверждается",
    Res.string.transaction_swap_status_exchanging to "Процесс обмена",
    Res.string.transaction_swap_status_sending to "Ожидание отправки",
    Res.string.transaction_swap_status_refunded to "Средства возвращены",
    Res.string.transaction_swap_status_verifying to "Верификация пользователя",
    Res.string.transaction_swap_status_created_or_wait_user to "Ожидание выбора банка",
    Res.string.transaction_swap_status_unknown to "Неизвестный статус",
)

/** Row titles as the Russian UI shows them; other strings resolve to their resource key. */
internal fun mockRussianTitles() {
    mockkObject(Translator)
    every { Translator.getString(any(), *anyVararg()) } answers { firstArg<StringResource>().key }
    RUSSIAN_TITLES.forEach { (resource, title) -> every { Translator.getString(resource) } returns title }
}

class TransactionRecordSearchMatcherTest {

    private var storedSwaps = emptyList<SwapProviderTransaction>()
    private val swapStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { getAllAcrossAccounts() } answers { storedSwaps }
        every { getByOutgoingRecordUid(any()) } answers {
            storedSwaps.find { it.outgoingRecordUid == firstArg<String>() }
        }
        every { getByTokenIn(any(), any(), any()) } returns null
    }
    private val swapTransactionMatcher = mockk<SwapTransactionMatcher> {
        every { findMatch(any()) } returns null
    }
    private val matcher = TransactionRecordSearchMatcher(
        SwapProviderRecordResolver(swapStorage, swapTransactionMatcher),
        swapStorage,
    )
    private val thorchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val rune = Token(Coin("thorchain", "THORChain", "RUNE"), thorchain, TokenType.Native, 8)

    private fun thorchainRecord(type: ThorchainTransactionRecord.Type) = ThorchainTransactionRecord(
        uid = "uid",
        transaction = mockk<Transaction>(relaxed = true) {
            every { hash } returns "HASH"
            every { memo } returns MEMO
        },
        spam = false,
        source = TransactionSource(thorchain, mockk(relaxed = true), null),
        token = rune,
        type = type,
        fee = null,
    )

    @Test
    fun matches_thorchainOutgoing_findsMemoAndRecipient() {
        val record = thorchainRecord(
            ThorchainTransactionRecord.Type.Outgoing(
                TransactionValue.CoinValue(rune, BigDecimal.ONE.negate()),
                to = RECIPIENT,
                sentToSelf = false,
            )
        )

        assertTrue(matches(record, "=:eth.eth"))
        assertTrue(matches(record, RECIPIENT.uppercase()))
        assertFalse(matches(record, "unrelated"))
    }

    @Test
    fun matches_thorchainIncoming_findsSender() {
        val record = thorchainRecord(
            ThorchainTransactionRecord.Type.Incoming(TransactionValue.CoinValue(rune, BigDecimal.ONE), from = SENDER)
        )

        assertTrue(matches(record, SENDER))
    }

    @Test
    fun matches_evmContractCall_findsRawAndLocalizedMethod() {
        val bsc = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
        val bnb = Token(Coin("bsc", "BNB Smart Chain", "BNB"), bsc, TokenType.Native, 18)
        val record = EvmTransactionRecord(
            transaction = mockk<EvmTransaction>(relaxed = true) {
                every { hashString } returns "HASH"
            },
            token = bnb,
            source = TransactionSource(bsc, mockk(relaxed = true), null),
            protected = false,
            transactionRecordType = TransactionRecordType.EVM,
            method = "Multicall",
        )

        assertTrue(matches(record, "multicall"))
        assertTrue(matches(record, localizedEvmMethodLabel("Multicall")))
        assertFalse(matches(record, "unrelated"))
    }

    @Test
    fun matches_solanaNftTransfer_findsMintAddress() {
        val coinManager = mockk<ICoinManager>(relaxed = true) {
            every { getToken(any()) } returns null
        }
        val record = SolanaTransactionConverterTestFixture.createConverter(coinManager).transactionRecord(
            SolanaTransactionConverterTestFixture.tokenTransferTransaction(
                hash = "HASH",
                from = SENDER,
                to = SolanaTransactionConverterTestFixture.USER_ADDRESS,
                mint = NFT_MINT,
                incoming = true,
                amount = BigDecimal.ONE,
                decimals = 0,
                isNft = true,
            )
        )

        assertTrue(matches(record, NFT_MINT))
    }

    @Before
    fun setUp() {
        mockRussianTitles()
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun match_dexSwap_findsSwapTitle() {
        assertTrue(matches(evmRecord(TransactionRecordType.EVM_SWAP), "обм"))
    }

    @Test
    fun match_failedDexSwap_findsFailedTitleOnly() {
        val record = evmRecord(TransactionRecordType.EVM_SWAP, failed = true)

        assertTrue(matches(record, "обмен не выполнен"))
        assertFalse(matches(record, "обмен выполнен"))
    }

    @Test
    fun match_evmOutgoing_findsSendTitle() {
        assertTrue(matches(evmRecord(TransactionRecordType.EVM_OUTGOING), "отпр"))
    }

    @Test
    fun match_approve_findsApproveTitle() {
        assertTrue(matches(evmRecord(TransactionRecordType.EVM_APPROVE), "разреш"))
    }

    @Test
    fun match_outgoingLinkedToFinishedProviderSwap_findsStatusTitleWithoutWriting() {
        val record = evmRecord(TransactionRecordType.EVM_OUTGOING)
        storedSwaps = listOf(testProviderSwap(TransactionStatusEnum.FINISHED, outgoingRecordUid = record.uid))

        val match = matcher.match(record, matcher.prepare("обм"))

        assertEquals(storedSwaps.single(), match?.providerSwap?.swap)
        verify(exactly = 0) { swapStorage.setOutgoingRecordUid(any(), any()) }
    }

    @Test
    fun match_incomingPayoutOfFinishedProviderSwap_alsoFindsConfirmingTitle() {
        val swap = testProviderSwap(TransactionStatusEnum.FINISHED, outgoingRecordUid = null)
        storedSwaps = listOf(swap)
        every { swapTransactionMatcher.findMatch(any()) } returns IncomingSwapMatch(swap, linkAmount = BigDecimal.ONE)

        assertTrue(matches(evmRecord(TransactionRecordType.EVM_INCOMING), "подтвержд"))
        verify(exactly = 0) { swapTransactionMatcher.findMatchingSwap(any()) }
    }

    @Test
    fun prepare_queryInNoProviderStatusTitle_skipsSwapStorage() {
        storedSwaps = listOf(testProviderSwap(TransactionStatusEnum.FINISHED, outgoingRecordUid = "0xhash"))

        assertFalse(matches(evmRecord(TransactionRecordType.EVM_OUTGOING), "разреш"))
        verify(exactly = 0) { swapStorage.getAllAcrossAccounts() }
        verify(exactly = 0) { swapStorage.getByOutgoingRecordUid(any()) }
    }

    @Test
    fun prepare_providerStatusBecomesFinished_changesFingerprintAndRecordMatches() {
        val record = evmRecord(TransactionRecordType.EVM_OUTGOING)
        storedSwaps = listOf(testProviderSwap(TransactionStatusEnum.EXCHANGING, outgoingRecordUid = record.uid))
        val processing = matcher.prepare("обмен выполнен")
        assertNull(matcher.match(record, processing))

        storedSwaps = listOf(testProviderSwap(TransactionStatusEnum.FINISHED, outgoingRecordUid = record.uid))
        val finished = matcher.prepare("обмен выполнен")

        assertNotEquals(processing.providerFingerprint, finished.providerFingerprint)
        assertNotNull(matcher.match(record, finished))
    }

    @Test
    fun match_replacedBitcoinRecordSwapUnderEarlierChainUid_findsStatusTitleWithoutRelinking() = runTest {
        val wallet = WalletFactory.previewWallet()
        val registry = BitcoinReplacementRegistry(mockk<TransactionSettingsStorage> {
            var links = emptySet<String>()
            every { bitcoinReplacementLinks } answers { links }
            every { bitcoinReplacementLinks = any() } answers { links = firstArg() }
        })
        startKoin { modules(module { single { registry } }) }
        registry.remember(
            wallet.account.id,
            wallet.token.blockchainType.uid,
            BitcoinReplacementLink(HASH_A, UID_A, HASH_B, UID_B),
        )
        storedSwaps = listOf(testProviderSwap(TransactionStatusEnum.FINISHED, outgoingRecordUid = UID_B))
        val restored = bitcoinRecord(wallet, uid = "uid-B-restored", hash = HASH_B, chainUids = listOf(UID_A, UID_B))

        assertTrue(matches(restored, "обмен выполнен"))
        verify(exactly = 0) { swapStorage.relinkOutgoingRecord(any(), any(), any(), any()) }
        verify(exactly = 0) { swapStorage.setOutgoingRecordUid(any(), any()) }
    }

    private fun matches(record: TransactionRecord, query: String) = matcher.matches(record, matcher.prepare(query))

    private fun evmRecord(type: TransactionRecordType, failed: Boolean = false): EvmTransactionRecord {
        val bsc = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
        val bnb = Token(Coin("bsc", "BNB Smart Chain", "BNB"), bsc, TokenType.Native, 18)
        return EvmTransactionRecord(
            from = "0xfrom",
            to = "0xto",
            transaction = mockk<EvmTransaction>(relaxed = true) {
                every { hashString } returns "0xhash"
                every { isFailed } returns failed
            },
            token = bnb,
            source = TransactionSource(bsc, mockk(relaxed = true), null),
            protected = false,
            transactionRecordType = type,
            spender = "0xspender",
            value = TransactionValue.CoinValue(bnb, BigDecimal.ONE),
        )
    }

    private fun bitcoinRecord(wallet: Wallet, uid: String, hash: String, chainUids: List<String>) =
        BitcoinTransactionRecord(
            token = wallet.token,
            amount = BigDecimal("-0.01"),
            to = listOf("bc1-deposit"),
            from = null,
            changeAddresses = emptyList(),
            uid = uid,
            transactionHash = hash,
            transactionIndex = 0,
            blockHeight = null,
            confirmationsThreshold = 1,
            timestamp = 1_779_766_789L,
            failed = false,
            memo = null,
            source = wallet.transactionSource,
            transactionRecordType = TransactionRecordType.BITCOIN_OUTGOING,
            fee = null,
            lockInfo = null,
            conflictingHash = null,
            showRawTransaction = false,
            replacementChainUids = chainUids,
        )

    @Test
    fun matches_removeLiquidity_findsSearchKeyAndTitleCaseInsensitively() {
        val record = liquidityRecord(LiquidityOperation(LiquidityOperation.Direction.Remove, failed = false))

        assertTrue(matches(record, "ЛИКВИДНОСТЬ"))
        assertTrue(matches(record, "выведено из ликвидности"))
        assertFalse(matches(record, "добавлено"))
    }

    @Test
    fun matches_failedAddLiquidity_findsFailedTitle() {
        val record = liquidityRecord(LiquidityOperation(LiquidityOperation.Direction.Add, failed = true))

        assertTrue(matches(record, "не удалось добавить"))
    }

    @Test
    fun matches_recordWithoutLiquidityOperation_ignoresLiquidityKey() {
        val record = liquidityRecord(null)

        assertFalse(matches(record, "ликвидность"))
    }

    private fun liquidityRecord(operation: LiquidityOperation?): EvmTransactionRecord {
        mockkObject(Translator)
        every { Translator.getString(Res.string.transactions_liquidity_search_key) } returns "ликвидность"
        every { Translator.getString(Res.string.transactions_liquidity_removed) } returns "Выведено из ликвидности"
        every { Translator.getString(Res.string.transactions_liquidity_add_failed) } returns
            "Не удалось добавить в ликвидность"
        return EvmTransactionRecord(
            transaction = mockk(relaxed = true) { every { hashString } returns "0xhash" },
            token = rune,
            source = TransactionSource(thorchain, mockk(relaxed = true), null),
            protected = false,
            transactionRecordType = TransactionRecordType.EVM_CONTRACT_CALL,
            incomingEvents = emptyList(),
            outgoingEvents = emptyList(),
            liquidityOperation = operation,
        )
    }

    private companion object {
        const val UID_A = "uid-A"
        const val UID_B = "uid-B"
        const val HASH_A = "aaaa"
        const val HASH_B = "bbbb"
        const val MEMO = "=:ETH.ETH:0xabc"
        const val RECIPIENT = "thor1recipientaddress"
        const val SENDER = "thor1senderaddress"
        const val NFT_MINT = "NftMint11111111111111111111111111111111111"
    }
}
