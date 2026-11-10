package cash.p.terminal.core.adapters

import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.multiswap.providers.AndroidUnstoppableProviderTitles
import cash.p.terminal.modules.transactions.TransactionItem
import cash.p.terminal.modules.transactions.TransactionViewItemFactory
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.AccountAddress
import io.horizontalsystems.tonkit.models.Action
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.NftTransfer
import io.horizontalsystems.tonkit.models.TonTransfer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class TonTransactionConverterTest {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(listOf("word"), ""),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val v4r2Address = Address.parse(V4R2_RAW)
    private val w5Address = Address.parse(W5_RAW)
    private val swapStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val swapMatcher = mockk<SwapTransactionMatcher>()

    @Test
    fun createTransactionRecord_ownTransferV4r2ToW5_recordsKeepSeparateUidsAndSameHash() {
        val event = transferEvent(from = v4r2Address, to = w5Address)

        val v4r2Record = converter(TokenType.Native, v4r2Address, TonWalletVersion.V4R2)
            .createTransactionRecord(event)
        val w5Record = converter(TokenType.TonW5Native, w5Address, TonWalletVersion.W5)
            .createTransactionRecord(event)

        assertEquals(EVENT_ID, v4r2Record.uid)
        assertNotEquals(v4r2Record.uid, w5Record.uid)
        assertEquals(EVENT_ID, v4r2Record.transactionHash)
        assertEquals(EVENT_ID, w5Record.transactionHash)
        assertIs<TonTransactionRecord.Action.Type.Send>(v4r2Record.actions.single().type)
        assertIs<TonTransactionRecord.Action.Type.Receive>(w5Record.actions.single().type)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun createTransactionRecord_outgoingNft_mapsToSendWithNftValue() {
        val type = nftActionType(sender = v4r2Address, recipient = w5Address, comment = null)

        assertEquals(
            TonTransactionRecord.Action.Type.Send(
                value = nftValue(BigDecimal("-1")),
                to = wallet(w5Address).address.toUserFriendly(bounceable = false),
                sentToSelf = false,
                comment = null,
            ),
            type,
        )
    }

    @Test
    fun createTransactionRecord_incomingNft_mapsToReceiveWithNftValue() {
        val type = nftActionType(sender = w5Address, recipient = v4r2Address, comment = null)

        assertEquals(
            TonTransactionRecord.Action.Type.Receive(
                value = nftValue(BigDecimal.ONE),
                from = wallet(w5Address).address.toUserFriendly(bounceable = false),
                to = wallet(v4r2Address).address.toUserFriendly(bounceable = false),
                comment = null,
            ),
            type,
        )
    }

    @Test
    fun createTransactionRecord_nftWithComment_keepsCommentOnBothDirections() {
        val sent = nftActionType(sender = v4r2Address, recipient = w5Address, comment = "for you")
        val received = nftActionType(sender = w5Address, recipient = v4r2Address, comment = "from me")

        assertEquals("for you", assertIs<TonTransactionRecord.Action.Type.Send>(sent).comment)
        assertEquals("from me", assertIs<TonTransactionRecord.Action.Type.Receive>(received).comment)
    }

    @Test
    fun createTransactionRecord_nftSentToOwnAddress_marksSentToSelf() {
        val type = nftActionType(sender = v4r2Address, recipient = v4r2Address, comment = null)

        assertEquals(true, assertIs<TonTransactionRecord.Action.Type.Send>(type).sentToSelf)
    }

    @Test
    fun createTransactionRecord_nftWithoutSenderOrRecipient_staysUnsupported() {
        val minted = nftActionType(sender = null, recipient = v4r2Address, comment = null)
        val sold = nftActionType(sender = v4r2Address, recipient = null, comment = null)

        assertIs<TonTransactionRecord.Action.Type.Unsupported>(minted)
        assertIs<TonTransactionRecord.Action.Type.Unsupported>(sold)
    }

    @Test
    fun createTransactionRecord_nftBetweenOtherAccounts_staysUnsupported() {
        val thirdParty = Address.parse(THIRD_PARTY_RAW)

        val type = nftActionType(sender = thirdParty, recipient = w5Address, comment = null)

        assertIs<TonTransactionRecord.Action.Type.Unsupported>(type)
    }

    @Test
    fun convertToViewItemCached_tonNftSend_showsNftInRed() = runTest {
        val viewItem = viewItemFactory().convertToViewItemCached(nftItem(sender = v4r2Address, recipient = w5Address))

        assertEquals("NFT", viewItem.primaryValue?.value)
        assertEquals(ColorName.Lucian, viewItem.primaryValue?.color)
    }

    @Test
    fun convertToViewItemCached_tonNftReceive_showsNftInGreen() = runTest {
        val viewItem = viewItemFactory().convertToViewItemCached(nftItem(sender = w5Address, recipient = v4r2Address))

        assertEquals("NFT", viewItem.primaryValue?.value)
        assertEquals(ColorName.Remus, viewItem.primaryValue?.color)
    }

    @Test
    fun convertToViewItemCached_tonNftSendWithSwapOfSameAmount_staysNftAndLeavesSwapUnlinked() = runTest {
        val factory = viewItemFactory(providerSwap = mockk(relaxed = true))

        val viewItem = factory.convertToViewItemCached(nftItem(sender = v4r2Address, recipient = w5Address))

        assertEquals("NFT", viewItem.primaryValue?.value)
        verify(exactly = 0) { swapStorage.setOutgoingRecordUid(any(), any()) }
    }

    @Test
    fun convertToViewItemCached_tonNftReceiveWithSwapOfSameAmount_staysNft() = runTest {
        val factory = viewItemFactory(providerSwap = mockk(relaxed = true))

        val viewItem = factory.convertToViewItemCached(nftItem(sender = w5Address, recipient = v4r2Address))

        assertEquals("NFT", viewItem.primaryValue?.value)
    }

    @Test
    fun convertToViewItemCached_tonCoinSendWithSwapOfSameAmount_linksProviderSwap() = runTest {
        val factory = viewItemFactory(providerSwap = mockk(relaxed = true))
        val record = converter(TokenType.Native, v4r2Address, TonWalletVersion.V4R2)
            .createTransactionRecord(transferEvent(from = v4r2Address, to = w5Address))

        factory.convertToViewItemCached(TransactionItem(record, currencyValue = null, lastBlockInfo = null))

        verify(exactly = 1) { swapStorage.setOutgoingRecordUid(any(), record.uid) }
    }

    private fun nftActionType(sender: Address?, recipient: Address?, comment: String?) =
        converter(TokenType.Native, v4r2Address, TonWalletVersion.V4R2)
            .createTransactionRecord(nftEvent(sender, recipient, comment))
            .actions.single().type

    private fun nftValue(value: BigDecimal) = TransactionValue.NftValue(
        mintAddress = Address.parse(NFT_RAW).toUserFriendly(bounceable = true),
        value = value,
        tokenName = null,
        tokenSymbol = "NFT",
    )

    private fun nftItem(sender: Address, recipient: Address) = TransactionItem(
        record = converter(TokenType.Native, v4r2Address, TonWalletVersion.V4R2)
            .createTransactionRecord(nftEvent(sender, recipient, comment = null)),
        currencyValue = null,
        lastBlockInfo = null,
    )

    private fun wallet(address: Address) = AccountAddress(address, name = null, isScam = false, isWallet = true)

    private fun nftEvent(sender: Address?, recipient: Address?, comment: String?) = event(
        Action(
            type = Action.Type.NftTransfer,
            status = Action.Status.OK,
            tonTransfer = null,
            jettonTransfer = null,
            jettonBurn = null,
            jettonMint = null,
            contractDeploy = null,
            jettonSwap = null,
            smartContractExec = null,
            nftTransfer = NftTransfer(
                nft = Address.parse(NFT_RAW),
                sender = sender?.let(::wallet),
                recipient = recipient?.let(::wallet),
                comment = comment,
            ),
        )
    )

    private fun viewItemFactory(providerSwap: SwapProviderTransaction? = null): TransactionViewItemFactory {
        mockkObject(DateHelper)
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"
        val contactsRepository = mockk<ContactsRepository> {
            every { getContactsFiltered(any(), addressQuery = any()) } returns emptyList()
        }
        val addressLabelManager = mockk<AddressLabelManager> {
            every { label(any(), any()) } returns null
        }
        val balanceHiddenManager = mockk<BalanceHiddenManager> {
            every { balanceHidden } returns false
            every { isTransactionInfoHidden(any()) } returns false
            every { isTransactionInfoHiddenForWallet(any(), any()) } returns false
        }
        val localStorage = mockk<TransactionSettingsStorage> {
            every { addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        }
        val poisonAddressManager = mockk<PoisonAddressManager> {
            coEvery { getPoisonStatus(any<TransactionRecord>()) } returns PoisonStatus.BLOCKCHAIN
        }
        every { swapStorage.getByOutgoingRecordUid(any()) } returns null
        every { swapStorage.getByTokenIn(any(), any(), any()) } returns providerSwap
        every { swapMatcher.findMatchingSwap(any()) } returns providerSwap
        return TransactionViewItemFactory(
            addressMetadataManager = AddressMetadataManager(contactsRepository, addressLabelManager),
            balanceHiddenManager = balanceHiddenManager,
            swapProviderTransactionsStorage = swapStorage,
            swapTransactionMatcher = swapMatcher,
            numberFormatter = mockk(relaxed = true),
            marketKit = mockk(relaxed = true),
            localStorage = localStorage,
            poisonAddressManager = poisonAddressManager,
            unstoppableProviderTitles = AndroidUnstoppableProviderTitles(),
        )
    }

    private fun converter(tokenType: TokenType, address: Address, version: TonWalletVersion): TonTransactionConverter {
        val token = Token(
            coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
            blockchain = Blockchain(BlockchainType.Ton, "The Open Network", null),
            type = tokenType,
            decimals = 9,
        )
        return TonTransactionConverter(
            address = address,
            coinManager = mockk(relaxed = true),
            source = TransactionSource(token.blockchain, account, tokenType.meta),
            baseToken = token,
            walletVersion = version,
        )
    }

    private fun transferEvent(from: Address, to: Address) = event(
        Action(
            type = Action.Type.TonTransfer,
            status = Action.Status.OK,
            tonTransfer = TonTransfer(
                sender = wallet(from),
                recipient = wallet(to),
                amount = BigInteger.valueOf(1_000_000_000L),
                comment = null,
            ),
            jettonTransfer = null,
            jettonBurn = null,
            jettonMint = null,
            contractDeploy = null,
            jettonSwap = null,
            smartContractExec = null,
        )
    )

    private fun event(action: Action) = Event(
        id = EVENT_ID,
        lt = 1L,
        timestamp = 1_715_000_000L,
        scam = false,
        inProgress = false,
        extra = 0L,
        actions = listOf(action),
    )

    private companion object {
        const val EVENT_ID = "ton-event-id"
        const val V4R2_RAW = "0:1111111111111111111111111111111111111111111111111111111111111111"
        const val W5_RAW = "0:2222222222222222222222222222222222222222222222222222222222222222"
        const val THIRD_PARTY_RAW = "0:3333333333333333333333333333333333333333333333333333333333333333"
        const val NFT_RAW = "0:4444444444444444444444444444444444444444444444444444444444444444"
    }
}
