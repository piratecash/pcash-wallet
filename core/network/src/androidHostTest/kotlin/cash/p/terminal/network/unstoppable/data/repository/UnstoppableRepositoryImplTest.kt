package cash.p.terminal.network.unstoppable.data.repository

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.unstoppable.api.UnstoppableApi
import cash.p.terminal.network.unstoppable.data.entity.ProviderDto
import cash.p.terminal.network.unstoppable.data.entity.TrackResponseDto
import cash.p.terminal.network.unstoppable.data.entity.request.TrackRequestDto
import cash.p.terminal.network.unstoppable.data.mapper.UnstoppableMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class UnstoppableRepositoryImplTest {

    private val api = mockk<UnstoppableApi>()
    private val repository = UnstoppableRepositoryImpl(api, UnstoppableMapper())
    private val json = Json { ignoreUnknownKeys = true }
    private val request = SwapProviderStatusRequest(
        transactionId = "uuid-1",
        destinationAddress = "dest",
        inboundTxHash = "0xhash",
    )

    private fun trackDto(body: String) = json.decodeFromString<TrackResponseDto>(body)

    private val completed = """{"status":"completed","toAmount":"0.5","legs":[
        {"type":"native_send","status":"completed","hash":"0xaa","chainId":"1"}]}"""
    private val notStarted = """{"status":"not_started","toAmount":"","legs":[
        {"type":"native_send","status":"not_started","hash":"","fromAmount":""}]}"""

    @Test
    fun getTransactionStatus_completedTrack_returnsStatusAndPositiveAmount() = runTest {
        coEvery { api.track(TrackRequestDto("uuid-1", "0xhash")) } returns trackDto(completed)

        val result = requireNotNull(repository.getTransactionStatus(request))

        assertEquals(TransactionStatusEnum.FINISHED, result.status)
        assertEquals(BigDecimal("0.5"), result.amountOutReal)
        assertNull(result.finishedAt)
    }

    @Test
    fun getTransactionStatus_notStartedWithBlankAmount_returnsWaitingWithoutAmount() = runTest {
        coEvery { api.track(any()) } returns trackDto(notStarted)

        val result = requireNotNull(repository.getTransactionStatus(request))

        assertEquals(TransactionStatusEnum.WAITING, result.status)
        assertNull(result.amountOutReal)
    }

    @Test
    fun getTransactionStatus_zeroAmount_returnsNullAmount() = runTest {
        coEvery { api.track(any()) } returns trackDto("""{"status":"swapping","toAmount":"0"}""")

        assertNull(requireNotNull(repository.getTransactionStatus(request)).amountOutReal)
    }

    @Test
    fun getTransactionStatus_unknownStatus_returnsNull() = runTest {
        coEvery { api.track(any()) } returns trackDto("""{"status":"unknown"}""")

        assertNull(repository.getTransactionStatus(request))
    }

    @Test
    fun observeTrack_afterStatusPoll_emitsParsedTrack() = runTest {
        coEvery { api.track(any()) } returns trackDto(completed)
        assertNull(repository.observeTrack("uuid-1").first())

        repository.getTransactionStatus(request)

        val track = requireNotNull(repository.observeTrack("uuid-1").first())
        assertEquals(TransactionStatusEnum.FINISHED, track.status)
        assertEquals("0xaa", track.legs.single().hash)
        assertNull(repository.observeTrack("other").first())
    }

    @Test
    fun getProviders_calledTwice_hitsApiOnce() = runTest {
        coEvery { api.providers() } returns listOf(ProviderDto(provider = "PEGASUS"))

        repository.getProviders()
        val second = repository.getProviders()

        assertEquals("PEGASUS", second.single().provider)
        coVerify(exactly = 1) { api.providers() }
    }

    @Test
    fun getProviders_afterFailure_retriesAndCachesSuccess() = runTest {
        coEvery { api.providers() } throws IllegalStateException("boom") andThen listOf(ProviderDto(provider = "CCE"))

        assertTrue(runCatching { repository.getProviders() }.isFailure)
        assertEquals("CCE", repository.getProviders().single().provider)
        repository.getProviders()

        coVerify(exactly = 2) { api.providers() }
    }
}
