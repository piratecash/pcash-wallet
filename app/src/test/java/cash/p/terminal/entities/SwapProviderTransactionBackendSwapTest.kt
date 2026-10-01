package cash.p.terminal.entities

import cash.p.terminal.modules.multiswap.providers.buildSwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwapProviderTransactionBackendSwapTest {

    @Test
    fun toStatusUrl_knownBackendProvider_returnsProviderTrackLink() {
        val transaction = backendTransaction(providerName = "changelly", externalId = "ext-1")

        assertEquals("changelly.com" to "https://changelly.com/track/ext-1", transaction.toStatusUrl())
    }

    @Test
    fun toStatusUrl_unknownBackendProvider_returnsNull() {
        assertNull(backendTransaction(providerName = "newswap", externalId = "ext-1").toStatusUrl())
    }

    @Test
    fun toStatusUrl_nullExternalId_returnsNull() {
        assertNull(backendTransaction(providerName = "changelly", externalId = null).toStatusUrl())
    }

    @Test
    fun swapProviderDisplayTitle_knownBackendProvider_returnsBrandTitle() {
        assertEquals("Changelly", swapProviderDisplayTitle(SwapProvider.PCASH_BACKEND, "changelly"))
    }

    @Test
    fun swapProviderDisplayTitle_unknownBackendProvider_returnsCapitalisedName() {
        assertEquals("Newswap", swapProviderDisplayTitle(SwapProvider.PCASH_BACKEND, "newswap"))
    }

    private fun backendTransaction(providerName: String, externalId: String?) =
        buildSwapProviderTransaction(SwapProvider.PCASH_BACKEND, "order-1").copy(
            subProviderId = providerName,
            providerExternalId = externalId,
        )
}
