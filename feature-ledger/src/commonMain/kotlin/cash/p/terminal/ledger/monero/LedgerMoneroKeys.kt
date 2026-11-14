package cash.p.terminal.ledger.monero

import cash.p.terminal.ledger.domain.LedgerAppKeyFetcher
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor

const val LEDGER_MONERO_APP = "Monero"

/** Stores the standard address with the public view and spend keys; the app gives them without asking. */
class LedgerMoneroKeyFetcher : LedgerAppKeyFetcher {
    override val appName: String = LEDGER_MONERO_APP

    override fun supports(query: TokenQuery): Boolean = moneroSpec(query) != null

    override suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey {
        val spec = checkNotNull(moneroSpec(query))
        val response = transport.send(GET_PUBLIC_KEYS)
        check(response.size >= ADDRESS_END) { "Monero app returned ${response.size} bytes for its public keys" }
        return LedgerKey(
            spec = spec,
            key = response.copyOfRange(PUBLIC_KEYS_SIZE, ADDRESS_END).decodeToString(),
            publicKey = response.copyOf(PUBLIC_KEYS_SIZE),
        )
    }

    private fun moneroSpec(query: TokenQuery) =
        HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)?.takeIf { it.coin == HardwareKeyCoin.Monero }

    private companion object {
        // The app fixes its protocol version on the first command; wallet2 speaks version 4 as its CLA.
        val GET_PUBLIC_KEYS = Apdu(cla = 0x04, ins = 0x20, p1 = 0x01, p2 = 0x00, data = byteArrayOf(0))
        const val PUBLIC_KEYS_SIZE = 64
        const val ADDRESS_END = PUBLIC_KEYS_SIZE + 95
    }
}
