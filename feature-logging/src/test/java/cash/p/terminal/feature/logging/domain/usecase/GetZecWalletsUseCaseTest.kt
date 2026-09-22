package cash.p.terminal.feature.logging.domain.usecase

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class GetZecWalletsUseCaseTest {

    private val accountManager = mockk<IAccountManager>()
    private val walletManager = mockk<IWalletManager>()
    private val useCase = GetZecWalletsUseCase(accountManager, walletManager)

    @Test
    fun getZecWallets_mnemonicAccountShieldedToken_isIncluded() = runTest {
        val wallet = givenSingleWallet(mnemonicType)

        assertEquals(listOf(wallet), useCase.getZecWallets())
    }

    @Test
    fun getZecWallets_mnemonicAccountUnifiedToken_isIncluded() = runTest {
        val wallet = givenSingleWallet(
            mnemonicType,
            tokenType = TokenType.AddressSpecTyped(AddressSpecType.Unified)
        )

        assertEquals(listOf(wallet), useCase.getZecWallets())
    }

    @Test
    fun getZecWallets_watchOnlyUfvkAccount_isExcluded() = runTest {
        givenSingleWallet(AccountType.ZCashUfvKey("uview1watchonly"))

        assertEquals(emptyList(), useCase.getZecWallets())
    }

    @Test
    fun getZecWallets_saplingSpendingKeyAccount_isIncluded() = runTest {
        val wallet = givenSingleWallet(AccountType.ZCashSaplingKey(saplingSpendingKey))

        assertEquals(listOf(wallet), useCase.getZecWallets())
    }

    @Test
    fun getZecWallets_saplingViewingKeyAccount_isExcluded() = runTest {
        givenSingleWallet(AccountType.ZCashSaplingKey(saplingViewingKey))

        assertEquals(emptyList(), useCase.getZecWallets())
    }

    @Test
    fun getZecWallets_mnemonicAccountTransparentToken_isExcluded() = runTest {
        givenSingleWallet(
            mnemonicType,
            tokenType = TokenType.AddressSpecTyped(AddressSpecType.Transparent)
        )

        assertEquals(emptyList(), useCase.getZecWallets())
    }

    @Test
    fun getZecWallets_mnemonicAccountNonZcashWallet_isExcluded() = runTest {
        givenSingleWallet(mnemonicType, blockchainType = BlockchainType.Ton)

        assertEquals(emptyList(), useCase.getZecWallets())
    }

    private fun givenSingleWallet(
        accountType: AccountType,
        tokenType: TokenType = TokenType.AddressSpecTyped(AddressSpecType.Shielded),
        blockchainType: BlockchainType = BlockchainType.Zcash,
    ): Wallet {
        val account = mockk<Account> { every { type } returns accountType }
        val token = mockk<Token> {
            every { this@mockk.blockchainType } returns blockchainType
            every { type } returns tokenType
        }
        val wallet = mockk<Wallet> {
            every { this@mockk.account } returns account
            every { this@mockk.token } returns token
        }
        every { accountManager.accounts } returns listOf(account)
        coEvery { walletManager.getWallets(account) } returns listOf(wallet)
        return wallet
    }

    private companion object {
        val mnemonicType = AccountType.Mnemonic(List(24) { "word$it" }, passphrase = "")
        const val saplingSpendingKey = "secret-extended-key-main1qsaplingspendingkey"
        const val saplingViewingKey = "zxviews1qsaplingviewingkey"
    }
}
