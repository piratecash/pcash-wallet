package cash.p.terminal.tangem.domain

import cash.p.terminal.tangem.domain.address.AddressType
import cash.p.terminal.tangem.domain.card.Wallet2CardConfig
import cash.p.terminal.tangem.domain.derivation.DerivationConfigV1
import cash.p.terminal.tangem.domain.derivation.DerivationConfigV2
import cash.p.terminal.tangem.domain.derivation.DerivationConfigV3
import com.tangem.common.card.EllipticCurve
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Test

class RobinhoodHardwareSupportTest {

    @Test
    fun getSupportedCurves_robinhood_usesSecp256k1() {
        assertEquals(
            listOf(EllipticCurve.Secp256k1),
            BlockchainType.RobinhoodChain.getSupportedCurves(),
        )
        assertEquals(
            EllipticCurve.Secp256k1,
            Wallet2CardConfig.primaryCurve(BlockchainType.RobinhoodChain),
        )
    }

    @Test
    fun derivations_robinhood_allCardGenerationsUseEthereumPath() {
        listOf(DerivationConfigV1, DerivationConfigV2, DerivationConfigV3).forEach { config ->
            assertEquals(
                "m/44'/60'/0'/0/0",
                config.derivations(BlockchainType.RobinhoodChain, "")[AddressType.Default]?.rawPath,
            )
        }
    }
}
