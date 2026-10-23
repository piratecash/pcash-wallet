package cash.p.terminal.wallet

import io.horizontalsystems.core.common.CommonParcelable
import cash.p.terminal.resources.ManageAccount_NWords
import cash.p.terminal.resources.ManageAccount_NWordsWithPassphrase
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.Translator
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.wallet.data.MnemonicKind
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.network.pirate.domain.enity.PremiumAccountEligibility
import cash.p.terminal.strings.helpers.toMasked
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.WordList
import io.horizontalsystems.core.common.CommonIgnoredOnParcel
import io.horizontalsystems.core.common.CommonParcelize
import java.math.BigInteger
import java.text.Normalizer

@CommonParcelize
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val origin: AccountOrigin,
    val level: Int,
    val isBackedUp: Boolean = false,
    val isFileBackedUp: Boolean = false,
) : CommonParcelable {

    @CommonIgnoredOnParcel
    val supportsBackup: Boolean
        get() = !isHardwareWalletAccount

    @CommonIgnoredOnParcel
    val isHardwareWalletAccount: Boolean
        get() = type is AccountType.HardwareCard || type is AccountType.HardwareDevice

    @CommonIgnoredOnParcel
    val hasAnyBackup = isBackedUp || isFileBackedUp

    @CommonIgnoredOnParcel
    val isWatchAccount: Boolean
        get() = type.isWatchAccountType

    @CommonIgnoredOnParcel
    val nonStandard: Boolean by lazy {
        if (type is AccountType.Mnemonic) {
            val words = type.words.joinToString(separator = " ")
            val passphrase = type.passphrase
            val normalizedWords = words.normalizeNFKD()
            val normalizedPassphrase = passphrase.normalizeNFKD()

            when {
                words != normalizedWords -> true
                passphrase != normalizedPassphrase -> true
                else -> try {
                    Mnemonic().validateStrict(type.words)
                    false
                } catch (exception: Exception) {
                    true
                }
            }
        } else {
            false
        }
    }

    @CommonIgnoredOnParcel
    val nonRecommended: Boolean by lazy {
        if (type is AccountType.Mnemonic) {
            val englishWords = WordList.wordList(Language.English).validWords(type.words)
            val standardPassphrase = PassphraseValidator().containsValidCharacters(type.passphrase)
            !englishWords || !standardPassphrase
        } else {
            false
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is Account) {
            return id == other.id
        }

        return false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

fun Account.canBeDuplicated(): Boolean =
    type is AccountType.Mnemonic || type is AccountType.MnemonicMonero

fun Account.supportsTonConnect(): Boolean =
    type is AccountType.Mnemonic || type is AccountType.HardwareCard

fun Account.isBackedUpOrNotRequired(): Boolean = hasAnyBackup || !supportsBackup

fun Account.canSwap(): Boolean = !isWatchAccount && isBackedUpOrNotRequired()

fun Account.requiresBackupForActions(): Boolean = !isWatchAccount && !isBackedUpOrNotRequired()

fun Account.eligibleForPremium(): Boolean =
    premiumEligibility() == PremiumAccountEligibility.ELIGIBLE

fun Account.premiumEligibility(): PremiumAccountEligibility {
    return when {
        isWatchAccount -> PremiumAccountEligibility.WATCH_ACCOUNT
        type is AccountType.Mnemonic && !hasAnyBackup -> PremiumAccountEligibility.NO_BACKUP
        type is AccountType.Mnemonic && hasAnyBackup -> PremiumAccountEligibility.ELIGIBLE
        type is AccountType.HardwareCard -> PremiumAccountEligibility.ELIGIBLE
        type is AccountType.HardwareDevice -> PremiumAccountEligibility.ELIGIBLE
        else -> PremiumAccountEligibility.WRONG_TYPE
    }
}

@CommonParcelize
sealed class AccountType : CommonParcelable {
    @CommonParcelize
    data class EvmAddress(val address: String) : AccountType()

    @CommonParcelize
    data class SolanaAddress(val address: String) : AccountType()

    @CommonParcelize
    data class TronAddress(val address: String) : AccountType()

    @CommonParcelize
    data class TonAddress(val address: String) : AccountType()

    @CommonParcelize
    data class StellarAddress(val address: String) : AccountType()

    @CommonParcelize
    data class ThorchainAddress(val address: String) : AccountType()

    @CommonParcelize
    data class MayachainAddress(val address: String) : AccountType()

    @CommonParcelize
    data class BitcoinAddress(
        val address: String,
        val blockchainType: BlockchainType,
        val tokenType: TokenType
    ) : AccountType() {

        val serialized: String
            get() = "$address|${blockchainType.uid}|${tokenType.id}"

        companion object {
            fun fromSerialized(serialized: String): BitcoinAddress {
                val split = serialized.split("|")
                return BitcoinAddress(
                    split[0],
                    BlockchainType.fromUid(split[1]),
                    TokenType.fromId(split[2])!!
                )
            }
        }
    }

    @CommonParcelize
    data class Mnemonic(val words: List<String>, val passphrase: String) : AccountType() {
        @CommonIgnoredOnParcel
        val seed by lazy { MnemonicSeed.derive(words, passphrase) }

        @CommonIgnoredOnParcel
        val kind by lazy { MnemonicKind.getKind(words) }

        override fun equals(other: Any?): Boolean {
            return other is Mnemonic
                    && words.toTypedArray().contentEquals(other.words.toTypedArray())
                    && passphrase == other.passphrase
        }

        override fun hashCode(): Int {
            return words.toTypedArray().contentHashCode() + passphrase.hashCode()
        }

        override fun toString(): String {
            return "Mnemonic(words=(${words.toMasked()}), passphrase='${passphrase.toMasked()}')"
        }
    }

    @CommonParcelize
    data class StellarSecretKey(val key: String) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is StellarSecretKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }

        override fun toString(): String {
            return "StellarSecretKey(key='${key.toMasked()}')"
        }
    }

    @CommonParcelize
    data class MnemonicMonero(
        val words: List<String>,
        val password: String,
        val height: Long,
        val walletInnerName: String
    ) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is MnemonicMonero
                    && words.toTypedArray().contentEquals(other.words.toTypedArray())
                    && password == other.password
                    && height == other.height
                    && walletInnerName == other.walletInnerName
        }

        override fun hashCode(): Int {
            var result = words.toTypedArray().contentHashCode()
            result = 31 * result + password.hashCode()
            result = 31 * result + height.hashCode()
            result = 31 * result + walletInnerName.hashCode()
            return result
        }

        override fun toString(): String {
            return "MnemonicMonero(words=(${words.toMasked()}), password='${password.toMasked()}', height=$height, " +
                    "walletInnerName='$walletInnerName')"
        }
    }

    @CommonParcelize
    data class EvmPrivateKey(val key: BigInteger) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is EvmPrivateKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }

        override fun toString(): String {
            return "EvmPrivateKey(key='${key.toString(16).toMasked()}')"
        }
    }

    @CommonParcelize
    data class HdExtendedKey(val keySerialized: String) : AccountType() {
        val hdExtendedKey: HDExtendedKey
            get() = HDExtendedKey(keySerialized)

        override fun equals(other: Any?): Boolean {
            return other is HdExtendedKey && keySerialized.contentEquals(other.keySerialized)
        }

        override fun hashCode(): Int {
            return keySerialized.hashCode()
        }
    }

    @CommonParcelize
    data class ZCashUfvKey(val key: String) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is ZCashUfvKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }

        override fun toString(): String {
            return "ZCashUfvKey(key='${key.toMasked()}')"
        }
    }

    @CommonParcelize
    data class ZCashSaplingKey(val key: String) : AccountType() {
        /** Fail-safe: anything that is not a spending key counts as watch-only. */
        val isSpendingKey: Boolean
            get() = key.startsWith(SPENDING_KEY_HRP_PREFIX)

        override fun equals(other: Any?): Boolean {
            return other is ZCashSaplingKey && key == other.key
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }

        override fun toString(): String {
            return "ZCashSaplingKey(key='${key.toMasked()}')"
        }

        private companion object {
            const val SPENDING_KEY_HRP_PREFIX = "secret-extended-key"
        }
    }

    @CommonParcelize
    data class HardwareCard(
        val cardId: String,
        val backupCardsCount: Int,
        val walletPublicKey: String,
        val signedHashes: Int
    ) : AccountType() {
        override fun equals(other: Any?): Boolean {
            return other is HardwareCard &&
                    cardId == other.cardId &&
                    backupCardsCount == other.backupCardsCount &&
                    walletPublicKey == other.walletPublicKey
        }

        override fun hashCode(): Int {
            return cardId.hashCode()
        }
    }

    @CommonParcelize
    sealed class HardwareDevice : AccountType() {
        abstract val deviceId: String
        abstract val model: String
        abstract val walletPublicKey: String
    }

    @CommonParcelize
    data class TrezorDevice(
        override val deviceId: String,
        override val model: String,
        val firmwareVersion: String,
        override val walletPublicKey: String
    ) : HardwareDevice() {
        override fun equals(other: Any?): Boolean {
            return other is TrezorDevice &&
                    deviceId == other.deviceId &&
                    walletPublicKey == other.walletPublicKey
        }

        override fun hashCode(): Int {
            return deviceId.hashCode()
        }
    }

    @CommonParcelize
    data class LedgerDevice(
        override val deviceId: String,
        override val model: String,
        override val walletPublicKey: String
    ) : HardwareDevice() {
        override fun equals(other: Any?): Boolean {
            return other is LedgerDevice &&
                    deviceId == other.deviceId &&
                    walletPublicKey == other.walletPublicKey
        }

        override fun hashCode(): Int {
            return deviceId.hashCode()
        }
    }

    val description: String
        get() = when (this) {
            is Mnemonic -> {
                val count = words.size

                if (passphrase.isNotBlank()) {
                    Translator.getString(
                        Res.string.ManageAccount_NWordsWithPassphrase,
                        count
                    )
                } else {
                    Translator.getString(
                        Res.string.ManageAccount_NWords,
                        count
                    )
                }
            }

            is BitcoinAddress -> "BTC Address"
            is EvmAddress -> "EVM Address"
            is SolanaAddress -> "Solana Address"
            is TronAddress -> "Tron Address"
            is TonAddress -> "Ton Address"
            is StellarAddress -> "Stellar Address"
            is ThorchainAddress -> "THORChain Address"
            is MayachainAddress -> "Maya Address"
            is StellarSecretKey -> "Stellar Secret Key"
            is EvmPrivateKey -> "EVM Private Key"
            is ZCashUfvKey -> "ZCash UFV Key"
            is ZCashSaplingKey ->
                if (isSpendingKey) "ZCash Sapling Key" else "ZCash Sapling Viewing Key"

            is HardwareCard -> "Hardware card"
            is TrezorDevice -> "Trezor Device"
            is LedgerDevice -> "Ledger Device"
            is MnemonicMonero -> "Monero Wallet"
            is HdExtendedKey -> {
                when (this.hdExtendedKey.derivedType) {
                    HDExtendedKey.DerivedType.Master -> "BIP32 Root Key"
                    HDExtendedKey.DerivedType.Account -> {
                        if (hdExtendedKey.isPublic) {
                            "Account xPubKey"
                        } else {
                            "Account xPrivKey"
                        }
                    }

                    else -> ""
                }
            }
        }

    val supportedDerivations: List<Derivation>
        get() = when (this) {
            is Mnemonic -> {
                listOf(Derivation.bip44, Derivation.bip49, Derivation.bip84, Derivation.bip86)
            }

            is HdExtendedKey -> {
                hdExtendedKey.purposes.map { it.derivation }
            }

            else -> emptyList()
        }

    val hideZeroBalances: Boolean
        get() = false

    val detailedDescription: String
        get() = when (this) {
            is EvmAddress -> this.address.shorten()
            is SolanaAddress -> this.address.shorten()
            is TronAddress -> this.address.shorten()
            is TonAddress -> this.address.shorten()
            is StellarAddress -> this.address.shorten()
            is ThorchainAddress -> this.address.shorten()
            is MayachainAddress -> this.address.shorten()
            is BitcoinAddress -> this.address.shorten()
            else -> this.description
        }

    val canAddTokens: Boolean
        get() = when (this) {
            is Mnemonic, is EvmPrivateKey, is HardwareDevice, is HardwareCard -> true
            else -> false
        }

    val supportsWalletConnect: Boolean
        get() = when (this) {
            is HardwareCard,
            is HardwareDevice,
            is Mnemonic,
            is EvmPrivateKey -> true

            else -> false
        }

    val isWatchAccountType: Boolean
        get() = when (this) {
            is ZCashSaplingKey -> !isSpendingKey

            is ZCashUfvKey,
            is EvmAddress,
            is SolanaAddress,
            is TronAddress,
            is TonAddress,
            is StellarAddress,
            is ThorchainAddress,
            is MayachainAddress,
            is BitcoinAddress -> true

            is HdExtendedKey -> hdExtendedKey.isPublic
            else -> false
        }

    fun evmAddress(chain: Chain) = when (this) {
        is Mnemonic -> Signer.address(seed, chain)
        is EvmPrivateKey -> Signer.address(key)
        else -> null
    }
}

val HDWallet.Purpose.derivation: Derivation
    get() = when (this) {
        HDWallet.Purpose.BIP44 -> Derivation.bip44
        HDWallet.Purpose.BIP49 -> Derivation.bip49
        HDWallet.Purpose.BIP84 -> Derivation.bip84
        HDWallet.Purpose.BIP86 -> Derivation.bip86
    }

val HDWallet.Purpose.tokenTypeDerivation: TokenType.Derivation
    get() = when (this) {
        HDWallet.Purpose.BIP44 -> TokenType.Derivation.Bip44
        HDWallet.Purpose.BIP49 -> TokenType.Derivation.Bip49
        HDWallet.Purpose.BIP84 -> TokenType.Derivation.Bip84
        HDWallet.Purpose.BIP86 -> TokenType.Derivation.Bip86
    }

@CommonParcelize
enum class AccountOrigin(val value: String) : CommonParcelable {
    Created("Created"),
    Restored("Restored");
}

fun String.normalizeNFKD(): String = Normalizer.normalize(this, Normalizer.Form.NFKD)
