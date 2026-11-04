package cash.p.terminal.wallet.useCases

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class GetHardwarePublicKeyForWalletUseCaseTest {

    private val storage = mockk<IHardwarePublicKeyStorage>()
    private val useCase = GetHardwarePublicKeyForWalletUseCase(storage)
    private val account = hardwareAccount()

    @Test
    fun invoke_exactKeyStored_returnsExactKeyWithoutChainLookup() = runTest {
        val bep20 = TokenType.Eip20("0xtoken")
        val exactKey = key(BlockchainType.BinanceSmartChain, bep20)
        coEvery { storage.getKey(account.id, BlockchainType.BinanceSmartChain, bep20) } returns exactKey

        assertSame(exactKey, useCase(account, BlockchainType.BinanceSmartChain, bep20))
        coVerify(exactly = 0) { storage.getKeyByBlockchain(any(), any()) }
    }

    @Test
    fun invoke_eip20WithoutExactKey_returnsChainKey() = runTest {
        val bep20 = TokenType.Eip20("0xtoken")
        val chainKey = key(BlockchainType.BinanceSmartChain, TokenType.Native)
        coEvery { storage.getKey(account.id, BlockchainType.BinanceSmartChain, bep20) } returns null
        coEvery { storage.getKeyByBlockchain(account.id, BlockchainType.BinanceSmartChain) } returns chainKey

        assertSame(chainKey, useCase(account, BlockchainType.BinanceSmartChain, bep20))
    }

    @Test
    fun invoke_contractTokenTypesWithoutExactKey_returnChainKey() = runTest {
        listOf(
            BlockchainType.Solana to TokenType.Spl("spl-mint"),
            BlockchainType.Ton to TokenType.Jetton("jetton-master"),
            BlockchainType.Ton to TokenType.TonW5Native,
            BlockchainType.Ton to TokenType.TonW5Jetton("jetton-master"),
            BlockchainType.Stellar to TokenType.Asset("USDC", "issuer"),
        ).forEach { (blockchainType, tokenType) ->
            val chainKey = key(blockchainType, TokenType.Native)
            coEvery { storage.getKey(account.id, blockchainType, tokenType) } returns null
            coEvery { storage.getKeyByBlockchain(account.id, blockchainType) } returns chainKey

            assertSame(chainKey, useCase(account, blockchainType, tokenType))
        }
    }

    @Test
    fun invoke_nativeWithoutExactKey_returnsNull() = runTest {
        coEvery { storage.getKey(account.id, BlockchainType.Ethereum, TokenType.Native) } returns null

        assertNull(useCase(account, BlockchainType.Ethereum, TokenType.Native))
        coVerify(exactly = 0) { storage.getKeyByBlockchain(any(), any()) }
    }

    @Test
    fun invoke_derivedWithOtherDerivationStored_returnsNull() = runTest {
        val bip49 = TokenType.Derived(TokenType.Derivation.Bip49)
        val bip84 = TokenType.Derived(TokenType.Derivation.Bip84)
        coEvery { storage.getKey(account.id, BlockchainType.Bitcoin, bip49) } returns null
        coEvery { storage.getKey(account.id, BlockchainType.Bitcoin, bip84) } returns
            key(BlockchainType.Bitcoin, bip84)

        assertNull(useCase(account, BlockchainType.Bitcoin, bip49))
        coVerify(exactly = 0) { storage.getKeyByBlockchain(any(), any()) }
    }

    @Test
    fun invoke_addressTypedWithoutExactKey_returnsNull() = runTest {
        val type0 = TokenType.AddressTyped(TokenType.AddressType.Type0)
        coEvery { storage.getKey(account.id, BlockchainType.BitcoinCash, type0) } returns null

        assertNull(useCase(account, BlockchainType.BitcoinCash, type0))
        coVerify(exactly = 0) { storage.getKeyByBlockchain(any(), any()) }
    }

    @Test
    fun invoke_softwareAccount_returnsNullWithoutStorageAccess() = runTest {
        val softwareAccount = Account(
            id = "software",
            name = "Software",
            type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
            origin = AccountOrigin.Created,
            level = 0
        )

        assertNull(useCase(softwareAccount, BlockchainType.BinanceSmartChain, TokenType.Eip20("0xtoken")))
        confirmVerified(storage)
    }

    private fun hardwareAccount() = Account(
        id = "hardware",
        name = "Hardware",
        type = AccountType.HardwareCard(
            cardId = "card",
            backupCardsCount = 0,
            walletPublicKey = "pk",
            signedHashes = 0
        ),
        origin = AccountOrigin.Created,
        level = 0
    )

    private fun key(blockchainType: BlockchainType, tokenType: TokenType) = HardwarePublicKey(
        accountId = account.id,
        blockchainType = blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = tokenType,
        key = SecretString("key"),
        derivationPath = "m/44/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )
}
