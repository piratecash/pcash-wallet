package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HardwarePublicKeySpecsTest {

    private val solana = TokenQuery(BlockchainType.Solana, TokenType.Native)
    private val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)

    @Test
    fun specFor_solanaOnTrezor_usesHardenedChangeLevel() {
        assertEquals("m/44'/501'/0'/0'", HardwarePublicKeySpecs.specFor(solana, HardwareVendor.Trezor)?.derivationPath)
    }

    @Test
    fun specFor_solanaOnLedger_stopsAtAccountLevel() {
        assertEquals("m/44'/501'/0'", HardwarePublicKeySpecs.specFor(solana, HardwareVendor.Ledger)?.derivationPath)
    }

    @Test
    fun specFor_ethereum_samePathForEveryVendor() {
        HardwareVendor.entries.forEach { vendor ->
            assertEquals("m/44'/60'/0'/0/0", HardwarePublicKeySpecs.specFor(ethereum, vendor)?.derivationPath)
        }
    }

    @Test
    fun specFor_evmChainsAddedForLedger_servedOnlyOnLedger() {
        val chains =
            listOf(BlockchainType.Avalanche, BlockchainType.Gnosis, BlockchainType.Fantom, BlockchainType.ZkSync)
        chains.forEach { type ->
            val query = TokenQuery(type, TokenType.Eip20("0x55d398326f99059ff775485246999027b3197955"))
            val ledgerSpec = HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)

            assertEquals("m/44'/60'/0'/0/0", ledgerSpec?.derivationPath)
            assertNull(HardwarePublicKeySpecs.specFor(query, HardwareVendor.Trezor))
        }
    }

    // Paths of Ledger Live's first account: the TON app takes six hardened levels.
    @Test
    fun specFor_tonAndThorchain_ledgerLivePathsOnLedgerOnly() {
        val expected = mapOf(
            TokenQuery(BlockchainType.Ton, TokenType.Native) to "m/44'/607'/0'/0'/0'/0'",
            TokenQuery(BlockchainType.Ton, TokenType.Jetton("EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs")) to
                "m/44'/607'/0'/0'/0'/0'",
            TokenQuery(BlockchainType.Thorchain, TokenType.Native) to "m/44'/931'/0'/0/0",
            TokenQuery(BlockchainType.Thorchain, TokenType.ThorchainAsset("btc-btc")) to "m/44'/931'/0'/0/0",
        )

        expected.forEach { (query, path) ->
            assertEquals(path, HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)?.derivationPath)
            assertNull(HardwarePublicKeySpecs.specFor(query, HardwareVendor.Trezor))
        }
        val maya = TokenQuery(BlockchainType.Mayachain, TokenType.Native)
        assertNull(HardwarePublicKeySpecs.specFor(maya, HardwareVendor.Ledger))
    }

    @Test
    fun specFor_zcash_ledgerViewingKeyWithoutSaplingTrezorTransparentOnly() {
        fun zcash(type: TokenType.AddressSpecType) = TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(type))
        val transparent = zcash(TokenType.AddressSpecType.Transparent)
        val unified = zcash(TokenType.AddressSpecType.Unified)
        val shielded = zcash(TokenType.AddressSpecType.Shielded)

        listOf(transparent, unified).forEach { query ->
            val spec = HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger)
            assertEquals("m/44'/133'/0'", spec?.derivationPath)
            assertNull(spec?.expectedPurpose)
        }
        assertNull(HardwarePublicKeySpecs.specFor(shielded, HardwareVendor.Ledger))
        assertEquals("m/44'/133'/0'", HardwarePublicKeySpecs.specFor(transparent, HardwareVendor.Trezor)?.derivationPath)
        assertNull(HardwarePublicKeySpecs.specFor(unified, HardwareVendor.Trezor))
    }
}
