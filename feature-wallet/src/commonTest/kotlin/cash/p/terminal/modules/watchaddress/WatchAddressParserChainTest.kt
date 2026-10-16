package cash.p.terminal.modules.watchaddress

import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchAddressParserChainTest {

    // Public BIP39 test vector, not a real user mnemonic.
    private val seed = AccountType.Mnemonic(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
        "",
    ).seed

    private val thorAddress = ThorchainKit.getAddress(seed, Network.Mainnet).toString()
    private val mayaAddress = ThorchainKit.getAddress(seed, Network.MayaMainnet).toString()

    // Domain handlers resolve over the network, so only the address handlers are exercised.
    private val parserChain = AddressHandlerFactory(udnApiKey = "").parserChain(
        blockchainTypes = WatchAddressModule.supportedBlockchainTypes,
        blockchainTypesWithEns = emptyList(),
    )

    @Test
    fun supportedAddressHandlers_thorchainAddress_resolvesThorchainOnly() {
        assertEquals(listOf(BlockchainType.Thorchain), handlerTypes(thorAddress))
    }

    @Test
    fun supportedAddressHandlers_mayachainAddress_resolvesMayachainOnly() {
        assertEquals(listOf(BlockchainType.Mayachain), handlerTypes(mayaAddress))
    }

    @Test
    fun supportedAddressHandlers_stagenetOrBadChecksumAddress_resolvesNone() {
        val stagenetAddress = ThorchainKit.getAddress(seed, Network.Stagenet).toString()
        val badChecksum = thorAddress.dropLast(1) + if (thorAddress.last() == 'q') 'p' else 'q'

        for (value in listOf(stagenetAddress, badChecksum)) {
            assertTrue(handlerTypes(value).isEmpty())
        }
    }

    private fun handlerTypes(value: String) =
        parserChain.supportedAddressHandlers(value).map { it.blockchainType }
}
