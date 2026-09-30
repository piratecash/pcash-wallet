package cash.p.terminal.core

import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Pool
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.keyPools
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.ExtendedKeyCoinType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet

/** Which ZEC address specs an account owns — the single source of truth for `supports()` and for wallet creation. */
fun AccountType.zcashAddressSpecs(): Set<AddressSpecType> = when (this) {
    is AccountType.Mnemonic -> AddressSpecType.entries.toSet()

    is AccountType.ZCashUfvKey -> specsOfKey(key)

    is AccountType.ZCashSaplingKey -> setOf(AddressSpecType.Shielded)

    is AccountType.HdExtendedKey -> transparentSpecOf(hdExtendedKey)

    is AccountType.TrezorDevice ->
        if (TrezorModelSupport.isSupported(TrezorModel.fromInternalModel(model), BlockchainType.Zcash)) {
            setOf(AddressSpecType.Transparent)
        } else {
            emptySet()
        }

    is AccountType.BitcoinAddress,
    is AccountType.EvmAddress,
    is AccountType.EvmPrivateKey,
    is AccountType.HardwareCard,
    is AccountType.MnemonicMonero,
    is AccountType.SolanaAddress,
    is AccountType.StellarAddress,
    is AccountType.StellarSecretKey,
    is AccountType.TonAddress,
    is AccountType.TronAddress -> emptySet()
}

/** A key the SDK cannot classify owns nothing — never fall back to the full set. */
private fun specsOfKey(key: String): Set<AddressSpecType> =
    tryOrNull { ZcashSdk.keyPools(key, ZcashNetwork.MAIN) }
        ?.let { pools -> POOL_SPECS.filterKeys { it in pools }.values.toSet() }
        .orEmpty()

private fun transparentSpecOf(key: HDExtendedKey): Set<AddressSpecType> =
    if (key.derivedType == HDExtendedKey.DerivedType.Account &&
        key.coinTypes.contains(ExtendedKeyCoinType.Bitcoin) &&
        key.purposes.contains(HDWallet.Purpose.BIP44)
    ) {
        setOf(AddressSpecType.Transparent)
    } else {
        emptySet()
    }

private val POOL_SPECS = mapOf(
    Pool.TRANSPARENT to AddressSpecType.Transparent,
    Pool.SAPLING to AddressSpecType.Shielded,
    Pool.ORCHARD to AddressSpecType.Unified,
)
