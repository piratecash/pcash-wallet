package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.CreateRequiredTokensUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.expandedZcashAddressSpecQueries
import cash.p.terminal.wallet.isMonero
import cash.p.terminal.wallet.latestAccountOr
import cash.p.terminal.wallet.useCases.ScanToAddUseCase

class CreateRequiredTokensUseCaseImpl(
    private val walletActivator: WalletActivator,
    private val userDeletedWalletManager: UserDeletedWalletManager,
    private val scanToAddUseCase: ScanToAddUseCase,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val accountManager: IAccountManager
) : CreateRequiredTokensUseCase {
    override suspend fun invoke(account: Account, tokenQueries: List<TokenQuery>) {
        val expandedTokenQueries = tokenQueries.expandedZcashAddressSpecQueries(account.type)
        userDeletedWalletManager.unmarkAsDeleted(account.id, expandedTokenQueries.map { it.id })

        if (!account.isHardwareWalletAccount) {
            walletActivator.activateWalletsSuspended(account, expandedTokenQueries)
            return
        }

        val hardwareId = when (val type = account.type) {
            is AccountType.HardwareCard -> type.cardId
            is AccountType.TrezorDevice -> type.deviceId
            else -> error("Not a hardware wallet account")
        }
        val existingKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)
        val missingQueries = expandedTokenQueries.filterNot { it.isDerivedIn(existingKeys) }

        if (missingQueries.isNotEmpty()) {
            scanToAddUseCase.addTokensByScan(
                blockchainsToDerive = missingQueries,
                cardId = hardwareId,
                accountId = account.id
            )
        }

        // Firmware can refuse a derivation the app still asked for; a wallet activated without its
        // key has no adapter, so only what the device actually derived may be activated. Monero is
        // provisioned from the device wallet itself and never stores a key row.
        val derivedKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)
        val activatableQueries =
            expandedTokenQueries.filter { it.isMonero || it.isDerivedIn(derivedKeys) }

        // The scan may have healed a legacy Trezor model; re-read so the activation
        // below sees the healed model instead of the stale snapshot.
        walletActivator.activateWalletsSuspended(
            accountManager.latestAccountOr(account),
            activatableQueries
        )
    }

    private fun TokenQuery.isDerivedIn(keys: List<HardwarePublicKey>): Boolean = keys.any {
        it.blockchainType == blockchainType.uid && it.tokenType == tokenType
    }
}
