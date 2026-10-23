package cash.p.terminal.core

import cash.p.terminal.core.utils.AddressUriResult

interface IAddressParser {
    fun parse(addressUri: String): AddressUriResult
}
