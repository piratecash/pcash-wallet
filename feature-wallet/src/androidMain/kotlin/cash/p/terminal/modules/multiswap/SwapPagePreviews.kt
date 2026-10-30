package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.math.BigDecimal

@Preview(name = "Exact in", showBackground = true)
@Composable
private fun SwapOutputInputExactInPreview() {
    SwapOutputInputPreview(
        amount = BigDecimal("0.12345678"),
        fiatAmount = BigDecimal("20"),
        fiatPriceImpact = BigDecimal("-1.62"),
    )
}

@Preview(name = "Exact out", showBackground = true)
@Composable
private fun SwapOutputInputExactOutPreview() {
    SwapOutputInputPreview(
        amount = BigDecimal("100"),
        fiatAmount = BigDecimal("123456789.12"),
        fiatPriceImpact = BigDecimal("1.23"),
    )
}

@Preview(name = "No price impact", showBackground = true)
@Composable
private fun SwapOutputInputNoPriceImpactPreview() {
    SwapOutputInputPreview(
        amount = BigDecimal("100"),
        fiatAmount = null,
        fiatPriceImpact = null,
    )
}
