package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.data.api.PCashWalletRequestDto
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class ConnectMiniAppWalletUseCase(
    private val miniAppApi: MiniAppApi,
    private val evmPersonalSigner: EvmPersonalSigner,
    private val getTonAddressUseCase: GetTonAddressUseCase,
    private val getSpecialProposalDataUseCase: GetSpecialProposalDataUseCase,
    private val uniqueCodeStorage: IUniqueCodeStorage,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(account: Account, jwt: String, endpoint: String) =
        withContext(dispatcherProvider.io) {
            val walletAddress = getTonAddressUseCase.getAddress(account)
            val premiumAddress = evmPersonalSigner.address(account) ?: throw NoEvmSignerException()

            // The nonce is single-use and expires in 10 minutes, so it is taken right before signing.
            val nonce = miniAppApi.getEvmNonce(jwt, endpoint).nonce
            val message = "Address: $premiumAddress\nNonce: $nonce"
            val signature = evmPersonalSigner.signPersonalMessage(account, message)
                ?: throw NoEvmSignerException()

            val (pirate, cosa) = getSpecialProposalDataUseCase.getPirateCosaBalances(premiumAddress)

            val response = miniAppApi.submitPCashWallet(
                jwt = jwt,
                endpoint = endpoint,
                request = PCashWalletRequestDto(
                    walletAddress = walletAddress,
                    premiumAddress = premiumAddress,
                    premiumSignature = signature,
                    pirate = pirate.toPlainString(),
                    cosa = cosa.toPlainString(),
                    uniqueCode = uniqueCodeStorage.uniqueCode.ifBlank { null },
                    apiVersion = MiniAppApi.API_VERSION
                )
            )

            with(uniqueCodeStorage) {
                connectedAccountId = account.id
                uniqueCode = response.uniqueCode.orEmpty()
            }
        }
}

class NoEvmSignerException : Exception("No EVM signer available for the selected account")
