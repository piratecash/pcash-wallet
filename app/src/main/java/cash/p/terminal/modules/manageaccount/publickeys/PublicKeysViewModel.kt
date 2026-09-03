package cash.p.terminal.modules.manageaccount.publickeys

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ExtendedPublicKey
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ZcashViewKey
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyModule.DisplayKeyType.AccountPublicKey
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class PublicKeysViewModel(
    account: Account,
    evmBlockchainManager: EvmBlockchainManager,
) : ViewModel() {

    var viewState by mutableStateOf(PublicKeysModule.ViewState())
        private set

    private val zcashKeyExporter: ZcashKeyExporter by inject(ZcashKeyExporter::class.java)

    init {
        val evmAddress: String? = when (val accountType = account.type) {
            is AccountType.Mnemonic -> {
                val chain = evmBlockchainManager.getChain(BlockchainType.Ethereum)
                Signer.address(accountType.words, accountType.passphrase, chain).eip55
            }

            is AccountType.EvmPrivateKey -> {
                Signer.address(accountType.key).eip55
            }

            is AccountType.EvmAddress -> accountType.address
            is AccountType.SolanaAddress -> accountType.address
            is AccountType.TronAddress -> accountType.address
            else -> null
        }

        val hdExtendedKey = (account.type as? AccountType.HdExtendedKey)?.hdExtendedKey
        var accountPublicKey = AccountPublicKey(false)

        val publicKey = if (account.type is AccountType.Mnemonic) {
            accountPublicKey = AccountPublicKey(true)
            val seed = Mnemonic().toSeed(
                (account.type as AccountType.Mnemonic).words,
                (account.type as AccountType.Mnemonic).passphrase
            )
            HDExtendedKey(seed, HDWallet.Purpose.BIP44)
        } else if (hdExtendedKey?.derivedType == HDExtendedKey.DerivedType.Master) {
            accountPublicKey = AccountPublicKey(true)
            hdExtendedKey
        } else if (hdExtendedKey?.derivedType == HDExtendedKey.DerivedType.Account && !hdExtendedKey.isPublic) {
            hdExtendedKey
        } else if (hdExtendedKey?.derivedType == HDExtendedKey.DerivedType.Account && hdExtendedKey.isPublic) {
            hdExtendedKey
        } else {
            null
        }

        viewState = PublicKeysModule.ViewState(
            evmAddress = evmAddress,
            extendedPublicKey = publicKey?.let { ExtendedPublicKey(it, accountPublicKey) }
        )

        loadZcashViewKey(account.type)
    }

    /** The key belongs to this account alone — an adapter lookup would resolve the active one. */
    private fun loadZcashViewKey(accountType: AccountType) {
        viewModelScope.launch(CoroutineExceptionHandler { _, _ -> }) {
            val viewKey = zcashKeyExporter.viewingKey(accountType)
            viewState = viewState.copy(
                zcashViewKey = viewKey?.let(::ZcashViewKey),
                zcashViewKeyFailed = viewKey == null && zcashKeyExporter.supportsViewingKey(accountType)
            )
        }
    }
}
