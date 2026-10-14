package cash.p.terminal.wallet.entities

import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HardwarePublicKeyServesTest {

    @Test
    fun serves_sameChainContractToken_true() {
        val bscNativeKey = key(BlockchainType.BinanceSmartChain, TokenType.Native)

        assertTrue(bscNativeKey.serves(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xtoken")))
    }

    @Test
    fun serves_otherChainContractToken_false() {
        val ethereumKey = key(BlockchainType.Ethereum, TokenType.Native)

        assertFalse(ethereumKey.serves(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xtoken")))
    }

    @Test
    fun serves_exactNonContractToken_true() {
        val bip84 = TokenType.Derived(TokenType.Derivation.Bip84)

        assertTrue(key(BlockchainType.Bitcoin, bip84).serves(BlockchainType.Bitcoin, bip84))
    }

    @Test
    fun serves_otherDerivationSameChain_false() {
        val bip84Key = key(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84))

        assertFalse(bip84Key.serves(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip49)))
    }

    @Test
    fun serves_nativeAskedWithTokenRowOnly_false() {
        val bep20Key = key(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xtoken"))

        assertFalse(bep20Key.serves(BlockchainType.BinanceSmartChain, TokenType.Native))
    }

    private fun key(blockchainType: BlockchainType, tokenType: TokenType) = HardwarePublicKey(
        accountId = "hardware",
        blockchainType = blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = tokenType,
        key = SecretString("key"),
        derivationPath = "m/44/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )
}
