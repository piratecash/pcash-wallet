package cash.p.terminal.modules.swapprogress

import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.UnstoppableProviderTitles
import cash.p.terminal.modules.transactionInfo.TransactionInfoModule
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrack
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrackLeg
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnstoppableSwapStepsFactoryTest {
    private val formatter = mockk<IAppNumberFormatter> {
        every { formatCoinFull(any(), any(), any()) } answers { "${firstArg<BigDecimal>().toPlainString()} ${secondArg<String?>()}" }
    }
    private val titles = object : UnstoppableProviderTitles {
        override fun title(apiId: String?) = "Pegasus"
    }
    private val legExplorer = mockk<SwapLegExplorer> {
        every { explorer(any(), any(), any()) } returns null
        every { explorer("litecoin", "account-1", "ltc-hash") } returns
            TransactionInfoModule.ExplorerData("Litecoin explorer", "https://ltc/ltc-hash")
    }
    private val factory = UnstoppableSwapStepsFactory(formatter, titles, legExplorer)

    @Before
    fun mockTranslations() {
        mockkObject(Translator)
        every { Translator.getString(any<StringResource>(), *anyVararg()) } answers {
            val args = secondArg<Array<Any>>()
            (listOf(firstArg<StringResource>().key) + args).joinToString("|")
        }
    }

    @After
    fun clearTranslations() = unmockkObject(Translator)

    private fun swap(
        status: String = "waiting",
        depositTransactionHash: String? = null,
        amountOutReal: String? = null,
    ) = SwapProviderTransaction(
        date = 1L,
        outgoingRecordUid = null,
        transactionId = "uuid",
        status = status,
        provider = SwapProvider.UNSTOPPABLE,
        coinUidIn = "litecoin",
        blockchainTypeIn = "litecoin",
        amountIn = BigDecimal("0.15"),
        addressIn = "in",
        coinUidOut = "ethereum",
        blockchainTypeOut = "ethereum",
        amountOut = BigDecimal("0.0036"),
        addressOut = "out",
        accountId = "account-1",
        subProviderId = "PEGASUS",
        depositTransactionHash = depositTransactionHash,
        amountOutReal = amountOutReal?.let(::BigDecimal),
    )

    private fun leg(
        type: UnstoppableTrackLeg.Type,
        status: UnstoppableTrackLeg.Status,
        from: String,
        to: String = from,
        hash: String? = null,
        chainId: String? = "litecoin",
        fromAmount: String? = "0.15",
        toAmount: String? = fromAmount,
        toAddress: String? = "deposit-address",
    ) = UnstoppableTrackLeg(
        type, status, hash, chainId, from, fromAmount?.let(::BigDecimal), to, toAmount?.let(::BigDecimal), toAddress,
    )

    private fun track(
        vararg legs: UnstoppableTrackLeg,
        actionRequired: Boolean = false,
        pauseReason: String? = null,
        toAmount: String? = null,
    ) = UnstoppableTrack(TransactionStatusEnum.WAITING, actionRequired, pauseReason, toAmount?.let(::BigDecimal), legs.toList())

    private val threeLegs = track(
        leg(
            UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "LTC.LTC",
            hash = "ltc-hash",
        ),
        leg(
            UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.InProgress, "LTC.LTC", "ETH.USDT-0xdac",
            chainId = "near", toAmount = "12.5",
        ),
        leg(
            UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.NotStarted, "ETH.USDT-0xdac",
            chainId = "1", fromAmount = "12.5",
        ),
    )

    private fun routeWithoutPayout(
        receiveStatus: UnstoppableTrackLeg.Status = UnstoppableTrackLeg.Status.NotStarted,
        receive: UnstoppableTrackLeg = leg(
            UnstoppableTrackLeg.Type.NativeSend, receiveStatus, "ETH.ETH", chainId = "1", fromAmount = null,
        ),
    ) = track(
        leg(UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "LTC.LTC"),
        leg(UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.InProgress, "LTC.LTC", "ETH.ETH", chainId = "near", toAmount = null),
        receive,
    )

    private fun List<TransactionInfoViewItem>.values() =
        filterIsInstance<TransactionInfoViewItem.ValueClickable>().map { it.title to it.value }

    @Test
    fun sections_threeLegs_headerAndCardPerLegWithParsedAssetCodes() {
        val sections = factory.sections(swap(), threeLegs, emptyList(), hideAmount = false)

        assertEquals(6, sections.size)
        val headers = sections.mapNotNull { (it.singleOrNull() as? TransactionInfoViewItem.Description)?.text }
        assertEquals(
            listOf(
                "swap_progress_step_header|1|swap_progress_leg_send|LTC",
                "swap_progress_step_header|2|swap_progress_leg_swap|LTC|USDT",
                "swap_progress_step_header|3|swap_progress_leg_receive|USDT",
            ),
            headers,
        )
    }

    @Test
    fun sections_completedAndPendingLegs_statusHashExplorerAndApproximateAmount() {
        val cards = factory.sections(swap(), threeLegs, emptyList(), false)
            .filter { it.first() is TransactionInfoViewItem.Status }

        val first = cards[0]
        assertTrue((first[0] as TransactionInfoViewItem.Status).status === TransactionStatus.Completed)
        assertEquals(listOf("offline_transaction_amount" to "0.15 LTC"), first.values())
        assertEquals("ltc-hash", first.filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
        assertEquals("TransactionInfo_ButtonViewOnExplorerName|Litecoin explorer", first.filterIsInstance<TransactionInfoViewItem.Explorer>().single().title)

        val swapCard = cards[1]
        assertTrue((swapCard[0] as TransactionInfoViewItem.Status).status is TransactionStatus.Processing)
        assertEquals(
            listOf("swap_you_send" to "≈ 0.15 LTC", "Swap_YouGet" to "≈ 12.5 USDT"),
            swapCard.values(),
        )

        val last = cards[2]
        assertTrue((last[0] as TransactionInfoViewItem.Status).status === TransactionStatus.Pending)
        assertTrue(last.none { it is TransactionInfoViewItem.TransactionHash || it is TransactionInfoViewItem.Explorer })
    }

    @Test
    fun sections_intermediateSwapWithoutToAmount_rowOmitted() {
        val track = track(
            leg(UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.NotStarted, "LTC.LTC", "ETH.USDC", toAmount = null),
            leg(UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.NotStarted, "ETH.USDC", "ETH.ETH", toAmount = null),
        )

        val cards = factory.sections(swap(), track, emptyList(), false).filter { it.first() is TransactionInfoViewItem.Status }

        assertEquals(listOf("swap_you_send" to "≈ 0.15 LTC"), cards[0].values())
        assertEquals("Swap_YouGet" to "≈ 0.0036 ETH", cards[1].values().last())
    }

    @Test
    fun sections_finalLegsWithoutAmounts_showQuotedAmountApproximate() {
        val cards = factory.sections(swap(), routeWithoutPayout(), emptyList(), false)
            .filter { it.first() is TransactionInfoViewItem.Status }

        assertEquals(listOf("swap_you_send" to "≈ 0.15 LTC", "Swap_YouGet" to "≈ 0.0036 ETH"), cards[1].values())
        assertEquals(listOf("offline_transaction_amount" to "≈ 0.0036 ETH"), cards[2].values())
    }

    @Test
    fun sections_receiveLegAmountArrives_replacesExpectedAmount() {
        val track = routeWithoutPayout(
            receive = leg(
                UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "ETH.ETH",
                chainId = "1", fromAmount = "0.0035",
            ),
        )

        val receive = factory.sections(swap(), track, emptyList(), false).last()

        assertEquals(listOf("offline_transaction_amount" to "0.0035 ETH"), receive.values())
    }

    @Test
    fun sections_receiveLegWithOnlyToAmount_showsItInsteadOfExpected() {
        val track = routeWithoutPayout(
            receive = leg(
                UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "ETH.ETH",
                chainId = "1", fromAmount = null, toAmount = "0.0035",
            ),
        )

        val receive = factory.sections(swap(), track, emptyList(), false).last()

        assertEquals(listOf("offline_transaction_amount" to "0.0035 ETH"), receive.values())
    }

    @Test
    fun sections_completedReceiveLegWithoutAmount_quoteStaysApproximate() {
        val receive = factory.sections(swap(), routeWithoutPayout(receiveStatus = UnstoppableTrackLeg.Status.Completed), emptyList(), false).last()

        assertEquals(listOf("offline_transaction_amount" to "≈ 0.0036 ETH"), receive.values())
    }

    @Test
    fun sections_storedRealPayoutWithoutLegAmount_completedLegShowsExactAmount() {
        val track = routeWithoutPayout(receiveStatus = UnstoppableTrackLeg.Status.Completed)

        val receive = factory.sections(swap(amountOutReal = "0.00355"), track, emptyList(), false).last()

        assertEquals(listOf("offline_transaction_amount" to "0.00355 ETH"), receive.values())
    }

    @Test
    fun sections_trackPayoutWithoutLegAmount_winsOverStoredPayoutAndQuote() {
        val route = routeWithoutPayout(receiveStatus = UnstoppableTrackLeg.Status.Completed)
        val track = track(*route.legs.toTypedArray(), toAmount = "0.00357")

        val receive = factory.sections(swap(amountOutReal = "0.00355"), track, emptyList(), false).last()

        assertEquals(listOf("offline_transaction_amount" to "0.00357 ETH"), receive.values())
    }

    @Test
    fun sections_depositLegWithoutHash_usesStoredDepositHashForIdExplorerAndEmail() {
        val sections = factory.sections(
            swap(depositTransactionHash = "ltc-hash"), routeWithoutPayout(), listOf(SupportContact.Email("a@b.c")), false,
        )
        val cards = sections.filter { it.first() is TransactionInfoViewItem.Status }

        assertEquals("ltc-hash", cards[0].filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
        assertEquals(1, cards[0].filterIsInstance<TransactionInfoViewItem.Explorer>().size)
        assertTrue(cards.drop(1).none { card -> card.any { it is TransactionInfoViewItem.TransactionHash } })
        assertTrue((sections.last().single() as TransactionInfoViewItem.SupportEmail).body.startsWith("swap_support_email_deposit_tx|ltc-hash"))
    }

    @Test
    fun sections_depositLegWithHash_apiHashWinsOverStoredOne() {
        val cards = factory.sections(swap(depositTransactionHash = "stored-hash"), threeLegs, emptyList(), false)
            .filter { it.first() is TransactionInfoViewItem.Status }

        assertEquals("ltc-hash", cards[0].filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
    }

    @Test
    fun sections_hideAmount_masksAmounts() {
        val card = factory.sections(swap(), threeLegs, emptyList(), hideAmount = true)
            .first { it.first() is TransactionInfoViewItem.Status }

        assertEquals(listOf("offline_transaction_amount" to "*****"), card.values())
    }

    @Test
    fun sections_hideAmount_masksLegHash() {
        val card = factory.sections(swap(), threeLegs, emptyList(), hideAmount = true)
            .first { it.first() is TransactionInfoViewItem.Status }

        assertEquals("*****", card.filterIsInstance<TransactionInfoViewItem.TransactionHash>().single().transactionHash)
    }

    @Test
    fun sections_actionRequired_bannerFirstWithReason() {
        val track = track(
            leg(UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.InProgress, "LTC.LTC", "ETH.ETH"),
            actionRequired = true,
            pauseReason = "aml",
        )

        val banner = factory.sections(swap(), track, emptyList(), false).first().single()

        assertEquals(
            "swap_progress_action_required_aml swap_progress_contact_support",
            (banner as TransactionInfoViewItem.WarningMessage).message,
        )
    }

    @Test
    fun sections_emptyLegs_singleStatusCardFromStoredStatus() {
        val sections = factory.sections(swap("finished"), track(), emptyList(), false)

        val status = sections.single().single() as TransactionInfoViewItem.Status
        assertTrue(status.status === TransactionStatus.Completed)
    }

    @Test
    fun sections_nullTrackAndContacts_statusThenSupportSection() {
        val contacts = listOf(SupportContact.Telegram("https://t.me/x"), SupportContact.Email("a@b.c"))

        val sections = factory.sections(swap(), null, contacts, false)

        assertEquals(2, sections.size)
        val support = sections.last()
        val telegram = support[0] as TransactionInfoViewItem.Explorer
        assertEquals("swap_support_telegram|Pegasus", telegram.title)
        assertEquals("https://t.me/x", telegram.url)
        assertEquals("swap_support_email|Pegasus", (support[1] as TransactionInfoViewItem.SupportEmail).title)
    }

    @Test
    fun sections_contactsWithDepositLeg_emailBodyHasHashAndAddress() {
        val sections = factory.sections(swap(), threeLegs, listOf(SupportContact.Email("a@b.c")), false)

        val email = sections.last().single() as TransactionInfoViewItem.SupportEmail
        assertEquals(
            "swap_support_email_deposit_tx|ltc-hash\nswap_support_email_deposit_address|deposit-address",
            email.body,
        )
    }

    @Test
    fun sections_noContacts_noSupportSection() {
        val sections = factory.sections(swap(), threeLegs, emptyList(), false)

        assertTrue(sections.none { section -> section.any { it is TransactionInfoViewItem.SupportEmail } })
    }

    @Test
    fun progressSummary_firstOpenLeg_isCurrentStep() {
        val summary = threeLegs.progressSummary()

        assertEquals(SwapProgressSummary(currentStep = 2, totalSteps = 3, allCompleted = false), summary)
    }

    @Test
    fun progressSummary_allCompleted_currentStepIsTotal() {
        val done = track(
            leg(UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "LTC.LTC"),
            leg(UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.Completed, "LTC.LTC", "ETH.ETH"),
        )

        assertEquals(SwapProgressSummary(2, 2, true), done.progressSummary())
    }

    @Test
    fun progressSummary_noLegs_returnsNull() {
        assertNull(track().progressSummary())
    }

    private fun completedRoute(
        depositHash: String? = "ltc-hash",
        swapHash: String? = "near-hash",
        swapToAmount: String? = "0.0036",
        receiveStatus: UnstoppableTrackLeg.Status = UnstoppableTrackLeg.Status.Completed,
    ) = track(
        leg(UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "LTC.LTC", hash = depositHash),
        leg(
            UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.Completed, "LTC.LTC", "ETH.ETH",
            hash = swapHash, chainId = "near", toAmount = swapToAmount,
        ),
        leg(UnstoppableTrackLeg.Type.NativeSend, receiveStatus, "ETH.ETH", hash = "0xeth", chainId = "1", fromAmount = "0.0036"),
    )

    @Test
    fun isSettled_allLegsCompletedWithHashesAndAmounts_true() {
        assertTrue(completedRoute().isSettled(depositHash = null))
    }

    @Test
    fun isSettled_depositHashOnlyStoredLocally_true() {
        assertTrue(completedRoute(depositHash = null).isSettled(depositHash = "ltc-hash"))
    }

    @Test
    fun isSettled_legHashMissing_false() {
        assertFalse(completedRoute(swapHash = null).isSettled(depositHash = "ltc-hash"))
    }

    @Test
    fun isSettled_swapResultAmountMissing_false() {
        assertFalse(completedRoute(swapToAmount = null).isSettled(depositHash = null))
    }

    @Test
    fun isSettled_legStillInProgress_false() {
        assertFalse(completedRoute(receiveStatus = UnstoppableTrackLeg.Status.InProgress).isSettled(depositHash = null))
    }

    @Test
    fun isSettled_legFailed_trueEvenWithLaterLegsNotStarted() {
        val failed = track(
            leg(UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, "LTC.LTC", hash = "ltc-hash"),
            leg(UnstoppableTrackLeg.Type.Swap, UnstoppableTrackLeg.Status.Failed, "LTC.LTC", "ETH.ETH", chainId = "near", toAmount = null),
            leg(UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.NotStarted, "ETH.ETH", chainId = "1"),
        )

        assertTrue(failed.isSettled(depositHash = null))
    }

    @Test
    fun isSettled_noLegs_false() {
        assertFalse(track().isSettled(depositHash = "ltc-hash"))
    }
}
