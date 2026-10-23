package cash.p.terminal.core.address

import cash.p.terminal.entities.Address
import cash.p.terminal.network.addresscheck.api.ChainalysisApi

class ChainalysisAddressValidator(private val api: ChainalysisApi) {

    suspend fun isClear(address: Address): Boolean = !api.hasIdentifications(address.hex)
}
