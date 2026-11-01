package cash.p.terminal.wallet.slip39

import cash.p.terminal.wallet.slip39.Slip39VectorsTest.Companion.BASIC_20_3OF6
import cash.p.terminal.wallet.slip39.Slip39VectorsTest.Companion.BASIC_20_3OF6_XPRV
import cash.p.terminal.wallet.slip39.Slip39VectorsTest.Companion.BASIC_33_2OF5
import cash.p.terminal.wallet.slip39.Slip39VectorsTest.Companion.BASIC_33_2OF5_XPRV
import cash.p.terminal.wallet.slip39.Slip39VectorsTest.Companion.BASIC_EXT_20_2OF3
import cash.p.terminal.wallet.slip39.Slip39VectorsTest.Companion.BASIC_EXT_20_2OF3_XPRV
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDWallet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertFailsWith

class Slip39AccountTypeFactoryTest {

    @Test
    fun create_canonicalShareOfTrezorSet_seedDerivesTrezorRootXprv() = runTest {
        val factory = factory()
        listOf(
            BASIC_20_3OF6 to BASIC_20_3OF6_XPRV,
            BASIC_EXT_20_2OF3 to BASIC_EXT_20_2OF3_XPRV,
            BASIC_33_2OF5 to BASIC_33_2OF5_XPRV,
        ).forEach { (shares, xprv) ->
            val canonicalShare = Slip39Shamir.split(combine(shares), 1, 1, SecureRandom()).single()

            val accountType = factory.create(canonicalShare, "TREZOR")

            assertEquals(canonicalShare, accountType.words)
            assertEquals(xprv, HDExtendedKey(accountType.seed, HDWallet.Purpose.BIP44).serializePrivate())
        }
    }

    @Test
    fun create_memberOfMultiShareSet_throwsIllegalArgument() = runTest {
        val factory = factory()

        assertFailsWith<IllegalArgumentException> { factory.create(BASIC_20_3OF6.first().split(" "), "TREZOR") }
    }

    private fun TestScope.factory() = Slip39AccountTypeFactory(
        mockk<DispatcherProvider> { every { default } returns StandardTestDispatcher(testScheduler) }
    )
}
