package cash.p.terminal.tangem.domain.usecase

import cash.p.terminal.tangem.domain.model.ProductType
import cash.p.terminal.tangem.domain.model.ScanResponse
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildHardwarePublicKeyUseCaseTest {

    private val tonPath = "m/44'/607'/0'"
    private val derivedKey = ByteArray(32) { 7 }

    @Test
    fun invoke_tonV4R2AndW5Queries_writeSameAddressKeyOnTonPath() {
        val keys = BuildHardwarePublicKeyUseCase()(
            scanResponse = wallet2ScanResponse(),
            accountId = "account",
            blockchainTypeList = listOf(
                TokenQuery(BlockchainType.Ton, TokenType.Native),
                TokenQuery(BlockchainType.Ton, TokenType.TonW5Native),
            ),
        )

        assertEquals(listOf(TokenType.Native, TokenType.TonW5Native), keys.map { it.tokenType })
        keys.forEach { key ->
            assertEquals(HardwarePublicKeyType.ADDRESS, key.type)
            assertEquals(tonPath, key.derivationPath)
            assertArrayEquals(derivedKey, key.derivedPublicKey)
        }
    }

    private fun wallet2ScanResponse(): ScanResponse {
        val card = mockk<Card>(relaxed = true) {
            every { settings.isHDWalletAllowed } returns true
            every { settings.isKeysImportAllowed } returns true
            every { firmwareVersion } returns FirmwareVersion.Ed25519Slip0010Available
            every { batchId } returns "AF99"
        }
        val extendedKey = ExtendedPublicKey(derivedKey, ByteArray(32), 0, ByteArray(4), 0L)
        val cardKeys = ExtendedPublicKeysMap(mapOf(DerivationPath(tonPath) to extendedKey))
        return ScanResponse(
            card = card,
            productType = ProductType.Wallet2,
            walletData = null,
            derivedKeys = mapOf(ByteArrayKey(ByteArray(32) { 1 }) to cardKeys),
        )
    }
}
