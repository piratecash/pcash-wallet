package cash.p.terminal.core.managers

import android.content.Context
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.network.Network
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test

class ThorchainKitManagersTest {

    private val context = mockk<Context>()
    private val managers = ThorchainKitManagers(context, mockk(), mockk(), mockk(), mockk(), mockk())

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun clear_anyAccount_clearsBothNetworks() {
        mockkObject(ThorchainKit.Companion)
        every { ThorchainKit.clear(any(), any(), any()) } returns Unit

        managers.clear(ACCOUNT_ID)

        verify(exactly = 1) { ThorchainKit.clear(context, Network.Mainnet, ACCOUNT_ID) }
        verify(exactly = 1) { ThorchainKit.clear(context, Network.MayaMainnet, ACCOUNT_ID) }
    }

    @Test
    fun forType_thorchainAndMaya_returnsTheirManagers() {
        assertSame(managers.thorchain, managers.forType(BlockchainType.Thorchain))
        assertSame(managers.maya, managers.forType(BlockchainType.Mayachain))
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
    }
}
