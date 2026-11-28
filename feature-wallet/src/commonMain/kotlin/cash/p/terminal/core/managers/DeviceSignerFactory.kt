package cash.p.terminal.core.managers

import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.zcash.ZcashTransactionSigner
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.ledger.account.LedgerSolanaClearSigning
import cash.p.terminal.ledger.account.LedgerSolanaSigner
import cash.p.terminal.ledger.account.LedgerStellarSigner
import cash.p.terminal.ledger.account.LedgerThorchainSigner
import cash.p.terminal.ledger.account.LedgerTonSigner
import cash.p.terminal.ledger.account.LedgerTronSigner
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledger.zcash.LedgerZcashSigner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.solana.core.PublicKey
import io.horizontalsystems.thorchainkit.transaction.ThorchainExternalSigner
import io.horizontalsystems.tonkit.core.TonTransactionDeviceSigner
import org.koin.java.KoinJavaComponent.inject
import org.stellar.sdk.KeyPair
import org.stellar.sdk.Network

/** Signers of the single-key kits for a USB/BLE device account; [key] is the stored key of that chain. */
class DeviceSignerFactory(
    private val solanaHardwareSigners: SolanaHardwareSigners,
    private val tronHardwareSigners: TronHardwareSigners,
    private val stellarHardwareSigners: StellarHardwareSigners,
) {
    // Lazy: desktop has neither Ledger bindings nor Ledger accounts.
    private val ledgerAppAccess: LedgerAppAccess by inject(LedgerAppAccess::class.java)
    private val ledgerSolanaClearSigning: LedgerSolanaClearSigning by inject(LedgerSolanaClearSigning::class.java)

    /** [online] lets a Ledger fetch what shows a token transfer in clear; offline signing makes no request. */
    fun solana(
        account: Account,
        type: AccountType.HardwareDevice,
        key: HardwarePublicKey,
        online: Boolean,
    ) = when (type) {
        is AccountType.TrezorDevice -> solanaHardwareSigners.trezorAccount(
            publicKey = PublicKey(key.key.value.hexToByteArray()),
            hardwarePublicKey = key
        )
        is AccountType.LedgerDevice ->
            LedgerSolanaSigner(ledgerKey(account, key), ledgerSolanaClearSigning.takeIf { online })
    }

    fun tron(account: Account, type: AccountType.HardwareDevice, key: HardwarePublicKey) = when (type) {
        is AccountType.TrezorDevice -> tronHardwareSigners.trezorSigner(
            expectedAddressBase58 = key.key.value,
            derivationPath = key.derivationPath
        )
        is AccountType.LedgerDevice -> LedgerTronSigner(ledgerKey(account, key))
    }

    fun stellar(account: Account, type: AccountType.HardwareDevice, key: HardwarePublicKey) = when (type) {
        is AccountType.TrezorDevice -> stellarHardwareSigners.trezorSigner(
            publicKey = KeyPair.fromAccountId(key.key.value).publicKey,
            derivationPath = key.derivationPath,
            networkPassphrase = Network.PUBLIC.networkPassphrase
        )
        is AccountType.LedgerDevice -> LedgerStellarSigner(ledgerKey(account, key))
    }

    fun ton(account: Account, type: AccountType.HardwareDevice, key: HardwarePublicKey): TonTransactionDeviceSigner =
        LedgerTonSigner(ledgerOnlyKey(account, type, key))

    fun thorchain(account: Account, type: AccountType.HardwareDevice, key: HardwarePublicKey): ThorchainExternalSigner =
        LedgerThorchainSigner(ledgerOnlyKey(account, type, key))

    /** Trezor signs only a transparent bundle, through its own signer. */
    fun ledgerZcash(account: Account, key: HardwarePublicKey): ZcashTransactionSigner {
        val signer = LedgerZcashSigner(ledgerKey(account, key))
        return ZcashTransactionSigner { wallet, _, transaction -> signer.sign(wallet, transaction) }
    }

    // Trezor has no TON or THORChain app, so it never stores their keys.
    private fun ledgerOnlyKey(
        account: Account,
        type: AccountType.HardwareDevice,
        key: HardwarePublicKey,
    ): LedgerSigningKey {
        if (type !is AccountType.LedgerDevice) throw UnsupportedAccountException()
        return ledgerKey(account, key)
    }

    private fun ledgerKey(account: Account, key: HardwarePublicKey) = LedgerSigningKey(ledgerAppAccess, account, key)
}
