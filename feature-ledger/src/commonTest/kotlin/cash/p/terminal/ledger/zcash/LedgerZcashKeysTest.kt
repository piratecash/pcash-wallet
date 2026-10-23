package cash.p.terminal.ledger.zcash

import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.zcash.FakeZcashApp.Companion.GET_VK
import cash.p.terminal.ledger.zcash.FakeZcashApp.Companion.GET_WALLET_PUBLIC_KEY
import cash.p.terminal.ledger.zcash.FakeZcashApp.Companion.P1
import cash.p.terminal.ledger.zcash.ZcashFixtures.ACCOUNT_KEY_COMPRESSED
import cash.p.terminal.ledger.zcash.ZcashFixtures.ADDRESS_7
import cash.p.terminal.ledger.zcash.ZcashFixtures.DEVICE_ORCHARD_ADDRESS
import cash.p.terminal.ledger.zcash.ZcashFixtures.DEVICE_UFVK
import cash.p.terminal.ledger.zcash.ZcashFixtures.transparent
import cash.p.terminal.ledger.zcash.ZcashFixtures.unified
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareAddressLocator
import cash.p.terminal.wallet.hardware.HardwareAddressPosition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerZcashKeysTest {

    private val fetcher = LedgerZcashKeyFetcher()

    @Test
    fun fetchKeys_transparentAndUnified_readsViewingKeyOnceWithAccountKey() = runTest {
        val device = FakeZcashApp()

        val keys = fetcher.fetchKeys(device, listOf(transparent, unified))

        assertEquals(listOf(transparent, unified), keys.map { it.spec.query })
        assertTrue(keys.all { it.key == DEVICE_UFVK })
        keys.forEach { assertContentEquals(ACCOUNT_KEY_COMPRESSED.hexToByteArray(), it.publicKey) }
        assertEquals(1, device.sent(GET_VK).size)
    }

    @Test
    fun holdsKey_accountKey_comparesWithoutAskingForViewingKey() = runTest {
        val device = FakeZcashApp()
        val accountKey = ACCOUNT_KEY_COMPRESSED.hexToByteArray()

        assertTrue(fetcher.holdsKey(device, unified, DEVICE_UFVK, accountKey))
        assertFalse(fetcher.holdsKey(device, unified, DEVICE_UFVK, accountKey.reversedArray()))
        assertTrue(device.sent(GET_VK).isEmpty())
    }

    @Test
    fun verify_freshTransparentAddress_showsItsIndexOnDevice() = runTest {
        val device = FakeZcashApp()

        verifier(position = 7).verify(device, wallet(transparent), ADDRESS_7)

        assertEquals(ADDRESS_7_PATH, shownPath(device))
    }

    @Test
    fun verify_transparentAddressPastIndex1000_showsItsIndexOnDevice() = runTest {
        val device = FakeZcashApp()

        verifier(position = 1_000).verify(device, wallet(transparent), ADDRESS_7)

        assertEquals(ADDRESS_1000_PATH, shownPath(device))
    }

    @Test
    fun verify_addressTheWalletDoesNotOwn_neverShowsIt() = runTest {
        val device = FakeZcashApp()

        assertFailsWith<IllegalStateException> {
            verifier(position = null).verify(device, wallet(transparent), "t1Rv4exT7bqhZqi2j7xz8bUHDMxwosrjADU")
        }
        assertTrue(device.sent(GET_WALLET_PUBLIC_KEY).isEmpty())
    }

    @Test
    fun verify_unifiedAddress_deviceShowsAnother_throwsIdentityMismatch() = runTest {
        verifier(position = null).verify(FakeZcashApp(), wallet(unified), DEVICE_ORCHARD_ADDRESS)

        assertFailsWith<LedgerIdentityMismatchException> {
            val otherAccount = FakeZcashApp(unifiedAddress = OTHER_ORCHARD_ADDRESS)
            verifier(position = null).verify(otherAccount, wallet(unified), DEVICE_ORCHARD_ADDRESS)
        }
    }

    private fun verifier(position: Int?): LedgerZcashAddressVerifier {
        val locator = mockk<IReceiveAdapter>(moreInterfaces = arrayOf(HardwareAddressLocator::class))
        coEvery { (locator as HardwareAddressLocator).locate(any()) } returns
            position?.let { HardwareAddressPosition(change = false, index = it) }
        return LedgerZcashAddressVerifier(mockk { every { getReceiveAdapterForWallet(any()) } returns locator })
    }

    private fun shownPath(device: FakeZcashApp): String {
        val shown = device.sent(GET_WALLET_PUBLIC_KEY).single()
        assertEquals(1, shown[P1].toInt())
        return shown.copyOfRange(APDU_HEADER, shown.size).toHexString()
    }

    private fun wallet(query: TokenQuery) = mockk<Wallet> { every { token.tokenQuery } returns query }

    private companion object {
        const val APDU_HEADER = 5

        // Five levels: 44'/133'/0'/0/7, big-endian.
        const val ADDRESS_7_PATH = "058000002c80000085800000000000000000000007"
        const val ADDRESS_1000_PATH = "058000002c800000858000000000000000000003e8"

        // Account 1 of the same seed, read on speculos.
        const val OTHER_ORCHARD_ADDRESS =
            "u1n4d94z4l9zs0kxhhytwyktg3rsmr9u0eagt3kn78j9m3lmnuzswuwn63az5jzfwqmvrfn0g8s3rvvg0wr0pklnkejm6d69hv8u5g6w9e"
    }
}
