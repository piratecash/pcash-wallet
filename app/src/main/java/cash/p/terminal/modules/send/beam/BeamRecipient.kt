package cash.p.terminal.modules.send.beam

import cash.p.beam.BeamAddressType
import cash.p.beam.BeamTokenParser
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.address.IAddressHandler
import cash.p.terminal.modules.address.AddressParserChain
import cash.p.terminal.modules.send.address.EnterAddressValidator
import io.horizontalsystems.core.entities.BlockchainType

internal object BeamRecipient : IAddressHandler, EnterAddressValidator {
    override val blockchainType = BlockchainType.Beam

    fun parserChain() = AddressParserChain(handlers = listOf(this))

    fun type(value: String): BeamAddressType? = try {
        BeamTokenParser.parse(value).type
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun isSupported(value: String) = type(value) != null

    override fun parseAddress(value: String): Address {
        require(type(value) != null) { "Invalid BEAM recipient" }
        return Address(value, blockchainType = blockchainType)
    }

    override suspend fun validate(address: Address) {
        parseAddress(address.hex)
    }
}
