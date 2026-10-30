package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.premium.data.model.BnbPremiumAddress
import cash.p.terminal.premium.domain.HardwareBnbAddressProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GetBnbAddressUseCaseImpl(
    private val seedToEvmAddressUseCase: SeedToEvmAddressUseCase,
    private val premiumDatabaseProvider: PremiumDatabaseProvider,
    private val hardwareBnbAddressProvider: HardwareBnbAddressProvider
) : GetBnbAddressUseCase {
    private val bnbPremiumAddressDao get() = premiumDatabaseProvider.database.bnbPremiumAddressDao()

    override suspend fun getAddress(
        account: Account,
        requestScanTangemIfNotFound: Boolean
    ): String? {
        return when (account.type) {
            is AccountType.Mnemonic -> {
                val mnemonicType = account.type as AccountType.Mnemonic
                seedToEvmAddressUseCase(mnemonicType.words, mnemonicType.passphrase)
            }

            is AccountType.HardwareDevice,
            is AccountType.HardwareCard -> {
                val address =
                    withContext(Dispatchers.IO) { bnbPremiumAddressDao.getByAccount(account.id)?.address }
                if (address == null && requestScanTangemIfNotFound && account.type is AccountType.HardwareCard) {
                    hardwareBnbAddressProvider.scanAddress(account)?.also {
                        bnbPremiumAddressDao.insert(BnbPremiumAddress(accountId = account.id, address = it))
                    }
                } else {
                    address
                }
            }

            else -> null
        }
    }

    override suspend fun saveAddress(address: String, accountId: String) =
        withContext(Dispatchers.IO) {
            bnbPremiumAddressDao.insert(BnbPremiumAddress(accountId = accountId, address = address))
        }

    override suspend fun deleteBnbAddress(accountId: String) = withContext(Dispatchers.IO) {
        bnbPremiumAddressDao.deleteByAccount(accountId)
    }

    override suspend fun deleteExcludeAccountIds(accountIds: List<String>) =
        withContext(Dispatchers.IO) {
            bnbPremiumAddressDao.deleteExceptAccountIds(accountIds)
        }
}
