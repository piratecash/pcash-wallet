package cash.p.terminal.core.address

import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.core.managers.SpamManager
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.wallet.Token

class PhishingAddressChecker(
    private val spamManager: SpamManager
) : AddressChecker {

    override suspend fun isClear(address: Address, token: Token): AddressCheckResult {
        val spamAddress = spamManager.find(address.hex.uppercase())
        return if (spamAddress == null) AddressCheckResult.Clear else AddressCheckResult.Detected
    }

    override fun supports(token: Token): Boolean {
        return EvmBlockchains.blockchainTypes.contains(token.blockchainType)
    }
}
