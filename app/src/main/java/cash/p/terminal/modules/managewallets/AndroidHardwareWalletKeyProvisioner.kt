package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.modules.send.isHardwareWalletCancelled
import cash.p.terminal.modules.send.userMessageRes
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.error_no_active_account
import cash.p.terminal.resources.trezor_not_initialized_description
import cash.p.terminal.tangem.domain.usecase.BuildHardwarePublicKeyUseCase
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.isMonero
import cash.p.terminal.wallet.latestAccountOr
import co.touchlab.kermit.Logger
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError.UserCancelled
import io.horizontalsystems.core.Translator

internal class AndroidHardwareWalletKeyProvisioner(
    private val tangemScanUseCase: TangemScanUseCase,
    private val fetchTrezorPublicKeys: FetchTrezorPublicKeysUseCase,
    private val addMoneroToTrezorAccount: AddMoneroToTrezorAccountUseCase,
    private val accountManager: IAccountManager,
) : HardwareWalletKeyProvisioner {

    private val logger = Logger.withTag("HardwareWalletKeyProvisioner")

    override suspend fun provisionPublicKeys(account: Account, tokenQueries: List<TokenQuery>): ProvisionResult =
        when (account.type) {
            is AccountType.HardwareCard -> scanTangem(account, tokenQueries)
            is AccountType.TrezorDevice -> fetchTrezorKeys(account, tokenQueries)
            else -> ProvisionResult.Failed(Translator.getString(Res.string.error_no_active_account))
        }

    private suspend fun scanTangem(account: Account, tokenQueries: List<TokenQuery>): ProvisionResult =
        when (val response = tangemScanUseCase.scanProduct(blockchainsToDerive = tokenQueries)) {
            is CompletionResult.Success -> ProvisionResult.Success(
                keys = BuildHardwarePublicKeyUseCase().invoke(
                    scanResponse = response.data,
                    accountId = account.id,
                    blockchainTypeList = tokenQueries
                ),
                account = account,
            )

            is CompletionResult.Failure -> if (response.error is UserCancelled) {
                ProvisionResult.Cancelled
            } else {
                ProvisionResult.Failed(response.error.customMessage)
            }
        }

    private suspend fun fetchTrezorKeys(account: Account, tokenQueries: List<TokenQuery>): ProvisionResult =
        try {
            val publicKeys = fetchTrezorPublicKeys(
                tokenQueries.filterNot { it.isMonero },
                account.id,
            )
            val refreshedAccount = accountManager.latestAccountOr(account)
            if (tokenQueries.any { it.isMonero }) {
                addMoneroToTrezorAccount.provision(refreshedAccount)
            }
            ProvisionResult.Success(publicKeys, refreshedAccount)
        } catch (e: TrezorNotInitializedException) {
            logger.w(e) { "Trezor is not set up" }
            ProvisionResult.Failed(Translator.getString(Res.string.trezor_not_initialized_description))
        } catch (_: TrezorCancelledException) {
            ProvisionResult.Cancelled
        } catch (e: HardwareWalletOperationException) {
            if (e.isHardwareWalletCancelled()) {
                ProvisionResult.Cancelled
            } else {
                logger.e(e) { "Trezor: failed to add Monero wallet" }
                ProvisionResult.Failed(Translator.getString(e.userMessageRes()))
            }
        } catch (e: Exception) {
            logger.e(e) { "Trezor: failed to fetch public keys" }
            ProvisionResult.Failed(e.message)
        }
}
