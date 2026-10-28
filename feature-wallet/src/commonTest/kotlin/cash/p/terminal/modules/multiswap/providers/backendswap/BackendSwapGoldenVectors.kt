package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import org.junit.Assert.assertEquals
import java.math.BigDecimal

// Reference values produced by ethers v6.15.0 `signer.signTypedData` with a throwaway key.
internal object BackendSwapGoldenVectors {
    const val PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
    const val ADDRESS = "0x2c7536E3605D9C16a7a3D7b1898e529396a65c23"
    const val DOMAIN_SEPARATOR = "0x70e10b7ae1cd9e2a8a24d21c0f9c9e46340e5aeb20e6d0c337cd78a6fec9d068"

    val m1 = CreateSwapMessage(
        provider = "changelly",
        type = "float",
        from = BackendSwapAsset(coinId = "bitcoin", blockchain = "bitcoin"),
        to = BackendSwapAsset(coinId = "ethereum", blockchain = "ethereum"),
        amount = BigDecimal("0.01"),
        recipient = "0x1234567890abcdef1234567890abcdef12345678",
        clientRequestId = "8d3d9db0-7d1b-43ce-9431-fb530ce2c6ef",
    )
    const val M1_STRUCT_HASH = "0x9eb9c202dc4ecb182acf1706506b614f23e86d72d84c68e4ffcb073ce1de3b17"
    const val M1_DIGEST = "0x20a4727a0a4f04220791a7bce431a576fc7aa642513d8840be90ed6340b33c1a"
    const val M1_SIGNATURE = "0x9a2c1d4822237a85fcdc69f8cd656353ce0bc5f6dd87f366513ff48a1f03b8f6" +
        "0675c8e34b8508de03f927aac931d8f386161d3489c5177b7fb85fb44f9fb6361b"

    val m2 = CreateSwapMessage(
        provider = "changelly",
        type = "float",
        from = BackendSwapAsset(coinId = "stellar", blockchain = "stellar"),
        to = BackendSwapAsset(coinId = "the-open-network", blockchain = "the-open-network"),
        amount = BigDecimal("0.00012300"),
        recipient = "UQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG",
        clientRequestId = "0f6c1c6e-2d0b-4a55-9d1f-3b2c5e7a9b10",
        recipientExtraId = "memo-42",
        refundAddress = "GBH4TZYZ4IRCPO44CBOLFUHULU2WGALXTAVESQA6432MBJMABBB4GIYI",
        refundExtraId = "77",
        fromAddress = "GBH4TZYZ4IRCPO44CBOLFUHULU2WGALXTAVESQA6432MBJMABBB4GIYI",
        fromExtraId = "1",
    )
    const val M2_DIGEST = "0xb0b1da379c83863a0082e7a2dc190584667af846179ac531a7d49b13aa8776e8"
    const val M2_SIGNATURE = "0x4ebde013632319978b99780907157178d690c8bbadd6c030cafbd48406ef76e3" +
        "4438ffe4ddacf7992abada37e3c2c3ca927444cc3901b57e5602bc3cda256dbe1b"

    fun softwareSigner(): Signer = Signer.getInstance(Signer.privateKey(PRIVATE_KEY), Chain.Ethereum)

    fun softwareAddress(): Address = Signer.address(Signer.privateKey(PRIVATE_KEY))

    /** ethers emits v = 27/28, our signer 0/1; the backend accepts both. */
    fun assertSameSignature(expected: String, actual: String) {
        assertEquals(SIGNATURE_HEX_LENGTH, actual.length)
        assertEquals(expected.take(RS_HEX_END), actual.take(RS_HEX_END))
        assertEquals(expected.drop(RS_HEX_END).toInt(16) % 27, actual.drop(RS_HEX_END).toInt(16) % 27)
    }

    private const val RS_HEX_END = 2 + 64 * 2
    private const val SIGNATURE_HEX_LENGTH = RS_HEX_END + 2
}
