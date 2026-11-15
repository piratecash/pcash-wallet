package cash.p.terminal.core.managers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.evm_method_claim
import org.junit.Assert.assertEquals
import org.junit.Test

class EvmMethodLabelsTest {

    @Test
    fun evmMethodLabelResource_paddedMixedCase_returnsResource() {
        assertEquals(Res.string.evm_method_claim, evmMethodLabelResource("Claim "))
        assertEquals(Res.string.evm_method_claim, evmMethodLabelResource("CLAIM"))
    }

    @Test
    fun localizedEvmMethodLabel_unknownLabel_returnsOriginal() {
        assertEquals("Swap Exact Tokens For Tokens", localizedEvmMethodLabel("Swap Exact Tokens For Tokens"))
    }
}
