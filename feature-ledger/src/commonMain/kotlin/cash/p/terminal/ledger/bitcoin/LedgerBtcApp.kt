package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.domain.LedgerCoinApp
import cash.p.terminal.ledger.domain.specFor
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.bitcoin.BitcoinAddressType
import cash.p.terminal.ledgerkit.bitcoin.LegacyAddressFormat
import cash.p.terminal.ledgerkit.bitcoin.LegacySigningMode
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareAddressPosition
import cash.p.terminal.wallet.hardware.HardwareDerivationPath
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import io.horizontalsystems.hdwalletkit.ExtendedKeyCoinType
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDWallet

/** The Ledger apps of the Bitcoin family; only Bitcoin speaks the PSBT protocol, the rest the legacy btchip one. */
enum class LedgerBtcApp(
    override val appName: String,
    override val coin: HardwareKeyCoin,
    private val extendedKeyCoinType: ExtendedKeyCoinType,
) : LedgerCoinApp {
    Bitcoin("Bitcoin", HardwareKeyCoin.Bitcoin, ExtendedKeyCoinType.Bitcoin),
    Litecoin("Litecoin", HardwareKeyCoin.Litecoin, ExtendedKeyCoinType.Litecoin),
    Dogecoin("Dogecoin", HardwareKeyCoin.Dogecoin, ExtendedKeyCoinType.Dogecoin),
    Dash("Dash", HardwareKeyCoin.Dash, ExtendedKeyCoinType.Dash),
    BitcoinCash("Bitcoin Cash", HardwareKeyCoin.BitcoinCash, ExtendedKeyCoinType.Bitcoin),
    ;

    val usesPsbt: Boolean get() = this == Bitcoin

    /** The extended-key version Trezor stores for the same account, so both vendors share one key format. */
    fun keyVersion(purpose: HDWallet.Purpose): HDExtendedKeyVersion =
        HDExtendedKeyVersion.initFrom(purpose, extendedKeyCoinType, isPrivate = false)

    fun signingMode(purpose: HDWallet.Purpose): LegacySigningMode = when {
        this == BitcoinCash -> LegacySigningMode.BITCOIN_CASH
        purpose == HDWallet.Purpose.BIP44 -> LegacySigningMode.LEGACY
        else -> LegacySigningMode.SEGWIT
    }

    fun addressFormat(purpose: HDWallet.Purpose): LegacyAddressFormat = when {
        this == BitcoinCash -> LegacyAddressFormat.CASHADDR
        purpose == HDWallet.Purpose.BIP49 -> LegacyAddressFormat.P2SH_SEGWIT
        purpose == HDWallet.Purpose.BIP84 -> LegacyAddressFormat.BECH32
        else -> LegacyAddressFormat.LEGACY
    }

    companion object {
        /** The spec and app of a Bitcoin-family [query], or null for other chains. */
        fun specFor(query: TokenQuery): Pair<HardwareKeySpec, LedgerBtcApp>? = entries.specFor(query)
    }
}

internal val HDWallet.Purpose.addressType: BitcoinAddressType
    get() = when (this) {
        HDWallet.Purpose.BIP44 -> BitcoinAddressType.LEGACY
        HDWallet.Purpose.BIP49 -> BitcoinAddressType.NESTED_SEGWIT
        HDWallet.Purpose.BIP84 -> BitcoinAddressType.NATIVE_SEGWIT
        HDWallet.Purpose.BIP86 -> BitcoinAddressType.TAPROOT
    }

internal fun ledgerAddressPath(accountPath: String, position: HardwareAddressPosition): DerivationPath =
    DerivationPath(HardwareDerivationPath.addressPath(accountPath, position).map { it.toUInt() })
