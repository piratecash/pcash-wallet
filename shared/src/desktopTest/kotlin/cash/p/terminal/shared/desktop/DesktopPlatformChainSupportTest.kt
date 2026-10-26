package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlatformChainSupportTest {

    @Test
    fun isSupported_everyEvmChain_returnsTrue() {
        assertEquals(11, EvmBlockchains.blockchainTypes.size)
        EvmBlockchains.blockchainTypes.forEach { type ->
            assertTrue(DesktopPlatformChainSupport.isSupported(wallet(type)), type.uid)
        }
    }

    @Test
    fun isSupported_everyNonEvmChain_returnsTrue() {
        listOf(
            BlockchainType.Bitcoin, BlockchainType.Litecoin, BlockchainType.BitcoinCash, BlockchainType.Dogecoin,
            BlockchainType.Dash, BlockchainType.ECash, BlockchainType.Cosanta, BlockchainType.PirateCash,
            BlockchainType.Zcash, BlockchainType.Monero, BlockchainType.Beam, BlockchainType.Ton,
            BlockchainType.Solana, BlockchainType.Tron, BlockchainType.Stellar, BlockchainType.Thorchain,
            BlockchainType.Mayachain,
        ).forEach { type ->
            assertTrue(DesktopPlatformChainSupport.isSupported(wallet(type)), type.uid)
        }
    }

    @Test
    fun isSupported_hardwareAccount_returnsFalse() {
        listOf(BlockchainType.Ethereum, BlockchainType.Bitcoin, BlockchainType.Monero).forEach { type ->
            assertFalse(DesktopPlatformChainSupport.isSupported(wallet(type, hardware = true)), type.uid)
        }
    }

    private fun wallet(type: BlockchainType, hardware: Boolean = false): Wallet = mockk {
        every { token.blockchainType } returns type
        every { account.isHardwareWalletAccount } returns hardware
    }
}
