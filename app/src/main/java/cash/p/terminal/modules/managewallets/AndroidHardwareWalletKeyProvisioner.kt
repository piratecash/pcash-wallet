package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.ledger.domain.LedgerScanToAddUseCase
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.ledger.ui.ledgerErrorMessage
import cash.p.terminal.ledger.ui.ledgerSkippedAppsMessage
import cash.p.terminal.modules.send.isHardwareWalletCancelled
import cash.p.terminal.modules.send.userMessageRes
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.error_no_active_account
import cash.p.terminal.resources.trezor_not_initialized_description
import cash.p.terminal.tangem.domain.usecase.BuildHardwarePublicKeyUseCase
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.trezorkit.TrezorNotInitializedException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareCancelledException
import cash.p.terminal.wallet.isMonero
import cash.p.terminal.wallet.latestAccountOr
import co.touchlab.kermit.Logger
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError.UserCancelled
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.CancellationException

internal class AndroidHardwareWalletKeyProvisioner(
    private val tangemScanUseCase: TangemScanUseCase,
    private val fetchTrezorPublicKeys: FetchTrezorPublicKeysUseCase,
    private val addMoneroToTrezorAccount: AddMoneroToTrezorAccountUseCase,
    private val accountManager: IAccountManager,
    private val ledgerScanToAdd: LedgerScanToAddUseCase,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
) : HardwareWalletKeyProvisioner {

    private val logger = Logger.withTag("HardwareWalletKeyProvisioner")

    override suspend fun provisionPublicKeys(
        account: Account,
        tokenQueries: List<TokenQuery>,
        moneroRestoreHeight: Long?,
    ): ProvisionResult =
        when (account.type) {
            is AccountType.HardwareCard -> scanTangem(account, tokenQueries)
            is AccountType.TrezorDevice -> fetchTrezorKeys(account, tokenQueries, moneroRestoreHeight)
            is AccountType.LedgerDevice -> fetchLedgerKeys(account, tokenQueries)
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

    private suspend fun fetchTrezorKeys(
        account: Account,
        tokenQueries: List<TokenQuery>,
        moneroRestoreHeight: Long?,
    ): ProvisionResult =
        try {
            val publicKeys = fetchTrezorPublicKeys(
                tokenQueries.filterNot { it.isMonero },
                account.id,
            )
            val refreshedAccount = accountManager.latestAccountOr(account)
            moneroRestoreHeight?.let { addMoneroToTrezorAccount.provision(refreshedAccount, it) }
            ProvisionResult.Success(publicKeys, refreshedAccount)
        } catch (e: TrezorNotInitializedException) {
            logger.w(e) { "Trezor is not set up" }
            ProvisionResult.Failed(Translator.getString(Res.string.trezor_not_initialized_description))
        } catch (_: HardwareCancelledException) {
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

    private suspend fun fetchLedgerKeys(account: Account, tokenQueries: List<TokenQuery>): ProvisionResult =
        try {
            val read = ledgerScanToAdd.scan(tokenQueries, account.id)
            val moneroReady = LEDGER_MONERO_APP !in read.skippedApps
            val refreshedAccount = accountManager.latestAccountOr(account)
            if (moneroReady && tokenQueries.any { it.isMonero }) addMoneroToTrezorAccount.provision(refreshedAccount)
            ProvisionResult.Success(
                keys = hardwarePublicKeyStorage.getAllPublicKeys(account.id),
                account = refreshedAccount,
                notFoundMessage = ledgerSkippedAppsMessage(read.skipped),
                moneroReady = moneroReady,
            )
        } catch (_: HardwareCancelledException) {
            ProvisionResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Ledger: failed to fetch public keys" }
            ProvisionResult.Failed(ledgerErrorMessage(e))
        }
}
