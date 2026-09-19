package cash.p.terminal.modules.transactionInfo

import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.R
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.modules.transactions.beamHistoryRecord
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(application = TestCoreApp::class)
class TransactionInfoViewItemFactoryTest {

    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any()) } answers { "string:${firstArg<Int>()}" }
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
        every { numberFormatter.formatCoinFull(any(), any(), any()) } answers {
            "${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()} ${secondArg<String>()}"
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun getViewItemSections_offlineRawWithoutMetadata_showsPlaceholders() {
        val record = pendingRecord(toAddress = "")
        val item = transactionInfoItem(
            record = record,
            offlineStatus = ColoredValue("Sent", ColorName.Remus),
        )

        val sections = TransactionInfoViewItemFactory(
            offlineOperationGate = mockk<OfflineOperationGate>(relaxed = true),
            wallet = null,
            blockchainType = BlockchainType.Bitcoin,
        ).getViewItemSections(item)

        val amount = sections.first().first() as TransactionInfoViewItem.Amount
        val recipient = sections.first()[1] as TransactionInfoViewItem.Value
        assertEquals("---", amount.coinValue.value)
        assertEquals("---", amount.fiatValue.value)
        assertEquals("---", recipient.value)
    }

    @Test
    fun getViewItemSections_offlineBlankRecipientKnownAmount_showsAmountWithUnknownRecipient() {
        val record = pendingRecord(toAddress = "", amount = BigDecimal.ONE)
        val item = transactionInfoItem(
            record = record,
            offlineStatus = ColoredValue("Sent", ColorName.Remus),
        )

        val sections = TransactionInfoViewItemFactory(
            offlineOperationGate = mockk<OfflineOperationGate>(relaxed = true),
            wallet = null,
            blockchainType = BlockchainType.Bitcoin,
        ).getViewItemSections(item)

        val amount = sections.first().first() as TransactionInfoViewItem.Amount
        // The recipient keeps the address row's place, right after the amount and before the rate.
        val recipient = sections.first()[1] as TransactionInfoViewItem.Value
        assertEquals("-1 BTC", amount.coinValue.value)
        assertEquals(Translator.getString(R.string.TransactionInfo_To), recipient.title)
        assertEquals("---", recipient.value)
    }

    @Test
    fun getReceiveSectionItems_knownAddress_showsLabelInTitleAndKeepsFullAddress() {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"

        val items = TransactionViewItemFactoryHelper.getReceiveSectionItems(
            value = tokenValue(),
            fromAddress = BRIDGE_ADDRESS,
            toAddress = null,
            coinPrice = null,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(BRIDGE_ADDRESS, address.value)
        assertEquals(false, address.showAdd)
        assertEquals(true, address.title.endsWith(" · Token Bridge"))
        assertEquals(true, address.collapseAddress)
    }

    @Test
    fun getSendSectionItems_knownAddress_showsLabelInTitleAndKeepsFullAddress() {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"

        val items = TransactionViewItemFactoryHelper.getSendSectionItems(
            value = tokenValue(),
            toAddress = listOf(BRIDGE_ADDRESS),
            coinPrice = null,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(BRIDGE_ADDRESS, address.value)
        assertEquals(false, address.showAdd)
        assertEquals(true, address.title.endsWith(" · Token Bridge"))
    }

    @Test
    fun getApproveSectionItems_knownSpender_showsLabelInTitle() {
        every {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        } returns "Token Bridge"

        val items = TransactionViewItemFactoryHelper.getApproveSectionItems(
            value = tokenValue(),
            coinPrice = null,
            spenderAddress = BRIDGE_ADDRESS,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(true, address.title.endsWith(" · Token Bridge"))
        assertEquals(false, address.showAdd)
    }

    @Test
    fun getSendSectionItems_contactAndLabelExist_keepsContactPriority() {
        val contact = mockk<Contact>(relaxed = true)
        every {
            contactsRepository.getContactsFiltered(
                BlockchainType.BinanceSmartChain,
                addressQuery = BRIDGE_ADDRESS,
            )
        } returns listOf(contact)

        val items = TransactionViewItemFactoryHelper.getSendSectionItems(
            value = tokenValue(),
            toAddress = listOf(BRIDGE_ADDRESS),
            coinPrice = null,
            hideAmount = false,
            blockchainType = BlockchainType.BinanceSmartChain,
        )

        val address = items.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(false, address.title.contains("Token Bridge"))
        assertEquals(false, address.showAdd)
        assertEquals(false, address.collapseAddress)
        assertEquals(
            contact,
            items.filterIsInstance<TransactionInfoViewItem.ContactItem>().single().contact,
        )
        verify(exactly = 0) {
            addressLabelManager.label(BlockchainType.BinanceSmartChain, BRIDGE_ADDRESS)
        }
    }

    @Test
    fun getSwapDetailsSectionItems_completeValues_preservesBothPriceDirections() {
        val items = TransactionViewItemFactoryHelper.getSwapDetailsSectionItems(
            rates = emptyMap(),
            exchangeAddress = BRIDGE_ADDRESS,
            valueOut = tokenValue(code = "OUT", value = BigDecimal.TEN),
            valueIn = tokenValue(code = "IN", value = BigDecimal("20")),
            blockchainType = BlockchainType.BinanceSmartChain,
            providerName = "Provider",
        )

        val prices = items.filterIsInstance<TransactionInfoViewItem.PriceWithToggle>().single()
        assertEquals("OUT = 2 IN", prices.valueOne)
        assertEquals("IN = 0.5 OUT", prices.valueTwo)
    }

    @Test
    fun getViewItemSections_beamDirections_preservesSignAndPrivacy() {
        BeamTransactionDirection.entries.forEach { direction ->
            val items = beamItems(beamHistoryRecord(direction = direction))
            // The BEAM-specific direction header is gone; the shared amount row carries the title.
            assertEquals(false, items.any { it is TransactionInfoViewItem.Transaction })
            val amount = items.filterIsInstance<TransactionInfoViewItem.Amount>().single()
            // Self goes through the shared send builder with sentToSelf, which renders unsigned.
            val expectedAmount = when (direction) {
                BeamTransactionDirection.Incoming -> "+1 BEAM"
                BeamTransactionDirection.Outgoing -> "-1 BEAM"
                BeamTransactionDirection.Self -> "1 BEAM"
            }
            assertEquals(expectedAmount, amount.coinValue.value)
            // No counterparty in this fixture, so no address row is fabricated.
            assertEquals(false, items.any { it is TransactionInfoViewItem.Address })
            assertEquals(false, items.any { it is TransactionInfoViewItem.Explorer })
            assertEquals("transaction-id",
                items.filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
            val values = items.filterIsInstance<TransactionInfoViewItem.Value>()
            // "Unknown" must no longer be rendered as a counterparty anywhere in the section.
            assertEquals(false, values.any {
                it.value == Translator.getString(R.string.transaction_swap_status_unknown)
            })
            // The fee row is the sender's cost, so it is present for everything but Incoming.
            assertEquals(direction != BeamTransactionDirection.Incoming,
                values.any { it.value == "0.000001 BEAM" })
            assertEquals(true, values.any { it.value == "kernel-id" })
        }
    }

    @Test
    fun getViewItemSections_beamCounterparty_rendersAddressRowOrOmitsIt() {
        val endpoint = "3ZkVRpXZ7dJqQFHRzWq2SbWUzB1xJz9Mk6uQnD4Yc7Tt"
        val token = "6xfHF7nkBSTHkLYGCcCFHhUgbDzoBXTGevBgHzHRSXjcPbPSAJU"
        // The relaxed mock answers "" rather than null, which reads as "a label exists" and would
        // append it to the title; these addresses are deliberately unlabelled.
        every { addressLabelManager.label(any(), any()) } returns null

        val incoming = beamItems(beamHistoryRecord(
            direction = BeamTransactionDirection.Incoming, counterparty = endpoint))
        val fromRow = incoming.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(Translator.getString(R.string.TransactionInfo_From), fromRow.title)
        assertEquals(endpoint, fromRow.value)

        val outgoing = beamItems(beamHistoryRecord(
            direction = BeamTransactionDirection.Outgoing, counterparty = token))
        val toRow = outgoing.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(Translator.getString(R.string.TransactionInfo_To), toRow.title)
        assertEquals(token, toRow.value)

        // An anonymous sender is protocol-sanctioned: omit the row rather than invent a placeholder.
        val anonymous = beamItems(beamHistoryRecord(
            direction = BeamTransactionDirection.Incoming, counterparty = null))
        assertEquals(false, anonymous.any { it is TransactionInfoViewItem.Address })
        assertEquals(false, anonymous.filterIsInstance<TransactionInfoViewItem.Value>().any {
            it.value == Translator.getString(R.string.transaction_swap_status_unknown)
        })
    }

    @Test
    fun getViewItemSections_beamAddressRow_isCollapsedWhileOtherChainsAreNot() {
        // Without this the relaxed mock reports a label for every address, which collapses them all
        // and would make the non-BEAM half of this test pass for the wrong reason.
        every { addressLabelManager.label(any(), any()) } returns null
        val beamRow = beamItems(beamHistoryRecord(
            direction = BeamTransactionDirection.Outgoing,
            counterparty = "6xfHF7nkBSTHkLYGCcCFHhUgbDzoBXTGevBgHzHRSXjcPbPSAJU",
        )).filterIsInstance<TransactionInfoViewItem.Address>().single()
        // A BEAM Offline token runs to ~3200 characters, so it must never render in full.
        assertEquals(true, beamRow.collapseAddress)

        // Regression guard: an unlabelled address on another chain keeps the shared default.
        val otherChain = TransactionViewItemFactoryHelper.getSendSectionItems(
            value = tokenValue(code = "ETH", value = BigDecimal.ONE),
            toAddress = listOf(BRIDGE_ADDRESS),
            coinPrice = null,
            hideAmount = false,
            sentToSelf = false,
            blockchainType = BlockchainType.Ethereum,
        ).filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(false, otherChain.collapseAddress)
    }

    @Test
    fun getViewItemSections_beamIncoming_omitsTheFeeRow() {
        val feeTitle = Translator.getString(R.string.TransactionInfo_Fee)
        val feeRows = { direction: BeamTransactionDirection ->
            beamItems(beamHistoryRecord(direction = direction))
                .filterIsInstance<TransactionInfoViewItem.Value>()
                .count { it.title == feeTitle }
        }

        // The receiving wallet does not pay this fee, so the row must not appear in its own section.
        assertEquals(0, feeRows(BeamTransactionDirection.Incoming))
        assertEquals(1, feeRows(BeamTransactionDirection.Outgoing))
        assertEquals(1, feeRows(BeamTransactionDirection.Self))
    }

    @Test
    fun beamInFlightStatuses_haveStrictlyIncreasingProgress() {
        val progress = { status: BeamTransactionStatus ->
            val mapped = beamHistoryRecord(status = status).status(null)
            assertEquals(true, mapped is TransactionStatus.Processing)
            (mapped as TransactionStatus.Processing).progress
        }

        val inProgress = progress(BeamTransactionStatus.InProgress)
        val registering = progress(BeamTransactionStatus.Registering)
        val confirming = progress(BeamTransactionStatus.Confirming)

        // Strict inequality is what makes this bite: a single shared value would satisfy <=.
        assertEquals(true, inProgress < registering)
        assertEquals(true, registering < confirming)
        assertEquals(true, confirming < 1f)
    }

    @Test
    fun getViewItemSections_beamStatusesWithOldProofHeight_preservesSdkState() {
        val titles = mapOf(
            BeamTransactionStatus.Pending to R.string.Transactions_Pending,
            BeamTransactionStatus.InProgress to R.string.beam_history_in_progress,
            BeamTransactionStatus.Registering to R.string.beam_history_registering,
            BeamTransactionStatus.Confirming to R.string.transaction_swap_status_confirming,
            BeamTransactionStatus.Completed to R.string.Transactions_Completed,
            BeamTransactionStatus.Failed to R.string.Transactions_Failed,
            BeamTransactionStatus.Canceled to R.string.beam_history_canceled,
            BeamTransactionStatus.Unknown to R.string.transaction_swap_status_unknown,
        )
        assertEquals(BeamTransactionStatus.entries.toSet(), titles.keys)
        titles.forEach { (status, title) ->
            val items = beamItems(beamHistoryRecord(status = status))
            val statusItem = items.filterIsInstance<TransactionInfoViewItem.Value>().single {
                it.title == Translator.getString(R.string.TransactionInfo_Status)
            }
            assertEquals(Translator.getString(title), statusItem.value)
            assertEquals(false, items.any { it is TransactionInfoViewItem.Status })
        }
    }

    @Test
    fun getViewItemSections_beamFailedWithoutKernel_showsReasonAndOmitsKernel() {
        val items = beamItems(beamHistoryRecord(
            status = BeamTransactionStatus.Failed, kernelId = null, failureReason = "Transaction expired"
        )).filterIsInstance<TransactionInfoViewItem.Value>()
        assertEquals("Transaction expired", items.single {
            it.title == Translator.getString(R.string.beam_history_failure_reason)
        }.value)
        assertEquals(false, items.any { it.title == Translator.getString(R.string.beam_history_kernel_id) })
    }

    @Test
    fun getViewItemSections_beamHidden_masksAmountsAndIdentifiers() {
        val items = beamItems(beamHistoryRecord(failureReason = "Transaction expired"), hideAmount = true)
        assertEquals("*****", items.filterIsInstance<TransactionInfoViewItem.Amount>().single().coinValue.value)
        assertEquals("*****",
            items.filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
        val sensitiveTitles = listOf(
            R.string.TransactionInfo_Fee, R.string.beam_history_kernel_id, R.string.beam_history_failure_reason
        ).map { Translator.getString(it) }
        val sensitiveValues = items.filterIsInstance<TransactionInfoViewItem.Value>().filter {
            it.title in sensitiveTitles
        }
        assertEquals(3, sensitiveValues.size)
        assertEquals(true, sensitiveValues.all { it.value == "*****" })
    }

    private fun beamItems(
        record: TransactionRecord,
        hideAmount: Boolean = false,
    ): List<TransactionInfoViewItem> = TransactionInfoViewItemFactory(
        offlineOperationGate = mockk(relaxed = true), wallet = null, blockchainType = BlockchainType.Beam
    ).getViewItemSections(transactionInfoItem(record).copy(
        lastBlockInfo = LastBlockInfo(height = 1_000), hideAmount = hideAmount,
    )).flatten()

    private fun transactionInfoItem(
        record: TransactionRecord,
        offlineStatus: ColoredValue? = null,
    ) = TransactionInfoItem(
        record = record,
        externalStatus = null,
        lastBlockInfo = null,
        explorerData = emptyList(),
        rates = emptyMap(),
        nftMetadata = emptyMap(),
        hideAmount = false,
        offlineStatus = offlineStatus,
    )

    private fun tokenValue(
        code: String = "COSA",
        value: BigDecimal = BigDecimal.ONE,
    ) = TransactionValue.TokenValue(
        tokenName = "COSA",
        tokenCode = code,
        tokenDecimals = 18,
        value = value,
    )

    private fun pendingRecord(toAddress: String, amount: BigDecimal = BigDecimal.ZERO): PendingTransactionRecord {
        val token = Token(
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
            type = TokenType.Derived(TokenType.Derivation.Bip84),
            decimals = 8,
        )
        return PendingTransactionRecord(
            uid = "offline-signed:hash",
            transactionHash = "hash",
            timestamp = 1_000L,
            source = TransactionSource(
                blockchain = token.blockchain,
                account = mockk<Account>(relaxed = true),
                meta = null,
            ),
            token = token,
            amount = amount,
            toAddress = toAddress,
            fromAddress = "",
            expiresAt = Long.MAX_VALUE,
            memo = null,
        )
    }

    private companion object {
        const val BRIDGE_ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"
    }
}

internal class TestCoreApp : CoreApp() {
    override fun localizedContext() = this
    override val isSwapEnabled: Boolean = false
}
