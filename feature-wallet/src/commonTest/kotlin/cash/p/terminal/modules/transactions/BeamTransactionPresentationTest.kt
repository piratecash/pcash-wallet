package cash.p.terminal.modules.transactions

import org.jetbrains.compose.resources.StringResource
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import io.horizontalsystems.core.Translator
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.beam_history_address_expired
import cash.p.terminal.resources.beam_history_canceled
import cash.p.terminal.resources.beam_history_expired
import cash.p.terminal.resources.beam_history_in_progress
import cash.p.terminal.resources.beam_history_waiting_for_receiver
import cash.p.terminal.resources.beam_history_waiting_for_sender

class BeamTransactionPresentationTest {

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any()) } answers { "string:${firstArg<StringResource>().key}" }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun statusTitle_inProgressByDirection_waitsForCounterparty() {
        assertEquals(
            Res.string.beam_history_waiting_for_receiver,
            beamInteractiveHistoryRecord(direction = BeamTransactionDirection.Outgoing).statusTitle
        )
        assertEquals(
            Res.string.beam_history_waiting_for_sender,
            beamInteractiveHistoryRecord(direction = BeamTransactionDirection.Incoming).statusTitle
        )
        assertEquals(
            Res.string.beam_history_in_progress,
            beamInteractiveHistoryRecord(direction = BeamTransactionDirection.Self).statusTitle
        )
    }

    @Test
    fun statusTitle_failedWithTransactionExpired_showsExpired() {
        assertEquals(
            Res.string.beam_history_expired,
            beamHistoryRecord(status = BeamTransactionStatus.Failed, failureReason = "TransactionExpired").statusTitle
        )
    }

    @Test
    fun statusTitle_failedWithOtherReason_showsGenericFailed() {
        assertEquals(
            Res.string.Transactions_Failed,
            beamHistoryRecord(status = BeamTransactionStatus.Failed, failureReason = "Canceled").statusTitle
        )
    }

    @Test
    fun failureReasonText_knownReasons_mapsToFriendlyText() {
        assertEquals(
            Translator.getString(Res.string.beam_history_canceled),
            beamHistoryRecord(failureReason = "Canceled").failureReasonText
        )
        assertEquals(
            Translator.getString(Res.string.beam_history_expired),
            beamHistoryRecord(failureReason = "TransactionExpired").failureReasonText
        )
        assertEquals(
            Translator.getString(Res.string.beam_history_address_expired),
            beamHistoryRecord(failureReason = "ExpiredAddressProvided").failureReasonText
        )
        assertEquals(
            Translator.getString(Res.string.Swap_ErrorInsufficientBalance),
            beamHistoryRecord(failureReason = "NoInputs").failureReasonText
        )
    }

    @Test
    fun failureReasonText_unknownReason_returnsRawValue() {
        assertEquals(
            "SomeOtherCoreReason",
            beamHistoryRecord(failureReason = "SomeOtherCoreReason").failureReasonText
        )
    }

    @Test
    fun failureReasonText_noFailure_returnsNull() {
        assertNull(beamHistoryRecord(failureReason = null).failureReasonText)
    }
}
