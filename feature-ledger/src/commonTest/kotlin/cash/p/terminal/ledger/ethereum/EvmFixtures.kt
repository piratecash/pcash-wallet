package cash.p.terminal.ledger.ethereum

import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import java.math.BigInteger

object EvmFixtures {
    const val RECIPIENT = "0x1111111111111111111111111111111111111111"
    const val USDT = "0xdac17f958d2ee523a2206206994597c13d831ec7"
    const val MAIL_JSON = """{"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],"Person":[{"name":"name","type":"string"},{"name":"wallet","type":"address"}],"Mail":[{"name":"from","type":"Person"},{"name":"to","type":"Person"},{"name":"contents","type":"string"}]},"primaryType":"Mail","domain":{"name":"Ether Mail","version":"1","chainId":1,"verifyingContract":"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"},"message":{"from":{"name":"Cow","wallet":"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"},"to":{"name":"Bob","wallet":"0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"},"contents":"Hello, Bob!"}}"""

    // transfer(RECIPIENT, 1_000_000)
    private const val ERC20_TRANSFER = "a9059cbb" +
        "0000000000000000000000001111111111111111111111111111111111111111" +
        "00000000000000000000000000000000000000000000000000000000000f4240"

    fun transfer(gasPrice: GasPrice) = RawTransaction(
        gasPrice = gasPrice,
        gasLimit = 21_000,
        to = Address(RECIPIENT),
        value = BigInteger.TEN.pow(16),
        nonce = 7,
    )

    fun usdtTransfer(gasPrice: GasPrice) = RawTransaction(
        gasPrice = gasPrice,
        gasLimit = 60_000,
        to = Address(USDT),
        value = BigInteger.ZERO,
        nonce = 3,
        data = hex(ERC20_TRANSFER),
    )

    fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
