package cash.p.terminal.modules.send.address

import cash.p.terminal.entities.Address

interface EnterAddressValidator {
    @Throws
    suspend fun validate(address: Address)
}
