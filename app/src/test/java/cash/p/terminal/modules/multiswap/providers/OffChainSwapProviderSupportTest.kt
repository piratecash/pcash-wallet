package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun buildTransactionData_mwebInputWithMemo_throwsMemoUnsupported() {
        val mweb = yiFiTestToken(BlockchainType.Litecoin, TokenType.Mweb, "LTC")

        assertThrows(SwapDepositMemoUnsupported::class.java) {
            support.buildTransactionData(mweb, BigDecimal("1.5"), "ltc1deposit", "=:BTC.BTC:bc1q")
        }
    }

    @Test
    fun buildTransactionData_mwebInputWithoutMemo_returnsBtcSend() {
        val mweb = yiFiTestToken(BlockchainType.Litecoin, TokenType.Mweb, "LTC")

        listOf(null, "", "  ").forEach { memo ->
            val data = support.buildTransactionData(mweb, BigDecimal("1.5"), "ltc1deposit", memo)

            assertEquals("memo=[$memo]", "ltc1deposit", (data as SendTransactionData.Btc).address)
        }
    }

    @Test
    fun buildTransactionData_publicLitecoinWithMemo_keepsMemo() {
        val ltc = yiFiTestToken(BlockchainType.Litecoin, TokenType.Derived(TokenType.Derivation.Bip84), "LTC")

        val data = support.buildTransactionData(ltc, BigDecimal("1.5"), "ltc1deposit", "=:BTC.BTC:bc1q")

        assertEquals("=:BTC.BTC:bc1q", (data as SendTransactionData.Btc).memo)
    }
}
