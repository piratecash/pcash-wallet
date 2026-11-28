package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerAddressVerifier
import cash.p.terminal.ledger.domain.LedgerAppKeyFetcher
import cash.p.terminal.ledger.domain.LedgerCoinApp
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledger.domain.ledgerPath
import cash.p.terminal.ledger.domain.specFor
import cash.p.terminal.ledgerkit.solana.SolanaAppClient
import cash.p.terminal.ledgerkit.stellar.StellarAppClient
import cash.p.terminal.ledgerkit.thorchain.ThorchainAppClient
import cash.p.terminal.ledgerkit.ton.TonAppClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.tron.TronAppClient
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import org.stellar.sdk.KeyPair

/** Apps holding one account key per chain, read in the format Trezor stores it. */
enum class LedgerAccountApp(override val appName: String, override val coin: HardwareKeyCoin) : LedgerCoinApp {
    Solana("Solana", HardwareKeyCoin.Solana) {
        override suspend fun readKey(transport: LedgerTransport, spec: HardwareKeySpec, display: Boolean) =
            SolanaAppClient(transport).getPublicKey(spec.ledgerPath(), display)
                .let { LedgerKey(spec, it.toHexString(), it) }
    },
    Tron("Tron", HardwareKeyCoin.Tron) {
        override suspend fun readKey(transport: LedgerTransport, spec: HardwareKeySpec, display: Boolean) =
            TronAppClient(transport).getAddress(spec.ledgerPath(), display)
                .let { LedgerKey(spec, it.address, it.publicKey) }
    },
    Stellar("Stellar", HardwareKeyCoin.Stellar) {
        override suspend fun readKey(transport: LedgerTransport, spec: HardwareKeySpec, display: Boolean) =
            StellarAppClient(transport).getPublicKey(spec.ledgerPath(), display)
                .let { LedgerKey(spec, KeyPair.fromPublicKey(it).accountId, it) }
    },
    Ton("TON", HardwareKeyCoin.Ton) {
        override suspend fun readKey(transport: LedgerTransport, spec: HardwareKeySpec, display: Boolean) =
            TonAppClient(transport).getPublicKey(spec.ledgerPath(), display)
                .let { LedgerKey(spec, it.toHexString(), it) }
    },
    Thorchain("THORChain", HardwareKeyCoin.Thorchain) {
        override suspend fun readKey(transport: LedgerTransport, spec: HardwareKeySpec, display: Boolean) =
            ThorchainAppClient(transport).getAddress(spec.ledgerPath(), display)
                .let { LedgerKey(spec, it.address, it.publicKey) }
    },
    ;

    /** [display] asks the user to confirm the address on the device. */
    abstract suspend fun readKey(transport: LedgerTransport, spec: HardwareKeySpec, display: Boolean): LedgerKey

    companion object {
        // The Tron app shows TRC-10 transfers only with Ledger-signed token names, which we do not carry.
        fun specFor(query: TokenQuery): Pair<HardwareKeySpec, LedgerAccountApp>? =
            entries.specFor(query)?.takeUnless { query.tokenType is TokenType.Trc10 }
    }
}

class LedgerAccountKeyFetcher(private val app: LedgerAccountApp) : LedgerAppKeyFetcher {
    override val appName: String = app.appName

    override fun supports(query: TokenQuery): Boolean = LedgerAccountApp.specFor(query)?.second == app

    override suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey =
        app.readKey(transport, checkNotNull(LedgerAccountApp.specFor(query)).first, display = false)
}

class LedgerAccountAddressVerifier : LedgerAddressVerifier {
    override fun supports(query: TokenQuery): Boolean = LedgerAccountApp.specFor(query) != null

    override suspend fun verify(transport: LedgerTransport, wallet: Wallet, address: String) {
        val (spec, app) = checkNotNull(LedgerAccountApp.specFor(wallet.token.tokenQuery))
        app.readKey(transport, spec, display = true)
    }
}
