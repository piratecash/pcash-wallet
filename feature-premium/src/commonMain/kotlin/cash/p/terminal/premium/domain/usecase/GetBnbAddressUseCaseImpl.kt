package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.premium.data.database.PremiumDatabaseRouter
import cash.p.terminal.premium.data.database.sessionDatabase
import cash.p.terminal.premium.data.model.BnbPremiumAddress
import cash.p.terminal.premium.domain.HardwareBnbAddressProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.SeedAccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GetBnbAddressUseCaseImpl(
    private val seedToEvmAddressUseCase: SeedToEvmAddressUseCase,
    private val premiumDatabaseRouter: PremiumDatabaseRouter,
    private val hardwareBnbAddressProvider: HardwareBnbAddressProvider
) : GetBnbAddressUseCase {
    private val bnbPremiumAddressDao get() = premiumDatabaseRouter.sessionDatabase.bnbPremiumAddressDao()

    override suspend fun getAddress(
        account: Account,
        requestScanTangemIfNotFound: Boolean
    ): String? {
        return when (val accountType = account.type) {
            is SeedAccountType -> seedToEvmAddressUseCase(accountType.seed)

            is AccountType.TrezorDevice,
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
