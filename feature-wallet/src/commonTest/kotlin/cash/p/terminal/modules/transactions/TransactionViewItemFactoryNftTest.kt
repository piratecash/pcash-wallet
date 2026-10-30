package cash.p.terminal.modules.transactions

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.RawUnstoppableProviderTitles
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_24_nft_placeholder
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.helpers.DateHelper
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
import java.math.BigDecimal

class TransactionViewItemFactoryNftTest {

    private val coinManager = mockk<ICoinManager>(relaxed = true)
    private val addressLabelManager = mockk<AddressLabelManager>()
    private val contactsRepository = mockk<ContactsRepository>()
    private val balanceHiddenManager = mockk<BalanceHiddenManager>()
    private val localStorage = mockk<TransactionSettingsStorage>()
    private val poisonAddressManager = mockk<PoisonAddressManager>()
    private val appNumberFormatter = mockk<IAppNumberFormatter>(relaxed = true)

    private lateinit var factory: TransactionViewItemFactory

    @Before
    fun setUp() {
        mockkObject(DateHelper)
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"
        every { coinManager.getToken(any()) } returns null
        every { addressLabelManager.label(any(), any()) } returns null
        every { contactsRepository.getContactsFiltered(any(), addressQuery = any()) } returns emptyList()
        every { balanceHiddenManager.balanceHidden } returns false
        every { balanceHiddenManager.isTransactionInfoHidden(any()) } returns false
        every { balanceHiddenManager.isTransactionInfoHiddenForWallet(any(), any()) } returns false
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        coEvery { poisonAddressManager.getPoisonStatus(any<TransactionRecord>()) } returns PoisonStatus.BLOCKCHAIN

        factory = TransactionViewItemFactory(
            addressMetadataManager = AddressMetadataManager(
                contactsRepository = contactsRepository,
                addressLabelManager = addressLabelManager,
            ),
            balanceHiddenManager = balanceHiddenManager,
            swapProviderTransactionsStorage = mockk(relaxed = true),
            swapTransactionMatcher = mockk(relaxed = true),
            numberFormatter = appNumberFormatter,
            marketKit = mockk(relaxed = true),
            localStorage = localStorage,
            poisonAddressManager = poisonAddressManager,
            unstoppableProviderTitles = RawUnstoppableProviderTitles,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun convertToViewItemCached_incomingSolanaNftWithoutName_showsMintWithPlaceholderIcon() = runTest {
        val viewItem = factory.convertToViewItemCached(incomingNftItem(name = null))

        assertEquals("#$NFT_MINT", viewItem.secondaryValue?.value)
        assertEquals(
            TransactionViewItem.Icon.Regular(
                url = null,
                alternativeUrl = null,
                placeholder = Res.drawable.icon_24_nft_placeholder,
                rectangle = true
            ),
            viewItem.icon
        )
    }

    @Test
    fun convertToViewItemCached_incomingSolanaNftWithName_showsNameAndMint() = runTest {
        val viewItem = factory.convertToViewItemCached(incomingNftItem(name = "Mad Lad"))

        assertEquals("Mad Lad #$NFT_MINT", viewItem.secondaryValue?.value)
    }

    private fun incomingNftItem(name: String?): TransactionItem {
        val record = SolanaTransactionConverterTestFixture.createConverter(coinManager).transactionRecord(
            SolanaTransactionConverterTestFixture.tokenTransferTransaction(
                hash = "hash",
                from = "SENDER_ADDRESS",
                to = SolanaTransactionConverterTestFixture.USER_ADDRESS,
                mint = NFT_MINT,
                incoming = true,
                amount = BigDecimal.ONE,
                decimals = 0,
                isNft = true,
                name = name,
            )
        )
        return TransactionItem(record = record, currencyValue = null, lastBlockInfo = null)
    }

    private companion object {
        const val NFT_MINT = "NFT_MINT"
    }
}
