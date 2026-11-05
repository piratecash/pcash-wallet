package cash.p.terminal.modules.transactionInfo

import org.jetbrains.compose.resources.StringResource
import cash.p.terminal.core.TestCoreApp
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.SwapDuration
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.modules.transactions.beamHistoryRecord
import cash.p.terminal.modules.transactions.beamInteractiveHistoryRecord
import io.horizontalsystems.core.Translator
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.AccountDelegation
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Transaction as EvmKitTransaction
import io.horizontalsystems.thorchainkit.models.Transaction
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.resources.TransactionInfo_ExecutedRate
import cash.p.terminal.resources.TransactionInfo_Fee
import cash.p.terminal.resources.TransactionInfo_From
import cash.p.terminal.resources.TransactionInfo_Price
import cash.p.terminal.resources.TransactionInfo_PriceImpact
import cash.p.terminal.resources.TransactionInfo_Status
import cash.p.terminal.resources.TransactionInfo_To
import cash.p.terminal.resources.Transactions_Completed
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.Transactions_Pending
import cash.p.terminal.resources.beam_cancel_description
import cash.p.terminal.resources.beam_history_address_expired
import cash.p.terminal.resources.beam_history_canceled
import cash.p.terminal.resources.beam_history_expired
import cash.p.terminal.resources.beam_history_failure_reason
import cash.p.terminal.resources.beam_history_in_progress
import cash.p.terminal.resources.beam_history_kernel_id
import cash.p.terminal.resources.beam_history_registering
import cash.p.terminal.resources.beam_history_waiting_for_receiver
import cash.p.terminal.resources.beam_history_waiting_for_sender
import cash.p.terminal.resources.duration_short_hours
import cash.p.terminal.resources.duration_short_minutes
import cash.p.terminal.resources.duration_short_seconds
import cash.p.terminal.resources.swap_duration
import cash.p.terminal.resources.swap_duration_approximate
import cash.p.terminal.resources.transaction_info_delegation_contract
import cash.p.terminal.resources.transaction_info_delegation_warning
import cash.p.terminal.resources.transaction_info_executed_amount
import cash.p.terminal.resources.transaction_info_initiator
import cash.p.terminal.resources.transaction_swap_status_confirming
import cash.p.terminal.resources.transaction_swap_status_unknown
import cash.p.terminal.resources.transactions_account_delegation_revoke

@RunWith(RobolectricTestRunner::class)
@Config(application = TestCoreApp::class)
class TransactionInfoViewItemFactoryTest {

