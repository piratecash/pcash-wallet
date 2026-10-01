package cash.p.terminal.modules.restoreaccount.restoreprivatekey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyModule.RestoreError.EmptyText
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyModule.RestoreError.NoValidKey
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyModule.RestoreError.NonPrivateKey
import cash.p.terminal.modules.restoreaccount.restoreprivatekey.RestorePrivateKeyModule.RestoreError.NotSupportedDerivedType
import cash.p.terminal.modules.address.ZcashKeyParser
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.stellarkit.StellarKit
import java.math.BigInteger

class RestorePrivateKeyViewModel(
    accountFactory: IAccountFactory,
) : ViewModel() {

    val defaultName = accountFactory.getNextAccountName()
    var accountName: String = defaultName
        get() = field.ifBlank { defaultName }
        private set

    private var text = ""

    var inputState by mutableStateOf<DataState.Error?>(null)
        private set

    fun onEnterName(name: String) {
        accountName = name
    }

    fun onEnterPrivateKey(input: String) {
        inputState = null
        text = input
    }

    fun resolveAccountType(): AccountType? {
        inputState = null
        return try {
            accountType(text)
        } catch (e: Exception) {
            inputState = DataState.Error(Exception(Translator.getString(e.errorText)))
            null
        }
    }

    private val Exception.errorText
        get() = when (this) {
            NonPrivateKey -> R.string.restore_private_key_is_view_only
            else -> R.string.Restore_PrivateKey_InvalidKey
        }

    @Throws(Exception::class)
    private fun accountType(text: String): AccountType {
        val textCleaned = text.trim()

        if (textCleaned.isEmpty()) {
            throw EmptyText
        }

        if (isValidEthereumPrivateKey(textCleaned)) {
            val privateKey = Signer.privateKey(textCleaned)
            return AccountType.EvmPrivateKey(privateKey)
        }

        if (StellarKit.isValidSecretKey(textCleaned)) {
            return AccountType.StellarSecretKey(textCleaned)
        }

        if (ZcashKeyParser.isSaplingSpendingKey(textCleaned)) {
            return AccountType.ZCashSaplingKey(textCleaned)
        }

        if (ZcashKeyParser.isUfvk(textCleaned) || ZcashKeyParser.isSaplingViewingKey(textCleaned)) {
            throw NonPrivateKey
        }

        val extendedKey = try {
            HDExtendedKey(textCleaned)
        } catch (e: Throwable) {
            throw NoValidKey
        }

        if (extendedKey.isPublic) {
            throw NonPrivateKey
        }

        return when (extendedKey.derivedType) {
            HDExtendedKey.DerivedType.Master,
            HDExtendedKey.DerivedType.Account -> AccountType.HdExtendedKey(extendedKey.serializePrivate())

            else -> throw NotSupportedDerivedType
        }
    }

    private fun isValidEthereumPrivateKey(privateKeyHex: String): Boolean {
        try {
            //key should be 32 bytes long
            privateKeyHex.hexToByteArray().let {
                if (it.size != 32) {
                    return false
                }
            }

            // Convert the hex private key to a BigInteger
            val privateKeyBigInt = BigInteger(privateKeyHex, 16)

            // Define the order of the secp256k1 curve (n)
            val secp256k1Order = BigInteger(
                "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141",
                16
            )

            // Check if the private key is greater than zero and less than the order
            return privateKeyBigInt > BigInteger.ZERO && privateKeyBigInt < secp256k1Order
        } catch (e: NumberFormatException) {
            return false
        }
    }
}
