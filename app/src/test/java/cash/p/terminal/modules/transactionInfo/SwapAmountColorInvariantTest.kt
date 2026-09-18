package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import cash.p.terminal.ui_compose.ColorName
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The transaction list and the transaction details screen must never paint one and the
 * same swap output two different colours. They are independent factories, so the
 * invariant is asserted on both at once rather than on either alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = InvariantTestCoreApp::class)
class SwapAmountColorInvariantTest {

    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)
    private val balanceHiddenManager = mockk<BalanceHiddenManager>()
    private val swapProviderTransactionsStorage =
        mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val swapTransactionMatcher = mockk<SwapTransactionMatcher>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val localStorage = mockk<ILocalStorage>()
    private val poisonAddressManager = mockk<PoisonAddressManager>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val appNumberFormatter = mockk<IAppNumberFormatter>(relaxed = true)

    private lateinit var listFactory: TransactionViewItemFactory

    @Before
    fun setUp() {
        stopKoin()
        CoreApp.instance = RuntimeEnvironment.getApplication() as CoreApp
        startKoin {
            modules(
                module {
                    single { numberFormatter }
                    single { contactsRepository }
                    single { addressLabelManager }
                    single {
                        AddressMetadataManager(
                            contactsRepository = contactsRepository,
                            addressLabelManager = addressLabelManager,
                        )
                    }
                }
            )
        }

        mockkObject(App)
        mockkObject(DateHelper)
        mockkObject(Translator)

        every { App.numberFormatter } returns appNumberFormatter
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"
        every { appNumberFormatter.formatCoinShort(any(), any(), any()) } returns "amount"
        every { appNumberFormatter.formatFiatShort(any(), any(), any()) } returns "$0"
        every { numberFormatter.formatCoinFull(any(), any(), any()) } returns "amount"

        every { balanceHiddenManager.balanceHidden } returns false
        every { balanceHiddenManager.isTransactionInfoHidden(any()) } returns false
        every { balanceHiddenManager.isTransactionInfoHiddenForWallet(any(), any()) } returns false
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        every { swapTransactionMatcher.findMatchingSwap(any()) } returns null
        coEvery {
            poisonAddressManager.getPoisonStatus(any<TransactionRecord>())
        } returns PoisonStatus.BLOCKCHAIN

        listFactory = TransactionViewItemFactory(
            addressMetadataManager = AddressMetadataManager(
                contactsRepository = contactsRepository,
                addressLabelManager = addressLabelManager,
            ),
            balanceHiddenManager = balanceHiddenManager,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
            swapTransactionMatcher = swapTransactionMatcher,
            numberFormatter = numberFormatter,
            marketKit = marketKit,
            localStorage = localStorage,
            poisonAddressManager = poisonAddressManager,
            accountManager = accountManager,
        )
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun swapOutputColor_recipientIsOwnAddress_listAndDetailsAgreeOnGreen() = runTest {
        val record = swapRecord(recipient = null)

        assertEquals(ColorName.Remus, listColor(record))
        assertEquals(ColorName.Remus, detailsColor(record))
    }

    @Test
    fun swapOutputColor_recipientIsThirdParty_listAndDetailsAgreeOnGrey() = runTest {
        val record = swapRecord(recipient = THIRD_PARTY)

        assertEquals(ColorName.Grey, listColor(record))
        assertEquals(ColorName.Grey, detailsColor(record))
    }

    private suspend fun listColor(record: EvmTransactionRecord): ColorName {
        val item = TransactionItem(
            record = record,
            currencyValue = null,
            lastBlockInfo = null,
            nftMetadata = emptyMap(),
        )
        return requireNotNull(listFactory.convertToViewItemCached(item).primaryValue).color
    }

    private fun detailsColor(record: EvmTransactionRecord): ColorName {
        val sections = TransactionInfoViewItemFactory(
            offlineOperationGate = mockk<OfflineOperationGate>(relaxed = true),
            wallet = null,
            blockchainType = BlockchainType.BinanceSmartChain,
        ).getViewItemSections(
            TransactionInfoItem(
                record = record,
                externalStatus = null,
                lastBlockInfo = null,
                explorerData = emptyList(),
                rates = emptyMap(),
                nftMetadata = emptyMap(),
                hideAmount = false,
                offlineStatus = null,
            )
        )

        val youGot = sections.flatten()
            .filterIsInstance<TransactionInfoViewItem.Amount>()
            .single { it.amountType == AmountType.YouGot }

        return youGot.coinValue.color
    }

    private fun swapRecord(recipient: String?) = EvmTransactionRecord(
        transaction = mockk<Transaction>(relaxed = true) {
            every { hashString } returns TX_HASH
            every { transactionIndex } returns 0
            every { blockNumber } returns 100L
            every { timestamp } returns 1_000L
            every { isFailed } returns false
        },
        token = mockk<Token>(relaxed = true),
        source = mockk<TransactionSource>(relaxed = true) {
            every { blockchain } returns mockk(relaxed = true) {
                every { type } returns BlockchainType.BinanceSmartChain
            }
        },
        protected = false,
        transactionRecordType = TransactionRecordType.EVM_SWAP,
        exchangeAddress = ROUTER_ADDRESS,
        valueIn = TransactionValue.TokenValue(
            tokenName = "BNB",
            tokenCode = "BNB",
            tokenDecimals = 18,
            value = BigDecimal("-1"),
        ),
        valueOut = TransactionValue.TokenValue(
            tokenName = "Filecoin",
            tokenCode = "FIL",
            tokenDecimals = 18,
            value = BigDecimal("897.288428"),
        ),
        recipient = recipient,
    )

    private companion object {
        const val TX_HASH = "0f79675d00000000000000000000000000000000000000000000000000000000"
        const val ROUTER_ADDRESS = "0x13f4EA83D0bd40E75C8222255bc855a974568Dd4"
        const val THIRD_PARTY = "0x2222222222222222222222222222222222222222"
    }
}

internal class InvariantTestCoreApp : CoreApp() {
    override fun localizedContext() = this
    override val isSwapEnabled: Boolean = false
}
