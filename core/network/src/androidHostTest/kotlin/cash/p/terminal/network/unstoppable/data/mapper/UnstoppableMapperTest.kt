package cash.p.terminal.network.unstoppable.data.mapper

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.unstoppable.data.entity.ApprovalDto
import cash.p.terminal.network.unstoppable.data.entity.ExecutionDto
import cash.p.terminal.network.unstoppable.data.entity.RouteDto
import cash.p.terminal.network.unstoppable.data.entity.SignableTxDto
import cash.p.terminal.network.unstoppable.data.entity.TrackResponseDto
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrackLeg.Status
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrackLeg.Type
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class UnstoppableMapperTest {

    private val mapper = UnstoppableMapper()

    @Test
    fun mapTrackStatus_allKnownStatuses_mapToExpectedEnum() {
        val expected = mapOf(
            "not_started" to TransactionStatusEnum.WAITING,
            "pending" to TransactionStatusEnum.CONFIRMING,
            "swapping" to TransactionStatusEnum.EXCHANGING,
            "completed" to TransactionStatusEnum.FINISHED,
            "refunded" to TransactionStatusEnum.REFUNDED,
            "failed" to TransactionStatusEnum.FAILED,
            // Non-terminal in-progress: the swap can still auto-resolve to refunded/completed,
            // so it must NOT map to FAILED.
            "action_required" to TransactionStatusEnum.WAITING,
        )

        expected.forEach { (status, expectedEnum) ->
            assertEquals("status=$status", expectedEnum, mapper.mapTrackStatus(status))
        }
    }

    @Test
    fun mapTrackStatus_unrecognizedStatus_returnsNull() {
        assertNull(mapper.mapTrackStatus("unknown"))
        assertNull(mapper.mapTrackStatus("some_future_status"))
    }

    @Test
    fun mapRoute_executionDto_carriesFieldsThroughAndKeepsMinBuyAmountNull() {
        val dto = RouteDto(
            expectedBuyAmount = BigDecimal("100"),
            minBuyAmount = null,
            execution = ExecutionDto(
                method = "signed_transaction",
                chain = "1",
                transactions = listOf(
                    SignableTxDto(kind = "evm", to = "0xTo", from = "0xFrom", value = "0x0", data = "0xabcdef", gas = "0x5208"),
                ),
                approval = ApprovalDto(spender = "0xSpender"),
            ),
            uuid = "uuid-1",
        )

        val route = mapper.mapRoute(dto)

        assertNull(route.minBuyAmount)
        val execution = requireNotNull(route.execution)
        assertEquals("1", execution.chain)
        val tx = execution.transactions.single()
        assertEquals("0xTo", tx.to)
        assertEquals("0xFrom", tx.from)
        assertEquals("0x0", tx.value)
        assertEquals("0xabcdef", tx.data)
        assertEquals("0x5208", tx.gas)
        assertEquals("0xSpender", execution.approval?.spender)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun track(body: String) = mapper.mapTrack(json.decodeFromString<TrackResponseDto>(body))

    private val nearNotStarted = """{"chainId":"litecoin","hash":"","type":"swap","status":"not_started",
        "fromAsset":"LTC.LTC","fromAmount":"0.15","toAsset":"ETH.ETH","toAmount":"","toAddress":"0x4A4F",
        "providers":["NEAR"],"meta":{"sellAmountUsd":"8.68"},"legs":[
        {"chainId":"litecoin","hash":"","type":"native_send","status":"not_started","fromAsset":"LTC.LTC","fromAmount":"0.15","toAsset":"LTC.LTC","toAmount":"0.15","toAddress":"Lb5Y"},
        {"chainId":"near","hash":"","type":"swap","status":"not_started","fromAsset":"LTC.LTC","fromAmount":"0.15","toAsset":"ETH.ETH","toAmount":"","toAddress":"0x4A4F"},
        {"chainId":"1","hash":"","type":"native_send","status":"not_started","fromAsset":"ETH.ETH","fromAmount":"","toAsset":"ETH.ETH","toAmount":"","toAddress":"0x4A4F"}]}"""

    @Test
    fun mapTrack_nearNotStartedWithBlankHashesAndAmounts_mapsBlanksToNull() {
        val track = track(nearNotStarted)

        assertEquals(TransactionStatusEnum.WAITING, track.status)
        assertNull(track.toAmount)
        assertEquals(listOf(Type.NativeSend, Type.Swap, Type.NativeSend), track.legs.map { it.type })
        assertTrue(track.legs.all { it.status == Status.NotStarted })
        assertTrue(track.legs.all { it.hash == null })
        assertEquals(BigDecimal("0.15"), track.legs[0].fromAmount)
        assertNull(track.legs[1].toAmount)
        assertNull(track.legs[2].fromAmount)
        assertEquals("near", track.legs[1].chainId)
        assertEquals("Lb5Y", track.legs[0].toAddress)
    }

    @Test
    fun mapTrack_swappingWithMixedLegStatuses_mapsLegStatuses() {
        val track = track(
            """{"status":"swapping","toAmount":"","legs":[
            {"type":"native_send","status":"completed","hash":"0xaa","chainId":"litecoin"},
            {"type":"swap","status":"swapping","hash":"","chainId":"near"},
            {"type":"native_send","status":"not_started","chainId":"1"}]}"""
        )

        assertEquals(TransactionStatusEnum.EXCHANGING, track.status)
        assertEquals(listOf(Status.Completed, Status.InProgress, Status.NotStarted), track.legs.map { it.status })
        assertEquals("0xaa", track.legs[0].hash)
        assertNull(track.legs[1].hash)
    }

    @Test
    fun mapTrack_completedPegasusTwoLegs_mapsAmountsAndHashes() {
        val track = track(
            """{"status":"completed","toAmount":"0.1969481","legs":[
            {"chainId":"1","hash":"0xdeb2","type":"native_send","status":"completed","fromAsset":"ETH.ETH","fromAmount":"0.005","toAsset":"ETH.ETH","toAmount":"0.005","toAddress":"0xea0e"},
            {"chainId":"litecoin","hash":"f0f8","type":"native_send","status":"completed","fromAsset":"LTC.LTC","fromAmount":"0.1969481","toAsset":"LTC.LTC","toAmount":"0.1969481","toAddress":"ltc1q"}]}"""
        )

        assertEquals(TransactionStatusEnum.FINISHED, track.status)
        assertEquals(BigDecimal("0.1969481"), track.toAmount)
        assertEquals(2, track.legs.size)
        assertTrue(track.legs.all { it.status == Status.Completed })
        assertEquals("f0f8", track.legs[1].hash)
        assertEquals("LTC.LTC", track.legs[1].toAsset)
        assertFalse(track.actionRequired)
    }

    @Test
    fun mapTrack_actionRequiredWithPauseReason_flagsActionRequiredAndLegInProgress() {
        val track = track(
            """{"status":"action_required","meta":{"pauseReason":"aml"},"legs":[
            {"type":"swap","status":"action_required"}]}"""
        )

        assertTrue(track.actionRequired)
        assertEquals("aml", track.pauseReason)
        assertEquals(TransactionStatusEnum.WAITING, track.status)
        assertEquals(Status.InProgress, track.legs.single().status)
    }

    @Test
    fun mapTrack_unknownLegTypeAndStatuses_mapsToOtherAndNotStarted() {
        val track = track(
            """{"status":"unknown","legs":[
            {"type":"bridge","status":"unknown"},
            {"type":"swap","status":"refunded"},
            {"type":"swap","status":"failed"}]}"""
        )

        assertNull(track.status)
        assertEquals(Type.Other, track.legs[0].type)
        assertEquals(Status.NotStarted, track.legs[0].status)
        assertEquals(Status.Failed, track.legs[1].status)
        assertEquals(Status.Failed, track.legs[2].status)
    }

    @Test
    fun mapTrack_noLegsAndNullAmount_mapsToEmptyLegs() {
        val track = track("""{"status":"pending","toAmount":null}""")

        assertTrue(track.legs.isEmpty())
        assertNull(track.toAmount)
    }
}
