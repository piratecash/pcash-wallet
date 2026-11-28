package cash.p.terminal.ledger.monero

import cash.p.terminal.ledger.di.ledgerAppKeyFetchers
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerMoneroKeysTest {

    private val fetcher = LedgerMoneroKeyFetcher()

    @Test
    fun fetchKey_publicKeysCommand_storesAddressWithViewAndSpendKeys() = runTest {
        val device = FakeMoneroApp()

        val key = fetcher.fetchKey(device, MONERO)

        assertEquals(ADDRESS, key.key)
        assertContentEquals(VIEW_KEY + SPEND_KEY, key.publicKey)
        assertEquals("m/44'/128'/0'", key.spec.derivationPath)
        assertContentEquals(GET_PUBLIC_KEYS, device.sent.single())
    }

    @Test
    fun holdsKey_otherDeviceAddress_returnsFalse() = runTest {
        val device = FakeMoneroApp()

        assertTrue(fetcher.holdsKey(device, MONERO, ADDRESS, VIEW_KEY + SPEND_KEY))
        assertFalse(fetcher.holdsKey(device, MONERO, ADDRESS.reversed(), VIEW_KEY + SPEND_KEY))
    }

    @Test
    fun supports_nativeMonero_isLedgerToken() {
        assertTrue(ledgerAppKeyFetchers.supports(MONERO))
        assertFalse(ledgerAppKeyFetchers.supports(TokenQuery(BlockchainType.Monero, TokenType.Unsupported("x", "y"))))
    }

    private class FakeMoneroApp : LedgerTransport {
        val sent = mutableListOf<ByteArray>()

        override suspend fun exchange(apdu: ByteArray): ByteArray {
            sent += apdu
            return VIEW_KEY + SPEND_KEY + ADDRESS.encodeToByteArray() + byteArrayOf(0x90.toByte(), 0x00)
        }

        override fun close() = Unit
    }

    private companion object {
        val MONERO = TokenQuery(BlockchainType.Monero, TokenType.Native)
        val VIEW_KEY = ByteArray(32) { 1 }
        val SPEND_KEY = ByteArray(32) { 2 }
        val ADDRESS = "4" + "A".repeat(94)
        val GET_PUBLIC_KEYS = byteArrayOf(0x04, 0x20, 0x01, 0x00, 0x01, 0x00)
    }
}
