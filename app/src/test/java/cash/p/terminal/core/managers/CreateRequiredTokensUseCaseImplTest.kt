package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CreateRequiredTokensUseCaseImplTest {

    private val walletActivator = mockk<WalletActivator>(relaxed = true)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)
    private val scanToAddUseCase = mockk<ScanToAddUseCase>(relaxed = true)
    private val hardwarePublicKeyStorage = mockk<IHardwarePublicKeyStorage>()
    private val accountManager = mockk<IAccountManager>()

    private val useCase = CreateRequiredTokensUseCaseImpl(
        walletActivator = walletActivator,
        userDeletedWalletManager = userDeletedWalletManager,
        scanToAddUseCase = scanToAddUseCase,
        hardwarePublicKeyStorage = hardwarePublicKeyStorage,
        accountManager = accountManager
    )

    @Test
    fun invoke_trezorFirmwareRefusedZcashDerivation_activatesOnlyKeyedQueries() = runTest {
        val account = trezorAccount()
        every { accountManager.account(account.id) } returns account
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(account.id) } returnsMany listOf(
            emptyList(),
            listOf(key(BlockchainType.Bitcoin, bitcoinQuery.tokenType))
        )

        useCase.invoke(account, listOf(bitcoinQuery, zcashQuery))

        coVerify { walletActivator.activateWalletsSuspended(account, listOf(bitcoinQuery)) }
    }

    @Test
    fun invoke_trezorMoneroRequested_activatesMoneroWithoutDerivedKey() = runTest {
        val account = trezorAccount()
        every { accountManager.account(account.id) } returns account
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(account.id) } returns emptyList()

        useCase.invoke(account, listOf(moneroQuery))

        coVerify { walletActivator.activateWalletsSuspended(account, listOf(moneroQuery)) }
    }

    @Test
    fun invoke_trezorAllKeysDerived_activatesEveryQuery() = runTest {
        val account = trezorAccount()
        every { accountManager.account(account.id) } returns account
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(account.id) } returns listOf(
            key(BlockchainType.Bitcoin, bitcoinQuery.tokenType),
            key(BlockchainType.Zcash, zcashQuery.tokenType)
        )

        useCase.invoke(account, listOf(bitcoinQuery, zcashQuery))

        coVerify { walletActivator.activateWalletsSuspended(account, listOf(bitcoinQuery, zcashQuery)) }
    }

    @Test
    fun invoke_mnemonicAccount_activatesEveryQueryWithoutScan() = runTest {
        val account = account(AccountType.Mnemonic(listOf("word"), ""))

        useCase.invoke(account, listOf(bitcoinQuery, moneroQuery))

        coVerify { walletActivator.activateWalletsSuspended(account, listOf(bitcoinQuery, moneroQuery)) }
        coVerify(exactly = 0) { scanToAddUseCase.addTokensByScan(any(), any(), any()) }
    }

    private val bitcoinQuery = TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(
        TokenType.Derivation.Bip84
    ))
    private val zcashQuery =
        TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))
    private val moneroQuery = TokenQuery(BlockchainType.Monero, TokenType.Native)

    private fun key(blockchainType: BlockchainType, tokenType: TokenType) = HardwarePublicKey(
        accountId = ACCOUNT_ID,
        blockchainType = blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = tokenType,
        key = SecretString("key"),
        derivationPath = "m/44'/0'/0'",
        publicKey = ByteArray(0),
        derivedPublicKey = ByteArray(0)
    )

    private fun trezorAccount() = account(
        AccountType.TrezorDevice(
            deviceId = "device",
            model = "T3B1",
            firmwareVersion = "2.5.3",
            walletPublicKey = "pub"
        )
    )

    private fun account(type: AccountType) = Account(
        id = ACCOUNT_ID,
        name = "name",
        type = type,
        origin = AccountOrigin.Restored,
        level = 0
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
