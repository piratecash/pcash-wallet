package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.R
import cash.p.terminal.strings.helpers.Translator
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BeamTransactionPresentationTest {

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any()) } answers { "string:${firstArg<Int>()}" }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun statusTitle_inProgressByDirection_waitsForCounterparty() {
        assertEquals(
            R.string.beam_history_waiting_for_receiver,
            beamInteractiveHistoryRecord(direction = BeamTransactionDirection.Outgoing).statusTitle
        )
        assertEquals(
            R.string.beam_history_waiting_for_sender,
            beamInteractiveHistoryRecord(direction = BeamTransactionDirection.Incoming).statusTitle
        )
        assertEquals(
            R.string.beam_history_in_progress,
            beamInteractiveHistoryRecord(direction = BeamTransactionDirection.Self).statusTitle
        )
    }

    @Test
    fun statusTitle_failedWithTransactionExpired_showsExpired() {
        assertEquals(
            R.string.beam_history_expired,
            beamHistoryRecord(status = BeamTransactionStatus.Failed, failureReason = "TransactionExpired").statusTitle
        )
    }

    @Test
    fun statusTitle_failedWithOtherReason_showsGenericFailed() {
        assertEquals(
            R.string.Transactions_Failed,
            beamHistoryRecord(status = BeamTransactionStatus.Failed, failureReason = "Canceled").statusTitle
        )
    }

    @Test
    fun failureReasonText_knownReasons_mapsToFriendlyText() {
        assertEquals(
            Translator.getString(R.string.beam_history_canceled),
            beamHistoryRecord(failureReason = "Canceled").failureReasonText
        )
        assertEquals(
            Translator.getString(R.string.beam_history_expired),
            beamHistoryRecord(failureReason = "TransactionExpired").failureReasonText
        )
        assertEquals(
            Translator.getString(R.string.beam_history_address_expired),
            beamHistoryRecord(failureReason = "ExpiredAddressProvided").failureReasonText
        )
        assertEquals(
            Translator.getString(R.string.Swap_ErrorInsufficientBalance),
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
