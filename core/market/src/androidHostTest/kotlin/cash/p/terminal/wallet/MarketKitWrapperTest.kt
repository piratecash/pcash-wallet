package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketKitWrapperTest {

    private val tonW5NativeQuery = TokenQuery(BlockchainType.Ton, TokenType.TonW5Native)
    private val wrapper = spyk(MarketKitWrapper(mockk(), mockk(), mockk()))

    @Test
    fun isTonW5NativeTokenMissing_w5NativeStored_returnsFalse() {
        every { wrapper.token(any()) } returns null
        every { wrapper.token(tonW5NativeQuery) } returns mockk()

        assertFalse(wrapper.isTonW5NativeTokenMissing())
    }

    @Test
    fun isTonW5NativeTokenMissing_onlyOtherTokensStored_returnsTrue() {
        every { wrapper.token(any()) } returns mockk()
        every { wrapper.token(tonW5NativeQuery) } returns null

        assertTrue(wrapper.isTonW5NativeTokenMissing())
    }
}