    init {
        // CryptoUtils.sha3 needs ETH-KECCAK-256 from BouncyCastle, registered at runtime
        // by EthereumKit; delegation records compute Address.eip55 via it.
        EthereumKit.init()
    }

    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)
    private val beamWallet = mockk<Wallet>(relaxed = true)
    private val allOperationsSupported = mockk<PlatformOperationSupport> {
        every { supports(any()) } returns true
    }

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any()) } answers { "string:${firstArg<StringResource>().key}" }
        every { Translator.getString(any(), *anyVararg()) } answers {
            "string:${firstArg<StringResource>().key}:${secondArg<Array<Any>>().joinToString()}"
        }
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
            evmKitWrapper = null,
            platformOperationSupport = allOperationsSupported,
            unstoppableProviderTitles = mockk(relaxed = true),
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
            evmKitWrapper = null,
            platformOperationSupport = allOperationsSupported,
            unstoppableProviderTitles = mockk(relaxed = true),
        ).getViewItemSections(item)

        val amount = sections.first().first() as TransactionInfoViewItem.Amount
        // The recipient keeps the address row's place, right after the amount and before the rate.
        val recipient = sections.first()[1] as TransactionInfoViewItem.Value
        assertEquals("-1 BTC", amount.coinValue.value)
        assertEquals(Translator.getString(Res.string.TransactionInfo_To), recipient.title)
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
    fun getStatusSectionItems_thorchainOutgoingWithFee_showsFeeRow() {
        val values = thorchainStatusItems(TransactionValue.CoinValue(runeToken, BigDecimal("0.02")))
            .filterIsInstance<TransactionInfoViewItem.Value>()

        assertEquals(2, values.size)
        assertEquals("0.02 RUNE", values.last().value)
    }

    @Test
    fun getStatusSectionItems_thorchainWithoutFee_hasNoFeeRow() {
        val values = thorchainStatusItems(fee = null).filterIsInstance<TransactionInfoViewItem.Value>()

        // only the date row
        assertEquals(1, values.size)
    }

    @Test
    fun getStatusSectionItems_foreignEvmRecord_showsInitiatorAddress() {
        val items = evmStatusItems(foreignEvmRecord(foreign = true, initiator = RELAYER_ADDRESS))

        val initiator = items.filterIsInstance<TransactionInfoViewItem.Address>().single {
            it.title == Translator.getString(Res.string.transaction_info_initiator)
        }
        assertEquals(RELAYER_ADDRESS, initiator.value)
        assertTrue(initiator.collapseAddress)
        assertEquals(
            false,
            items.filterIsInstance<TransactionInfoViewItem.Value>()
                .any { it.title == Translator.getString(Res.string.TransactionInfo_Fee) }
        )
    }

    @Test
    fun getStatusSectionItems_ownEvmRecord_hasNoInitiatorRow() {
        val items = evmStatusItems(foreignEvmRecord(foreign = false, initiator = null))

        assertEquals(
            false,
            items.filterIsInstance<TransactionInfoViewItem.Address>()
                .any { it.title == Translator.getString(Res.string.transaction_info_initiator) }
        )
    }

    @Test
    fun getViewItemSections_delegationActive_showsWarningSectionAndCollapsedContractRow() {
        val sections = evmViewItemSectionList(
            delegationRecord(delegate = DELEGATE_ADDRESS, isActive = true)
        )

        // The page draws a WarningMessage only when it is the sole item of its section.
        val warning = sections.single { it.any { item -> item is TransactionInfoViewItem.WarningMessage } }
        assertEquals(
            Translator.getString(Res.string.transaction_info_delegation_warning),
            (warning.single() as TransactionInfoViewItem.WarningMessage).message,
        )
        val contractRow = sections.flatten().filterIsInstance<TransactionInfoViewItem.Address>().single {
            it.title == Translator.getString(Res.string.transaction_info_delegation_contract)
        }
        assertEquals(Address(DELEGATE_ADDRESS).eip55, contractRow.value)
        assertTrue(contractRow.collapseAddress)
    }

    @Test
    fun getViewItemSections_delegationNotActive_showsContractRowWithoutWarning() {
        val items = evmViewItemSections(
            delegationRecord(delegate = DELEGATE_ADDRESS, isActive = false)
        )

        assertEquals(emptyList<Any>(), items.filterIsInstance<TransactionInfoViewItem.WarningMessage>())
        assertEquals(
            1,
            items.filterIsInstance<TransactionInfoViewItem.Address>()
                .count { it.title == Translator.getString(Res.string.transaction_info_delegation_contract) }
        )
    }

    @Test
    fun getViewItemSections_delegationRevoked_showsDescriptionOnly() {
        val items = evmViewItemSections(
            delegationRecord(delegate = ZERO_ADDRESS, isActive = false)
        )

        assertEquals(emptyList<Any>(), items.filterIsInstance<TransactionInfoViewItem.WarningMessage>())
        assertEquals(emptyList<Any>(), items.filterIsInstance<TransactionInfoViewItem.Address>())
        assertEquals(
            Translator.getString(Res.string.transactions_account_delegation_revoke),
            items.filterIsInstance<TransactionInfoViewItem.Description>().single().text,
        )
    }

    @Test
    fun getViewItemSections_sponsoredCallWithDelegation_showsTransferAndDelegationSections() {
        val items = evmViewItemSections(
            sponsoredCallRecordWithDelegation(delegate = DELEGATE_ADDRESS, isActive = true)
        )

        assertEquals(
            1,
            items.filterIsInstance<TransactionInfoViewItem.Amount>().size
        )
        assertEquals(
            1,
            items.filterIsInstance<TransactionInfoViewItem.WarningMessage>().size
        )
        assertEquals(
            1,
            items.filterIsInstance<TransactionInfoViewItem.Address>()
                .count { it.title == Translator.getString(Res.string.transaction_info_delegation_contract) }
        )
    }

    private fun evmViewItemSections(record: EvmTransactionRecord): List<TransactionInfoViewItem> =
        evmViewItemSectionList(record).flatten()

    private fun evmViewItemSectionList(record: EvmTransactionRecord): List<List<TransactionInfoViewItem>> =
        TransactionInfoViewItemFactory(
            offlineOperationGate = mockk(relaxed = true),
            wallet = null,
            blockchainType = BlockchainType.BinanceSmartChain,
            evmKitWrapper = null,
            platformOperationSupport = allOperationsSupported,
            unstoppableProviderTitles = mockk(relaxed = true),
        ).getViewItemSections(transactionInfoItem(record))

    private fun delegationRecord(delegate: String, isActive: Boolean): EvmTransactionRecord {
        every { addressLabelManager.label(BlockchainType.BinanceSmartChain, Address(delegate).eip55) } returns null
        return EvmTransactionRecord(
            transaction = mockk<EvmKitTransaction>(relaxed = true) {
                every { hashString } returns "0xdelegation-$delegate-$isActive"
                every { timestamp } returns 1_000L
                every { isFailed } returns false
                every { accountDelegation } returns AccountDelegation(Address(delegate), isActive)
            },
            token = mockk<Token>(relaxed = true),
            source = mockk<TransactionSource>(relaxed = true) {
                every { blockchain } returns mockk(relaxed = true) {
                    every { type } returns BlockchainType.BinanceSmartChain
                }
            },
            protected = false,
            transactionRecordType = TransactionRecordType.EVM_ACCOUNT_DELEGATION,
        )
    }

    private fun sponsoredCallRecordWithDelegation(delegate: String, isActive: Boolean): EvmTransactionRecord {
        every { addressLabelManager.label(BlockchainType.BinanceSmartChain, Address(delegate).eip55) } returns null
        return EvmTransactionRecord(
            transaction = mockk<EvmKitTransaction>(relaxed = true) {
                every { hashString } returns "0xsponsored-$delegate-$isActive"
                every { timestamp } returns 1_000L
                every { isFailed } returns false
                every { accountDelegation } returns AccountDelegation(Address(delegate), isActive)
            },
            token = mockk<Token>(relaxed = true),
            source = mockk<TransactionSource>(relaxed = true) {
                every { blockchain } returns mockk(relaxed = true) {
                    every { type } returns BlockchainType.BinanceSmartChain
                }
            },
            protected = false,
            transactionRecordType = TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL,
            incomingEvents = emptyList(),
            outgoingEvents = listOf(
                TransferEvent(
                    address = BRIDGE_ADDRESS,
                    addressForIncomingAddress = null,
                    value = TransactionValue.TokenValue(
                        tokenName = "Cosanta",
                        tokenCode = "COSA",
                        tokenDecimals = 8,
                        value = BigDecimal("-1"),
                    ),
                )
            ),
        )
    }

    private fun evmStatusItems(record: EvmTransactionRecord) =
        TransactionViewItemFactoryHelper.getStatusSectionItems(
            transaction = record,
            status = TransactionStatus.Completed,
            rates = emptyMap(),
            blockchainType = BlockchainType.BinanceSmartChain,
            hideSensitiveInfo = false,
        )

    private fun foreignEvmRecord(foreign: Boolean, initiator: String?): EvmTransactionRecord {
        every { addressLabelManager.label(BlockchainType.BinanceSmartChain, RELAYER_ADDRESS) } returns null
        return EvmTransactionRecord(
            transaction = mockk<EvmKitTransaction>(relaxed = true) {
                every { hashString } returns "0xabc"
                every { transactionIndex } returns 1
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
            transactionRecordType = TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL,
            incomingEvents = emptyList(),
            outgoingEvents = emptyList(),
            foreignTransaction = foreign,
            initiator = initiator,
        )
    }

    private fun thorchainStatusItems(fee: TransactionValue?) =
        TransactionViewItemFactoryHelper.getStatusSectionItems(
            transaction = ThorchainTransactionRecord(
                uid = "HASH-rune",
                transaction = Transaction("HASH", 1, 1_000L, "send", "success", null, emptyList(), emptyList()),
                spam = false,
                source = TransactionSource(runeToken.blockchain, mockk<Account>(relaxed = true), null),
                token = runeToken,
                type = ThorchainTransactionRecord.Type.Outgoing(
                    TransactionValue.CoinValue(runeToken, BigDecimal.ONE.negate()),
                    to = null,
                    sentToSelf = false,
                ),
                fee = fee,
            ),
            status = TransactionStatus.Completed,
            rates = emptyMap(),
            blockchainType = BlockchainType.Thorchain,
            hideSensitiveInfo = false,
        )

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
                it.value == Translator.getString(Res.string.transaction_swap_status_unknown)
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
        assertEquals(Translator.getString(Res.string.TransactionInfo_From), fromRow.title)
        assertEquals(endpoint, fromRow.value)

        val outgoing = beamItems(beamHistoryRecord(
            direction = BeamTransactionDirection.Outgoing, counterparty = token))
        val toRow = outgoing.filterIsInstance<TransactionInfoViewItem.Address>().single()
        assertEquals(Translator.getString(Res.string.TransactionInfo_To), toRow.title)
        assertEquals(token, toRow.value)

        // An anonymous sender is protocol-sanctioned: omit the row rather than invent a placeholder.
        val anonymous = beamItems(beamHistoryRecord(
            direction = BeamTransactionDirection.Incoming, counterparty = null))
        assertEquals(false, anonymous.any { it is TransactionInfoViewItem.Address })
        assertEquals(false, anonymous.filterIsInstance<TransactionInfoViewItem.Value>().any {
            it.value == Translator.getString(Res.string.transaction_swap_status_unknown)
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
        val feeTitle = Translator.getString(Res.string.TransactionInfo_Fee)
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
        // Default direction is Outgoing; InProgress for Outgoing is covered separately below.
        val titles = mapOf(
            BeamTransactionStatus.Pending to Res.string.Transactions_Pending,
            BeamTransactionStatus.InProgress to Res.string.beam_history_waiting_for_receiver,
            BeamTransactionStatus.Registering to Res.string.beam_history_registering,
            BeamTransactionStatus.Confirming to Res.string.transaction_swap_status_confirming,
            BeamTransactionStatus.Completed to Res.string.Transactions_Completed,
            BeamTransactionStatus.Failed to Res.string.Transactions_Failed,
            BeamTransactionStatus.Canceled to Res.string.beam_history_canceled,
            BeamTransactionStatus.Unknown to Res.string.transaction_swap_status_unknown,
        )
        assertEquals(BeamTransactionStatus.entries.toSet(), titles.keys)
        titles.forEach { (status, title) ->
            val items = beamItems(beamHistoryRecord(status = status))
            val statusItem = items.filterIsInstance<TransactionInfoViewItem.Value>().single {
                it.title == Translator.getString(Res.string.TransactionInfo_Status)
            }
            assertEquals(Translator.getString(title), statusItem.value)
            assertEquals(false, items.any { it is TransactionInfoViewItem.Status })
        }
    }

    @Test
    fun getViewItemSections_beamInProgressByDirection_showsWaitingForCounterparty() {
        val titles = mapOf(
            BeamTransactionDirection.Outgoing to Res.string.beam_history_waiting_for_receiver,
            BeamTransactionDirection.Incoming to Res.string.beam_history_waiting_for_sender,
            BeamTransactionDirection.Self to Res.string.beam_history_in_progress,
        )
        titles.forEach { (direction, title) ->
            val items = beamItems(beamHistoryRecord(direction = direction, status = BeamTransactionStatus.InProgress))
            val statusItem = items.filterIsInstance<TransactionInfoViewItem.Value>().single {
                it.title == Translator.getString(Res.string.TransactionInfo_Status)
            }
            assertEquals(Translator.getString(title), statusItem.value)
        }
    }

    @Test
    fun getViewItemSections_beamFailedWithTransactionExpired_showsExpiredStatusAndReason() {
        val items = beamItems(beamHistoryRecord(
            status = BeamTransactionStatus.Failed, failureReason = "TransactionExpired"
        )).filterIsInstance<TransactionInfoViewItem.Value>()
        val statusItem = items.single { it.title == Translator.getString(Res.string.TransactionInfo_Status) }
        assertEquals(Translator.getString(Res.string.beam_history_expired), statusItem.value)
        assertEquals(Translator.getString(Res.string.beam_history_expired), items.single {
            it.title == Translator.getString(Res.string.beam_history_failure_reason)
        }.value)
    }

    @Test
    fun getViewItemSections_beamFailedWithKnownReasons_mapsReasonToFriendlyText() {
        val reasons = mapOf(
            "Canceled" to Res.string.beam_history_canceled,
            "ExpiredAddressProvided" to Res.string.beam_history_address_expired,
            "NoInputs" to Res.string.Swap_ErrorInsufficientBalance,
        )
        reasons.forEach { (failureReason, title) ->
            val items = beamItems(beamHistoryRecord(
                status = BeamTransactionStatus.Failed, failureReason = failureReason
            )).filterIsInstance<TransactionInfoViewItem.Value>()
            assertEquals(Translator.getString(title), items.single {
                it.title == Translator.getString(Res.string.beam_history_failure_reason)
            }.value)
        }
    }

    @Test
    fun getViewItemSections_beamFailedWithoutKernel_showsReasonAndOmitsKernel() {
        val items = beamItems(beamHistoryRecord(
            status = BeamTransactionStatus.Failed, kernelId = null, failureReason = "Transaction expired"
        )).filterIsInstance<TransactionInfoViewItem.Value>()
        assertEquals("Transaction expired", items.single {
            it.title == Translator.getString(Res.string.beam_history_failure_reason)
        }.value)
        assertEquals(false, items.any { it.title == Translator.getString(Res.string.beam_history_kernel_id) })
    }

    @Test
    fun getViewItemSections_beamHidden_masksAmountsAndIdentifiers() {
        val items = beamItems(beamHistoryRecord(failureReason = "Transaction expired"), hideAmount = true)
        assertEquals("*****", items.filterIsInstance<TransactionInfoViewItem.Amount>().single().coinValue.value)
        assertEquals("*****",
            items.filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
        val sensitiveTitles = listOf(
            Res.string.TransactionInfo_Fee, Res.string.beam_history_kernel_id, Res.string.beam_history_failure_reason
        ).map { Translator.getString(it) }
        val sensitiveValues = items.filterIsInstance<TransactionInfoViewItem.Value>().filter {
            it.title in sensitiveTitles
        }
        assertEquals(3, sensitiveValues.size)
        assertEquals(true, sensitiveValues.all { it.value == "*****" })
    }

    @Test
    fun getViewItemSections_beamOwnWaitingTransfer_offersCancelWithItsDescription() {
        val statuses = listOf(BeamTransactionStatus.Pending, BeamTransactionStatus.InProgress)
        statuses.forEach { status ->
            val items = beamCancellableItems(beamHistoryRecord(status = status))
            val cancel = items.filterIsInstance<TransactionInfoViewItem.BeamCancel>().single()
            assertEquals("transaction-id", cancel.transactionHash)
            assertEquals(OperationAvailability.Available, cancel.availability)
            assertEquals(beamWallet, cancel.wallet)
            assertEquals(
                Translator.getString(Res.string.beam_cancel_description),
                items.filterIsInstance<TransactionInfoViewItem.Description>().single().text,
            )
        }
    }

    @Test
    fun getViewItemSections_beamTransferCoreCannotCancel_omitsTheCancelRow() {
        val records = listOf(
            beamHistoryRecord(direction = BeamTransactionDirection.Incoming, status = BeamTransactionStatus.InProgress),
            beamHistoryRecord(status = BeamTransactionStatus.Registering),
            beamHistoryRecord(status = BeamTransactionStatus.Confirming),
            beamHistoryRecord(status = BeamTransactionStatus.Completed),
            beamHistoryRecord(status = BeamTransactionStatus.Canceled),
        )
        records.forEach { record ->
            assertEquals(
                emptyList<TransactionInfoViewItem.BeamCancel>(),
                beamCancellableItems(record).filterIsInstance<TransactionInfoViewItem.BeamCancel>(),
            )
        }
    }

    // Offline mode keeps the row clickable: the tap opens the recovery sheet instead of cancelling.
    @Test
    fun getViewItemSections_beamWaitingTransferOffline_keepsTheCancelRowBlocked() {
        val items = beamCancellableItems(
            beamInteractiveHistoryRecord(), operationAvailability = OperationAvailability.BlockedOffline
        )
        assertEquals(
            OperationAvailability.BlockedOffline,
            items.filterIsInstance<TransactionInfoViewItem.BeamCancel>().single().availability,
        )
    }

    @Test
    fun getViewItemSections_beamWaitingTransferWithoutWallet_omitsTheCancelRow() {
        val items = beamItems(beamInteractiveHistoryRecord())
        assertEquals(
            emptyList<TransactionInfoViewItem.BeamCancel>(),
            items.filterIsInstance<TransactionInfoViewItem.BeamCancel>(),
        )
    }

    @Test
    fun getViewItemSections_pendingEvmTransactionWithoutKitInstance_hasNoSpeedUp() {
        val items = pendingEvmItems(evmKitWrapper = null)

        assertEquals(
            emptyList<TransactionInfoViewItem.SpeedUpCancel>(),
            items.filterIsInstance<TransactionInfoViewItem.SpeedUpCancel>(),
        )
    }

    @Test
    fun getViewItemSections_pendingEvmTransactionWithKitInstance_speedUpCarriesThatInstance() {
        val wrapper = mockk<EvmKitWrapper>(relaxed = true)

        val speedUp = pendingEvmItems(evmKitWrapper = wrapper)
            .filterIsInstance<TransactionInfoViewItem.SpeedUpCancel>()
            .single()

        assertSame(wrapper, speedUp.evmKitWrapper)
    }

    @Test
    fun getViewItemSections_swapWithExactDuration_showsRoundedDuration() {
        stubDurationPatterns()

        assertEquals("1h 2m 3s", swapDurationRow(SwapDuration(3_722_600L, approximate = false))?.value)
    }

    @Test
    fun getViewItemSections_swapWithApproximateDuration_showsTildePrefix() {
        stubDurationPatterns()
        stubPattern(Res.string.swap_duration_approximate, "~%1\$s")

        assertEquals("~1m 5s", swapDurationRow(SwapDuration(65_000L, approximate = true))?.value)
    }

    @Test
    fun getViewItemSections_swapWithoutDuration_hasNoSwapDurationRow() {
        assertNull(swapDurationRow(null))
    }

    @Test
    fun getViewItemSections_swapInProgressWithoutRealAmount_showsAwaitingRows() {
        val items = swapItems(externalStatus = TransactionStatus.Processing(0.5f))

        val awaitingTitles = items.filterIsInstance<TransactionInfoViewItem.AwaitingSwapResult>().map { it.title }
        assertEquals(
            listOf(
                Res.string.transaction_info_executed_amount,
                Res.string.TransactionInfo_PriceImpact,
                Res.string.TransactionInfo_ExecutedRate,
                Res.string.TransactionInfo_Price,
            ).map { Translator.getString(it) },
            awaitingTitles
        )
        assertTrue(awaitingTitles.none { title -> items.hasRealRow(title) })
    }

    @Test
    fun getViewItemSections_swapFinishedWithoutRealAmount_hidesResultRows() {
        val items = swapItems(externalStatus = TransactionStatus.Completed)

        assertTrue(items.filterIsInstance<TransactionInfoViewItem.AwaitingSwapResult>().isEmpty())
        assertTrue(items.none { it is TransactionInfoViewItem.ValueColored || it is TransactionInfoViewItem.PriceWithToggle })
    }

    @Test
    fun getViewItemSections_swapInProgressWithRealAmount_showsRealRows() {
        val items = swapItems(externalStatus = TransactionStatus.Pending, amountOutReal = BigDecimal("9"))

        assertTrue(items.filterIsInstance<TransactionInfoViewItem.AwaitingSwapResult>().isEmpty())
        assertTrue(items.any { it is TransactionInfoViewItem.ValueColored })
    }

    private fun List<TransactionInfoViewItem>.hasRealRow(title: String) = any {
        (it is TransactionInfoViewItem.Value && it.title == title) ||
            (it is TransactionInfoViewItem.ValueClickable && it.title == title) ||
            (it is TransactionInfoViewItem.ValueColored && it.title == title) ||
            (it is TransactionInfoViewItem.PriceWithToggle && it.title == title)
    }

    private fun stubDurationPatterns() {
        stubPattern(Res.string.duration_short_hours, "%1\$dh")
        stubPattern(Res.string.duration_short_minutes, "%1\$dm")
        stubPattern(Res.string.duration_short_seconds, "%1\$ds")
    }

    private fun stubPattern(res: StringResource, pattern: String) {
        every { Translator.getString(res, *anyVararg()) } answers { pattern.format(*secondArg<Array<Any>>()) }
    }

    private fun swapDurationRow(swapDuration: SwapDuration?): TransactionInfoViewItem.Value? =
        swapItems(swapDuration)
            .filterIsInstance<TransactionInfoViewItem.Value>()
            .singleOrNull { it.title == Translator.getString(Res.string.swap_duration) }

    private fun swapItems(
        swapDuration: SwapDuration? = null,
        externalStatus: TransactionStatus? = null,
        amountOutReal: BigDecimal? = null,
    ): List<TransactionInfoViewItem> =
        TransactionInfoViewItemFactory(
            offlineOperationGate = mockk(relaxed = true),
            wallet = null,
            blockchainType = BlockchainType.Bitcoin,
            evmKitWrapper = null,
            platformOperationSupport = allOperationsSupported,
            unstoppableProviderTitles = mockk(relaxed = true),
        ).getViewItemSections(
            transactionInfoItem(pendingRecord(toAddress = "")).copy(
                swapAmountOut = BigDecimal.TEN,
                swapAmountIn = BigDecimal.ONE,
                swapCoinCodeOut = "ETH",
                swapCoinCodeIn = "BTC",
                swapProvider = SwapProvider.CHANGENOW,
                swapDuration = swapDuration,
                externalStatus = externalStatus,
                swapAmountOutReal = amountOutReal,
            )
        ).flatten()

    private fun pendingEvmItems(evmKitWrapper: EvmKitWrapper?): List<TransactionInfoViewItem> {
        val gate = mockk<OfflineOperationGate>(relaxed = true) {
            every { availability(beamWallet, any()) } returns OperationAvailability.Available
        }
        return TransactionInfoViewItemFactory(
            offlineOperationGate = gate,
            wallet = beamWallet,
            blockchainType = BlockchainType.BinanceSmartChain,
            evmKitWrapper = evmKitWrapper,
            platformOperationSupport = allOperationsSupported,
            unstoppableProviderTitles = mockk(relaxed = true),
        ).getViewItemSections(transactionInfoItem(pendingEvmRecord())).flatten()
    }

    private fun pendingEvmRecord() = EvmTransactionRecord(
        transaction = mockk<EvmKitTransaction>(relaxed = true) {
            every { hashString } returns "0xabc"
            every { blockNumber } returns null
            every { isFailed } returns false
        },
        token = mockk<Token>(relaxed = true),
        source = mockk<TransactionSource>(relaxed = true) {
            every { blockchain } returns mockk(relaxed = true) {
                every { type } returns BlockchainType.BinanceSmartChain
            }
        },
        protected = false,
        transactionRecordType = TransactionRecordType.EVM_OUTGOING,
        value = tokenValue(value = BigDecimal("-1")),
        to = "0x2222222222222222222222222222222222222222",
    )

    private fun beamCancellableItems(
        record: TransactionRecord,
        operationAvailability: OperationAvailability = OperationAvailability.Available,
    ): List<TransactionInfoViewItem> = beamItems(
        record = record,
        wallet = beamWallet,
        offlineOperationGate = mockk {
            every { availability(beamWallet, true) } returns operationAvailability
        },
    )

    private fun beamItems(
        record: TransactionRecord,
        hideAmount: Boolean = false,
        wallet: Wallet? = null,
        offlineOperationGate: OfflineOperationGate = mockk(relaxed = true),
    ): List<TransactionInfoViewItem> = TransactionInfoViewItemFactory(
        offlineOperationGate = offlineOperationGate,
        wallet = wallet,
        blockchainType = BlockchainType.Beam,
        evmKitWrapper = null,
        platformOperationSupport = allOperationsSupported,
        unstoppableProviderTitles = mockk(relaxed = true),
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

    private val runeToken = Token(
        coin = Coin(uid = "thorchain", name = "THORChain", code = "RUNE"),
        blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null),
        type = TokenType.Native,
        decimals = 8,
    )

    private companion object {
        const val RELAYER_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val BRIDGE_ADDRESS = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436"
        const val DELEGATE_ADDRESS = "0x64fc23203fcc4bcf09fc4b30669131333ba7936b"
        val ZERO_ADDRESS = "0x" + "0".repeat(40)
    }
}
