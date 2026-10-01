package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class OffChainSwapProviderSupportTest {

    private val support = buildOffChainSwapProviderSupport(
        walletUseCase = mockk(relaxed = true),
        accountManager = mockk(relaxed = true),
        storage = mockk(relaxed = true),
        marketKit = mockk(relaxed = true),
    )

    @Test
    fun buildTransactionData_thorchainAndMayaInputs_returnsThorchainSendWithMemo() {
        val tokens = listOf(
            nativeTestToken(BlockchainType.Thorchain, "RUNE"),
            yiFiTestToken(BlockchainType.Thorchain, TokenType.ThorchainAsset("tcy"), "TCY"),
            nativeTestToken(BlockchainType.Mayachain, "CACAO"),
        )

        tokens.forEach { token ->
            val data = support.buildTransactionData(token, BigDecimal("1.5"), "thor1deposit", "=:BTC.BTC:bc1q")

            assertEquals(
                token.type.toString(),
                SendTransactionData.Thorchain.Send("thor1deposit", BigDecimal("1.5"), "=:BTC.BTC:bc1q"),
                data,
            )
        }
    }

    @Test
    fun buildTransactionData_thorchainAndMayaInputsWithoutMemo_returnsUnsupported() {
        val tokens = listOf(
            nativeTestToken(BlockchainType.Thorchain, "RUNE"),
            nativeTestToken(BlockchainType.Mayachain, "CACAO"),
        )

        tokens.forEach { token ->
            listOf(null, "", "  ").forEach { memo ->
                val data = support.buildTransactionData(token, BigDecimal("1.5"), "thor1deposit", memo)

                assertEquals("${token.type} memo=[$memo]", SendTransactionData.Unsupported, data)
            }
        }
    }
}
