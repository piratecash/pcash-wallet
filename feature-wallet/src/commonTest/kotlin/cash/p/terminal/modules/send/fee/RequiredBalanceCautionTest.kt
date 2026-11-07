package cash.p.terminal.modules.send.fee

import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.check_fee_warning
import cash.p.terminal.resources.insufficient_balance_required_available
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequiredBalanceCautionTest {

    private val ton = Token(
        coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
        blockchain = Blockchain(BlockchainType.Ton, "TON", null),
        type = TokenType.Native,
        decimals = 9,
    )
    private val required = BigDecimal("0.301000000")

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "translated"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun requiredBalanceCaution_unknownBalance_returnsWarningWithRequiredAmount() {
        val caution = requiredBalanceCaution(available = null, required = required, feeToken = ton)

        assertEquals(CautionViewItem.Type.Warning, caution?.type)
        assertEquals("translated", caution?.title)
        verify { Translator.getString(Res.string.check_fee_warning, "0.301 TON") }
    }

    @Test
    fun requiredBalanceCaution_balanceBelowRequired_returnsErrorWithRequiredAndAvailable() {
        val caution = requiredBalanceCaution(
            available = BigDecimal("0.2787"),
            required = required,
            feeToken = ton,
        )

        assertEquals(CautionViewItem.Type.Error, caution?.type)
        assertEquals("translated", caution?.title)
        verify {
            Translator.getString(
                Res.string.insufficient_balance_required_available,
                "TON",
                "0.301 TON",
                "0.2787 TON",
            )
        }
    }

    @Test
    fun requiredBalanceCaution_balanceEqualToRequired_returnsNull() {
        assertNull(requiredBalanceCaution(available = BigDecimal("0.301"), required = required, feeToken = ton))
    }
}
