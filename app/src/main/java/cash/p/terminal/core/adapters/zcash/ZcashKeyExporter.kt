package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.AccountType
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveSaplingViewingKey
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.deriveTransparentAccountKey
import cash.p.zcash.deriveUfvk
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

enum class ZcashPrivateKeyType { Transparent, Shielded }

/**
 * Exports ZEC private and viewing keys for an account. Driven by [AccountType] alone: it is
 * the exact discriminator between a Sapling spending key and a ZEC-T extended key, which a
 * [ZcashKey]-based rule would collapse into the same case.
 */
class ZcashKeyExporter(private val dispatcherProvider: DispatcherProvider) {

    fun privateKeyTypes(type: AccountType): List<ZcashPrivateKeyType> = when {
        type is AccountType.Mnemonic -> ZcashPrivateKeyType.entries
        type is AccountType.ZCashSaplingKey && type.isSpendingKey -> listOf(ZcashPrivateKeyType.Shielded)
        else -> emptyList()
    }

    suspend fun export(type: AccountType, keyType: ZcashPrivateKeyType): String? {
        if (keyType !in privateKeyTypes(type)) return null
        return withContext(dispatcherProvider.io) {
            when (type) {
                is AccountType.Mnemonic -> exportFromPhrase(type, keyType)
                is AccountType.ZCashSaplingKey -> type.key
                else -> null
            }
        }
    }

    /** Words are not validated on import, so a phrase of blank words carries no key. */
    fun supportsViewingKey(type: AccountType): Boolean = when (type) {
        is AccountType.ZCashUfvKey, is AccountType.ZCashSaplingKey -> true
        is AccountType.Mnemonic -> type.words.any { it.isNotBlank() }
        else -> false
    }

    /**
     * A [ZCashUfvKey][AccountType.ZCashUfvKey] and a viewing-only
     * [ZCashSaplingKey][AccountType.ZCashSaplingKey] return their own stored key; a spending
     * key and a phrase are derived offline. `null` for every other account type.
     */
    suspend fun viewingKey(type: AccountType): String? {
        if (!supportsViewingKey(type)) return null
        return withContext(dispatcherProvider.io) {
            when (type) {
                is AccountType.ZCashUfvKey -> type.key

                is AccountType.ZCashSaplingKey -> if (type.isSpendingKey) {
                    tryOrNull { ZcashSdk.deriveSaplingViewingKey(type.key, ZcashNetwork.MAIN) }
                } else {
                    type.key
                }

                is AccountType.Mnemonic -> tryOrNull {
                    ZcashSdk.deriveUfvk(
                        phrase = type.words.joinToString(" "),
                        network = ZcashNetwork.MAIN,
                        passphrase = type.passphrase,
                    )
                }

                else -> null
            }
        }
    }

    private suspend fun exportFromPhrase(type: AccountType.Mnemonic, keyType: ZcashPrivateKeyType): String? {
        val phrase = type.words.joinToString(" ")
        val passphrase = type.passphrase
        return when (keyType) {
            ZcashPrivateKeyType.Transparent -> tryOrNull {
                ZcashSdk.deriveTransparentAccountKey(phrase, ZcashNetwork.MAIN, passphrase)
            }

            ZcashPrivateKeyType.Shielded -> {
                val usk = tryOrNull {
                    ZcashSdk.deriveSpendingKey(phrase, ZcashNetwork.MAIN, passphrase = passphrase)
                } ?: return null
                try {
                    saplingSpendingKey(usk)
                } finally {
                    usk.fill(0)
                }
            }
        }
    }
}
