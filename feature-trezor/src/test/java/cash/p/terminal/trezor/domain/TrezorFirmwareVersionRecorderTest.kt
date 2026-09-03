package cash.p.terminal.trezor.domain

import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrezorFirmwareVersionRecorderTest {

    private val accountManager: IAccountManager = mockk(relaxed = true)
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage = mockk(relaxed = true)
    private val identityValidator = TrezorAccountIdentityValidator(hardwarePublicKeyStorage)
    private val recorder = TrezorFirmwareVersionRecorder(accountManager, identityValidator)

    @Test
    fun record_legacyAccountEmptyMetadata_writesReportedVersion() {
        every { accountManager.account(ACCOUNT_ID) } returns
            account(trezorDevice(deviceId = "unknown", model = "", firmwareVersion = ""))

        runBlocking { recorder.record(ACCOUNT_ID, features(firmwareVersion = "2.10.0")) }

        verify(exactly = 1) {
            accountManager.update(
                match { (it.type as AccountType.TrezorDevice).firmwareVersion == "2.10.0" },
            )
        }
    }

    @Test
    fun record_changedVersion_updatesExactlyOnce() {
        every { accountManager.account(ACCOUNT_ID) } returns account(trezorDevice(firmwareVersion = "2.8.0"))

        runBlocking { recorder.record(ACCOUNT_ID, features(firmwareVersion = "2.10.0")) }

        verify(exactly = 1) {
            accountManager.update(
                match { (it.type as AccountType.TrezorDevice).firmwareVersion == "2.10.0" },
            )
        }
    }

    @Test
    fun record_unchangedVersion_doesNotUpdate() {
        every { accountManager.account(ACCOUNT_ID) } returns account(trezorDevice(firmwareVersion = "2.10.0"))

        runBlocking { recorder.record(ACCOUNT_ID, features(firmwareVersion = "2.10.0")) }

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun record_deviceIdMismatch_writesNothing() {
        every { accountManager.account(ACCOUNT_ID) } returns
            account(trezorDevice(deviceId = "other-device", firmwareVersion = "2.8.0"))

        runBlocking { recorder.record(ACCOUNT_ID, features(deviceId = DEVICE_ID, firmwareVersion = "2.10.0")) }

        verify(exactly = 0) { accountManager.update(any()) }
    }

    @Test
    fun record_storedIdUnknown_recordsConnectedDeviceVersion() {
        every { accountManager.account(ACCOUNT_ID) } returns
            account(trezorDevice(deviceId = "unknown", firmwareVersion = "2.8.0"))

        runBlocking { recorder.record(ACCOUNT_ID, features(deviceId = DEVICE_ID, firmwareVersion = "2.10.0")) }

        verify(exactly = 1) {
            accountManager.update(
                match { (it.type as AccountType.TrezorDevice).firmwareVersion == "2.10.0" },
            )
        }
    }

    @Test
    fun record_nonTrezorAccount_writesNothingInsteadOfThrowing() {
        every { accountManager.account(ACCOUNT_ID) } returns
            Account(
                id = ACCOUNT_ID,
                name = "Mnemonic",
                type = AccountType.Mnemonic(List(12) { "abandon" }, passphrase = ""),
                origin = AccountOrigin.Restored,
                level = 0,
            )

        runBlocking { recorder.record(ACCOUNT_ID, features(firmwareVersion = "2.10.0")) }

        verify(exactly = 0) { accountManager.update(any()) }
    }

    /**
     * The reader that matters (Phase 3 step 6/7's invariant) is a live `accountManager.account(id)`
     * lookup, never a captured `Account`/`Wallet` snapshot. Substituted here for the real `Wallet`
     * (its constructor is internal to `:core:wallet` and unreachable from this module): a
     * `staleAccountType` stands in for what a cached `Wallet`'s captured `Account` would still show
     * after `record()` writes a new version, since `Account.equals` is id-only and never observes it.
     */
    @Test
    fun record_thenSupportsUnifiedAddress_liveReadReflectsWriteWithNoRebuild() {
        val staleAccountType = trezorDevice(firmwareVersion = "2.5.2")
        var storedAccount = account(staleAccountType)
        every { accountManager.account(ACCOUNT_ID) } answers { storedAccount }
        val updated = slot<Account>()
        every { accountManager.update(capture(updated)) } answers { storedAccount = updated.captured }

        assertFalse(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(
                staleAccountType.model,
                staleAccountType.firmwareVersion,
            ),
        )

        runBlocking { recorder.record(ACCOUNT_ID, features(firmwareVersion = "2.5.3")) }

        // The stale captured snapshot (stand-in for a cached Wallet's Account) never observes the write.
        assertFalse(
            TrezorZcashAdmissionPolicy.supportsUnifiedAddress(
                staleAccountType.model,
                staleAccountType.firmwareVersion,
            ),
        )
        // The live accountManager.account(id) read does, immediately, with no rebuild in between.
        val liveType = accountManager.account(ACCOUNT_ID)?.type as AccountType.TrezorDevice
        assertTrue(TrezorZcashAdmissionPolicy.supportsUnifiedAddress(liveType.model, liveType.firmwareVersion))
    }

    private fun account(type: AccountType.TrezorDevice) = Account(
        id = ACCOUNT_ID,
        name = "Trezor",
        type = type,
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private fun trezorDevice(
        deviceId: String = DEVICE_ID,
        model: String = "T3T1",
        firmwareVersion: String,
    ) = AccountType.TrezorDevice(
        deviceId = deviceId,
        model = model,
        firmwareVersion = firmwareVersion,
        walletPublicKey = "wallet-public-key",
    )

    private fun features(deviceId: String? = DEVICE_ID, firmwareVersion: String) = TrezorFeatures(
        deviceId = deviceId,
        model = "Trezor Safe 5",
        internalModel = "T3T1",
        firmwareVersion = firmwareVersion,
        passphraseProtection = false,
    )

    companion object {
        private const val ACCOUNT_ID = "acc-1"
        private const val DEVICE_ID = "dev-1"
    }
}
