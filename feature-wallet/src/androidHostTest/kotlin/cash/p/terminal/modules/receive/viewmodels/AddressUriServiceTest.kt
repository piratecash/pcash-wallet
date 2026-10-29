package cash.p.terminal.modules.receive.viewmodels

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddressUriServiceTest {

    @Test
    fun refreshUri_tonW5Native_equalsV4r2NativeUri() {
        assertEquals(
            "toncoin:UQaddress?amount=1&blockchain_uid=the-open-network&token_uid=native",
            uri(token(BlockchainType.Ton, TokenType.TonW5Native)),
        )
        assertEquals(
            uri(token(BlockchainType.Ton, TokenType.Native)),
            uri(token(BlockchainType.Ton, TokenType.TonW5Native)),
        )
    }

    @Test
    fun refreshUri_tonW5Jetton_equalsV4r2JettonUri() {
        assertEquals(
            "toncoin:UQaddress?amount=1&blockchain_uid=the-open-network&token_uid=the-open-network:EQmaster",
            uri(token(BlockchainType.Ton, TokenType.TonW5Jetton("EQmaster"))),
        )
    }

    @Test
    fun refreshUri_bitcoinDerivation_omitsTokenUid() {
        val uri = uri(token(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84)))

        assertFalse(uri.contains("token_uid"))
    }

    private fun uri(token: Token): String {
        val service = AddressUriService(token)
        service.setAddress("UQaddress")
        service.setAmount(BigDecimal.ONE)
        return service.stateFlow.value.uri
    }

    private fun token(blockchainType: BlockchainType, type: TokenType) = Token(
        coin = Coin(uid = "coin", name = "Coin", code = "COIN"),
        blockchain = Blockchain(blockchainType, blockchainType.uid, null),
        type = type,
        decimals = 9,
    )
}
