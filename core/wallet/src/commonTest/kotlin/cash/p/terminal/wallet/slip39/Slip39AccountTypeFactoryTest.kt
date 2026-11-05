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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun createNew_bits128_returnsSingle20WordShare() = runTest {
        val accountType = factory().createNew(Slip39SecretSize.Bits128, "")
        val share = Slip39Shamir.parseShare(accountType.words.joinToString(" "))

        assertEquals(20, accountType.words.size)
        assertTrue(share.isSingleShare)
        assertTrue(share.extendable)
        assertEquals(1, share.iterationExponent)
        assertEquals(16, accountType.seed.size)
    }

    @Test
    fun createNew_bits256_returns33WordShareWith32ByteSeed() = runTest {
        val accountType = factory().createNew(Slip39SecretSize.Bits256, "")

        assertEquals(33, accountType.words.size)
        assertTrue(Slip39Shamir.parseShare(accountType.words.joinToString(" ")).isSingleShare)
        assertEquals(32, accountType.seed.size)
    }

    @Test
    fun createNew_seedEqualsDecryptOfOwnShare() = runTest {
        val accountType = factory().createNew(Slip39SecretSize.Bits128, PASSPHRASE)
        val ems = combine(listOf(accountType.words.joinToString(" ")))

        assertEquals(PASSPHRASE, accountType.passphrase)
        assertArrayEquals(ems.decrypt(PASSPHRASE), accountType.seed)
        assertFalse(ems.decrypt("").contentEquals(accountType.seed))
    }

    @Test
    fun createNew_sharesSplit2of3_anyTwoRecoverSameSeed() = runTest {
        val random = SecureRandom()
        val accountType = factory().createNew(Slip39SecretSize.Bits128, PASSPHRASE)
        val ems = combine(listOf(accountType.words.joinToString(" ")))
        val newSet = EncryptedMasterSecret(
            identifier = Slip39Shamir.randomIdentifier(random),
            extendable = true,
            iterationExponent = ems.iterationExponent,
            ciphertext = ems.ciphertext,
        )
        val shares = Slip39Shamir.split(newSet, 2, 3, random).map { it.joinToString(" ") }

        listOf(0 to 1, 0 to 2, 1 to 2).forEach { (a, b) ->
            assertArrayEquals(accountType.seed, combine(listOf(shares[a], shares[b])).decrypt(PASSPHRASE))
        }
    }

    @Test
    fun createNew_twoCalls_differentWords() = runTest {
        val factory = factory()

        assertNotEquals(
            factory.createNew(Slip39SecretSize.Bits128, "").words,
            factory.createNew(Slip39SecretSize.Bits128, "").words,
        )
    }

    private fun TestScope.factory() = Slip39AccountTypeFactory(
        mockk<DispatcherProvider> { every { default } returns StandardTestDispatcher(testScheduler) }
    )

    private companion object {
        const val PASSPHRASE = "TREZOR"
    }
}
