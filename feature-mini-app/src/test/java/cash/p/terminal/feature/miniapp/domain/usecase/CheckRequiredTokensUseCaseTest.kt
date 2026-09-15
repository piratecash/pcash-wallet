package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckRequiredTokensUseCaseTest {

    private val walletManager = mockk<IWalletManager>()
    private val marketKitWrapper = mockk<MarketKitWrapper>()
    private val hardwarePublicKeyStorage = mockk<IHardwarePublicKeyStorage>()

    private val useCase = CheckRequiredTokensUseCase(
        walletManager,
        marketKitWrapper,
        hardwarePublicKeyStorage
    )

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })

    private val requiredQueries = listOf(
        TokenQuery.PirateJetton,
        TokenQuery.PirateCashBnb,
        TokenQuery.CosantaBnb
    )

    private fun tokenFor(query: TokenQuery): Token = Token(
        coin = Coin(uid = query.id, name = query.id, code = query.id),
        blockchain = Blockchain(query.blockchainType, query.blockchainType.uid, null),
        type = query.tokenType,
        decimals = 8
    )

    private fun stubTokens() {
        requiredQueries.forEach { query ->
            every { marketKitWrapper.token(query) } returns tokenFor(query)
        }
    }

    private fun walletsFor(account: Account): List<Wallet> =
        requiredQueries.map {
            checkNotNull(walletFactory.create(tokenFor(it), account, hardwarePublicKey = null))
        }

    private val hardwareAccount = Account(
        id = "hw-1",
        name = "Tangem",
        type = AccountType.HardwareCard(
            cardId = "card1",
            backupCardsCount = 1,
            walletPublicKey = "pub",
            signedHashes = 0
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private val mnemonicAccount = Account(
        id = "mnemonic-1",
        name = "Mnemonic",
        type = AccountType.Mnemonic(words = listOf("a", "b"), passphrase = ""),
        origin = AccountOrigin.Created,
        level = 0
    )

    @Test
    fun invoke_hardwareAccountWithoutBscKey_reportsBnbTokensMissing() = runTest {
        stubTokens()
        coEvery { walletManager.getWallets(hardwareAccount) } returns walletsFor(hardwareAccount)
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(hardwareAccount.id, BlockchainType.BinanceSmartChain)
        } returns null

        val result = useCase(hardwareAccount)

        assertFalse(result.allTokensExist)
        assertEquals(
            setOf(TokenQuery.PirateCashBnb, TokenQuery.CosantaBnb),
            result.missingTokenQueries.toSet()
        )
        assertEquals(2, result.missingTokens.size)
        assertTrue(result.missingTokens.none { it.blockchainType == BlockchainType.Ton })
    }

    @Test
    fun invoke_hardwareAccountWithoutBscKeyAndNoBscMetadata_stillReportsBnbMissing() = runTest {
        every { marketKitWrapper.token(TokenQuery.PirateJetton) } returns tokenFor(TokenQuery.PirateJetton)
        every { marketKitWrapper.token(TokenQuery.PirateCashBnb) } returns null
        every { marketKitWrapper.token(TokenQuery.CosantaBnb) } returns null
        coEvery { walletManager.getWallets(hardwareAccount) } returns listOf(
            checkNotNull(
                walletFactory.create(
                    tokenFor(TokenQuery.PirateJetton),
                    hardwareAccount,
                    hardwarePublicKey = null
                )
            )
        )
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(hardwareAccount.id, BlockchainType.BinanceSmartChain)
        } returns null

        val result = useCase(hardwareAccount)

        assertFalse(result.allTokensExist)
        assertEquals(
            setOf(TokenQuery.PirateCashBnb, TokenQuery.CosantaBnb),
            result.missingTokenQueries.toSet()
        )
    }

    @Test
    fun invoke_mnemonicAccount_ignoresHardwareKeyStorage() = runTest {
        stubTokens()
        coEvery { walletManager.getWallets(mnemonicAccount) } returns walletsFor(mnemonicAccount)

        val result = useCase(mnemonicAccount)

        assertTrue(result.allTokensExist)
        coVerify(exactly = 0) { hardwarePublicKeyStorage.getKeyByBlockchain(any(), any()) }
    }
}
