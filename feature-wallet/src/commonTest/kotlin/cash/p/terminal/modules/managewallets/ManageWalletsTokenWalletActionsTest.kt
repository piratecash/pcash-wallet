package cash.p.terminal.modules.managewallets

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.policy.PlatformChainSupport
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageWalletsTokenWalletActionsTest {

    private val platformChainSupport = mockk<PlatformChainSupport> {
        every { isSupported(any<BlockchainType>()) } returns true
    }
    private val noHardwareWalletsPolicy = mockk<HardwareWalletTokenPolicy> {
        every { isSupported(any<Token>()) } returns false
    }
    private val actions = ManageWalletsTokenWalletActions(platformChainSupport, noHardwareWalletsPolicy)
    private val ethereum = Token(
        coin = Coin(uid = "ethereum", name = "Ethereum", code = "ETH"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Native,
        decimals = 18,
    )

    @Test
    fun supports_hardwareAccountRejectedByPolicy_returnsFalse() {
        val hardwareCard = AccountType.HardwareCard(
            cardId = "card-id",
            backupCardsCount = 0,
            walletPublicKey = "wallet-public-key",
            signedHashes = 0,
        )

        assertFalse(actions.supports(ethereum, hardwareCard))
    }

    @Test
    fun supports_mnemonicAccount_ignoresHardwarePolicy() {
        assertTrue(actions.supports(ethereum, AccountType.Mnemonic(List(12) { "word$it" }, "")))
    }
}
