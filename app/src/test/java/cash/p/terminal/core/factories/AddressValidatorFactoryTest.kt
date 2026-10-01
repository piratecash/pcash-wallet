package cash.p.terminal.core.factories

import cash.p.terminal.modules.send.address.MemoAddressValidator
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressValidatorFactoryTest {

    private fun token(blockchainType: BlockchainType, tokenType: TokenType) =
        mockk<Token>(relaxed = true) {
            every { this@mockk.blockchainType } returns blockchainType
            every { type } returns tokenType
        }

    @Test
    fun get_thorchainNativeToken_returnsMemoAddressValidator() {
        val validator = AddressValidatorFactory.get(token(BlockchainType.Thorchain, TokenType.Native))

        assertTrue(validator is MemoAddressValidator)
    }

    @Test
    fun get_mayachainNativeToken_returnsMemoAddressValidator() {
        val validator = AddressValidatorFactory.get(token(BlockchainType.Mayachain, TokenType.Native))

        assertTrue(validator is MemoAddressValidator)
    }

    @Test
    fun get_thorchainAssetToken_returnsMemoAddressValidator() {
        val validator = AddressValidatorFactory.get(
            token(BlockchainType.Thorchain, TokenType.ThorchainAsset("tcy"))
        )

        assertTrue(validator is MemoAddressValidator)
    }

    @Test
    fun get_mayachainThorchainAssetToken_returnsMemoAddressValidator() {
        val validator = AddressValidatorFactory.get(
            token(BlockchainType.Mayachain, TokenType.ThorchainAsset("thor.rune"))
        )

        assertTrue(validator is MemoAddressValidator)
    }
}
