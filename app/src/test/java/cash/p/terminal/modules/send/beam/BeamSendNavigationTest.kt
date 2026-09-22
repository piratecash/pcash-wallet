package cash.p.terminal.modules.send.beam

import cash.p.terminal.modules.send.SendConfirmationFragment.Type
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeamSendNavigationTest {
    @Test
    fun proceed_beamWithNullOrLegacyAddress_bypassesAllGenericChecks() {
        for (address in listOf(null, "private-beam-receiver-fixture")) {
            assertRoute(BlockchainType.Beam, Type.Beam, address, expectedBeam = 1)
        }
    }

    @Test
    fun proceed_mismatchedBeamTypeOrChain_stillBypassesGenericChecks() {
        assertRoute(BlockchainType.Beam, Type.Bitcoin, "private-beam-receiver-fixture", expectedBeam = 1)
        assertRoute(BlockchainType.Bitcoin, Type.Beam, "private-beam-receiver-fixture", expectedBeam = 1)
    }

    @Test
    fun proceed_otherChain_preservesGenericChecks() {
        assertRoute(BlockchainType.Bitcoin, Type.Bitcoin, "bitcoin-address", expectedBeam = 0)
    }

    @Test
    fun nativeAdmission_rejectsGamingNonNativeAndWrongSession() {
        val account = mockk<Account> {
            every { id } returns "account"
            every { type } returns AccountType.Mnemonic(emptyList(), "")
        }
        val token = mockk<Token> {
            every { blockchainType } returns BlockchainType.Beam
            every { type } returns TokenType.Native
            every { decimals } returns 8
        }
        val coin = mockk<Coin> { every { uid } returns "beam" }
        val wallet = mockk<Wallet> {
            every { this@mockk.account } returns account
            every { this@mockk.token } returns token
            every { this@mockk.coin } returns coin
        }
        val session = mockk<BeamSessionOwner.Session> {
            every { accountId } returns "account"
            every { network } returns BeamNetwork.Mainnet
        }
        val adapter = mockk<BeamAdapter> { every { accountId } returns "account" }

        assertTrue(isNativeBeamSendWallet(wallet, session, adapter))
        every { coin.uid } returns "beam-2"
        assertFalse(isNativeBeamSendWallet(wallet, session, adapter))
        every { coin.uid } returns "beam"
        every { token.type } returns TokenType.Mweb
        assertFalse(isNativeBeamSendWallet(wallet, session, adapter))
        every { token.type } returns TokenType.Native
        every { session.accountId } returns "other"
        assertFalse(isNativeBeamSendWallet(wallet, session, adapter))
        assertFalse(isNativeBeamSendWallet(wallet, null, adapter))
    }

    private fun assertRoute(chain: BlockchainType, type: Type, address: String?, expectedBeam: Int) {
        val wallet = mockk<Wallet>()
        every { wallet.token.blockchainType } returns chain
        var beamCalls = 0
        var genericCalls = 0
        if (!handleBeamProceed(ProceedActionData(address, wallet, type)) { beamCalls++ }) genericCalls++
        assertEquals(expectedBeam, beamCalls)
        assertEquals(1 - expectedBeam, genericCalls)
    }
}
