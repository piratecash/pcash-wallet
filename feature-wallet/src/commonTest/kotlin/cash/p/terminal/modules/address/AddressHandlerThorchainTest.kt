package cash.p.terminal.modules.address

import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressHandlerThorchainTest {

    // Public BIP39 test vector, not a real user mnemonic (same seed used elsewhere in this suite).
    private val seed = AccountType.Mnemonic(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            .split(" "),
        ""
    ).seed

    private val thorAddress = ThorchainKit.getAddress(seed, Network.Mainnet).toString()
    private val mayaAddress = ThorchainKit.getAddress(seed, Network.MayaMainnet).toString()

    private val thorHandler = AddressHandlerThorchain(Network.Mainnet, BlockchainType.Thorchain)
    private val mayaHandler = AddressHandlerThorchain(Network.MayaMainnet, BlockchainType.Mayachain)

    @Test
    fun isSupported_ownChainBech32_returnsTrue() {
        assertTrue(thorHandler.isSupported(thorAddress))
        assertTrue(mayaHandler.isSupported(mayaAddress))
    }

    @Test
    fun isSupported_otherChainPrefix_returnsFalse() {
        assertFalse(thorHandler.isSupported(mayaAddress))
        assertFalse(mayaHandler.isSupported(thorAddress))
    }

    @Test
    fun isSupported_invalidChecksum_returnsFalse() {
        val corrupted = thorAddress.dropLast(1) + if (thorAddress.last() == 'q') 'p' else 'q'

        assertFalse(thorHandler.isSupported(corrupted))
    }

    @Test
    fun parseAddress_ownChainBech32_returnsAddressWithBlockchainType() {
        val parsed = thorHandler.parseAddress(thorAddress)

        assertTrue(parsed.hex == thorAddress)
        assertTrue(parsed.blockchainType == BlockchainType.Thorchain)
    }

    @Test
    fun forBlockchainType_thorchainAndMayachain_resolvesOwnChainNetwork() {
        val thorHandlerFromType = AddressHandlerThorchain.forBlockchainType(BlockchainType.Thorchain)
        val mayaHandlerFromType = AddressHandlerThorchain.forBlockchainType(BlockchainType.Mayachain)

        assertTrue(thorHandlerFromType.isSupported(thorAddress))
        assertFalse(thorHandlerFromType.isSupported(mayaAddress))
        assertTrue(mayaHandlerFromType.isSupported(mayaAddress))
        assertFalse(mayaHandlerFromType.isSupported(thorAddress))
    }
}
