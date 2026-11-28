package cash.p.terminal.modules.nft

import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NftLinkTargetTest {

    @Test
    fun nftLinkTarget_httpsAndHttp_openInBrowserUnchanged() {
        assertEquals(NftLinkTarget.Web(HTTPS), nftLinkTarget(HTTPS))
        assertEquals(NftLinkTarget.Web("http://example.com"), nftLinkTarget("http://example.com"))
        assertEquals(NftLinkTarget.Web("HTTPS://Example.com"), nftLinkTarget("HTTPS://Example.com"))
    }

    @Test
    fun nftLinkTarget_tonTransfer_prefillsTonSend() {
        val target = assertIs<NftLinkTarget.Transfer>(
            nftLinkTarget("ton://transfer/$TON_ADDRESS?amount=1000000000&text=hi"),
        )

        assertEquals(listOf(BlockchainType.Ton), target.openSend.blockchainTypes)
        assertEquals(TON_ADDRESS, target.openSend.prefilledData.address)
        assertEquals(0, BigDecimal.ONE.compareTo(target.openSend.prefilledData.amount))
        assertEquals("hi", target.openSend.prefilledData.memo)
    }

    @Test
    fun nftLinkTarget_tonTransferWithHugeAmount_opensSendWithoutAmount() {
        val target = assertIs<NftLinkTarget.Transfer>(nftLinkTarget("ton://transfer/$TON_ADDRESS?value=1e2147483647"))

        assertEquals(null, target.openSend.prefilledData.amount)
    }

    @Test
    fun nftLinkTarget_notOpenableForms_unsupported() {
        listOf(
            "https://",
            "https:///path",
            "ton://stake/$TON_ADDRESS",
            "ton://transfer/",
            "javascript:alert(1)",
            "intent://scan#Intent;scheme=zxing;end",
            "file:///sdcard/wallet.db",
            "tc://?v=2&id=abc",
            "data:text/html,hi",
            "not a uri",
            "https://exa mple.com",
        ).forEach { assertEquals(NftLinkTarget.Unsupported, nftLinkTarget(it), it) }
    }

    @Test
    fun nftLinkTarget_hiddenCharacters_unsupported() {
        listOf(
            "https://example.com/‮gnp.exe",
            "https://example.com/​claim",
            "https://example.com/\nclaim",
            "https://example.com/\u0000",
            "https://example.com/󠁡claim",
            "https://example.com/\uD800claim",
            "https://example.com/ claim",
        ).forEach { assertEquals(NftLinkTarget.Unsupported, nftLinkTarget(it), it) }
    }

    private companion object {
        const val HTTPS = "https://example.com/claim?id=1"
        const val TON_ADDRESS = "UQBvW8Z5huBkMJYdnfAEM5JqTNkuWX3diqYENkWsIL0XggGG"
    }
}
