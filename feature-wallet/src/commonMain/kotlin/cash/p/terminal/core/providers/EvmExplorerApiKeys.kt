package cash.p.terminal.core.providers

import cash.p.terminal.network.data.EncodedSecrets

object EvmExplorerApiKeys {
    val etherscan by lazy { EncodedSecrets.ETHERSCAN_KEY.toKeyList() }
    val bscscan by lazy { EncodedSecrets.BSCSCAN_KEY.toKeyList() }
    val blockscout by lazy { EncodedSecrets.BLOCKSCOUT_KEY.toKeyList() }
}

fun String.toKeyList(): List<String> = split(",").map { it.trim() }
