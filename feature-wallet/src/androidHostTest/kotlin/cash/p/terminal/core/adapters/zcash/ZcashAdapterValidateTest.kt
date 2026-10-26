package cash.p.terminal.core.adapters.zcash

import cash.p.zcash.AddressReceivers
import cash.p.zcash.ZcashAddressKind
import cash.p.zcash.ZcashSdk
import cash.p.zcash.addressKind
import cash.p.zcash.addressReceivers
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * `validate` gates what a Zcash wallet may send to. A Trezor account can only sign a
 * transparent bundle, so on top of the ordinary address-kind check it refuses shielded/TEX
 * addresses outright and refuses a unified address unless it both carries a transparent
 * receiver and the account's stored firmware is known to support one. A non-Trezor account is
 * unrestricted, and that must not regress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterValidateTest : ZcashAdapterTestFixture() {

    @Before
    fun stubAddressParsing() {
        mockkStatic("cash.p.zcash.ZcashSdkKt")
    }

    // region Non-Trezor account is unrestricted (regression)

    @Test
    fun validate_nonTrezorAccount_transparentAddress_returnsTransparent() = runTest(dispatcher) {
        every { ZcashSdk.addressKind(TRANSPARENT_ADDRESS, any()) } returns ZcashAddressKind.TRANSPARENT
        adapter = createAdapter()

        assertEquals(ZcashAdapter.ZCashAddressType.Transparent, adapter.validate(TRANSPARENT_ADDRESS))
    }

    @Test
    fun validate_nonTrezorAccount_saplingAddress_returnsShielded() = runTest(dispatcher) {
        every { ZcashSdk.addressKind(SAPLING_ADDRESS, any()) } returns ZcashAddressKind.SAPLING
        adapter = createAdapter()

        assertEquals(ZcashAdapter.ZCashAddressType.Shielded, adapter.validate(SAPLING_ADDRESS))
    }

    @Test
    fun validate_nonTrezorAccount_unifiedAddressWithoutTransparentReceiver_returnsUnified() = runTest(dispatcher) {
        every { ZcashSdk.addressKind(UNIFIED_ADDRESS, any()) } returns ZcashAddressKind.UNIFIED
        adapter = createAdapter()

        assertEquals(ZcashAdapter.ZCashAddressType.Unified, adapter.validate(UNIFIED_ADDRESS))
    }

    // endregion

    // region Trezor account: transparent-only bundle

    @Test
    fun validate_trezorAccount_transparentAddress_returnsTransparent() = runTest(dispatcher) {
        stubTrezorAccount()
        every { ZcashSdk.addressKind(TRANSPARENT_ADDRESS, any()) } returns ZcashAddressKind.TRANSPARENT
        adapter = createAdapter()

        assertEquals(ZcashAdapter.ZCashAddressType.Transparent, adapter.validate(TRANSPARENT_ADDRESS))
    }

    @Test
    fun validate_trezorAccount_saplingAddress_throwsTrezorTransparentOnly() = runTest(dispatcher) {
        stubTrezorAccount()
        every { ZcashSdk.addressKind(SAPLING_ADDRESS, any()) } returns ZcashAddressKind.SAPLING
        adapter = createAdapter()

        assertFailsWith<ZcashAdapter.ZcashError.TrezorTransparentOnly> {
            adapter.validate(SAPLING_ADDRESS)
        }
    }

    @Test
    fun validate_trezorAccount_texAddress_throwsTrezorTransparentOnly() = runTest(dispatcher) {
        stubTrezorAccount()
        every { ZcashSdk.addressKind(TEX_ADDRESS, any()) } returns ZcashAddressKind.TEX
        adapter = createAdapter()

        assertFailsWith<ZcashAdapter.ZcashError.TrezorTransparentOnly> {
            adapter.validate(TEX_ADDRESS)
        }
    }

    // endregion

    // region Trezor account: unified address needs a transparent receiver

    @Test
    fun validate_trezorAccount_unifiedAddressWithoutTransparentReceiver_throwsNoTransparentReceiver() =
        runTest(dispatcher) {
            stubTrezorAccount()
            every { ZcashSdk.addressKind(UNIFIED_ADDRESS, any()) } returns ZcashAddressKind.UNIFIED
            every { ZcashSdk.addressReceivers(UNIFIED_ADDRESS, any()) } returns
                AddressReceivers(hasTransparent = false)
            adapter = createAdapter()

            assertFailsWith<ZcashAdapter.ZcashError.TrezorUnifiedAddressNoTransparentReceiver> {
                adapter.validate(UNIFIED_ADDRESS)
            }
        }

    // endregion

    // region Trezor account: unified address also needs firmware that can route it

    @Test
    fun validate_trezorAccount_unifiedAddressOnSupportedFirmware_returnsUnified() = runTest(dispatcher) {
        stubTrezorAccount(storedModel = "T2B1", storedFirmwareVersion = "2.6.0")
        every { ZcashSdk.addressKind(UNIFIED_ADDRESS, any()) } returns ZcashAddressKind.UNIFIED
        every { ZcashSdk.addressReceivers(UNIFIED_ADDRESS, any()) } returns
            AddressReceivers(hasTransparent = true)
        adapter = createAdapter()

        assertEquals(ZcashAdapter.ZCashAddressType.Unified, adapter.validate(UNIFIED_ADDRESS))
    }

    @Test
    fun validate_trezorAccount_unifiedAddressOnStaleFirmware_throwsFirmwareRequired() = runTest(dispatcher) {
        stubTrezorAccount(storedModel = "T2B1", storedFirmwareVersion = "2.4.0")
        every { ZcashSdk.addressKind(UNIFIED_ADDRESS, any()) } returns ZcashAddressKind.UNIFIED
        every { ZcashSdk.addressReceivers(UNIFIED_ADDRESS, any()) } returns
            AddressReceivers(hasTransparent = true)
        adapter = createAdapter()

        assertFailsWith<ZcashAdapter.ZcashError.TrezorFirmwareRequired> {
            adapter.validate(UNIFIED_ADDRESS)
        }
    }

    @Test
    fun validate_trezorAccount_unifiedAddressOnTrezorOne_throwsFirmwareRequired() = runTest(dispatcher) {
        // Trezor One never supports unified addresses, regardless of firmware.
        stubTrezorAccount(storedModel = "T1B1", storedFirmwareVersion = "9.9.9")
        every { ZcashSdk.addressKind(UNIFIED_ADDRESS, any()) } returns ZcashAddressKind.UNIFIED
        every { ZcashSdk.addressReceivers(UNIFIED_ADDRESS, any()) } returns
            AddressReceivers(hasTransparent = true)
        adapter = createAdapter()

        assertFailsWith<ZcashAdapter.ZcashError.TrezorFirmwareRequired> {
            adapter.validate(UNIFIED_ADDRESS)
        }
    }

    @Test
    fun validate_trezorAccount_unifiedAddressWithAbsentAccountMetadata_throwsFirmwareRequired() = runTest(dispatcher) {
        // Legacy/unreadable stored metadata must fail closed, with the same refusal as stale firmware.
        stubTrezorAccount(storedModel = null)
        every { ZcashSdk.addressKind(UNIFIED_ADDRESS, any()) } returns ZcashAddressKind.UNIFIED
        every { ZcashSdk.addressReceivers(UNIFIED_ADDRESS, any()) } returns
            AddressReceivers(hasTransparent = true)
        adapter = createAdapter()

        assertFailsWith<ZcashAdapter.ZcashError.TrezorFirmwareRequired> {
            adapter.validate(UNIFIED_ADDRESS)
        }
    }

    // endregion

    private companion object {
        const val TRANSPARENT_ADDRESS = "t1RecipientAddress"
        const val SAPLING_ADDRESS = "zs1RecipientAddress"
        const val UNIFIED_ADDRESS = "u1RecipientAddress"
        const val TEX_ADDRESS = "tex1RecipientAddress"
    }
}
