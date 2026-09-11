package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezor.domain.TrezorFirmwareVersionRecorder
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorBtcOutput
import cash.p.terminal.trezorkit.client.TrezorBtcSignResult
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.ShieldedCounts
import cash.p.zcash.TransparentSigningInput
import cash.p.zcash.TransparentSigningOutput
import cash.p.zcash.TransparentSigningRequest
import cash.p.zcash.ZcashAddressKind
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import cash.p.zcash.addressKind
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [TrezorZcashSigner] reviews a wallet-prepared PCZT's transparent bundle, has the Trezor sign it,
 * and applies the resulting ECDSA signatures back onto the transaction.
 */
class TrezorZcashSignerTest {

    private val wallet: ZcashWallet = mockk()
    private val session: TrezorClientSession = mockk()
    private val trezorClient: ITrezorClient = mockk()
    private val identityValidator: TrezorAccountIdentityValidator = mockk()
    private val firmwareVersionRecorder: TrezorFirmwareVersionRecorder = mockk(relaxed = true)

    private val transaction = PreparedTransaction(byteArrayOf(1, 2, 3))
    private val signedTransaction = PreparedTransaction(byteArrayOf(9, 9, 9))

    @Before
    fun setUp() {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        every { ZcashSdk.addressKind(any(), any()) } returns ZcashAddressKind.TRANSPARENT
        every { identityValidator.matchesDevice(any(), any()) } returns true
        coEvery { wallet.applyTransparentSignatures(any(), any(), any()) } returns signedTransaction
    }

    @After
    fun tearDown() = unmockkAll()

    private fun signer() = TrezorZcashSigner(
        accountId = ACCOUNT_ID,
        deviceId = DEVICE_ID,
        derivationPath = DERIVATION_PATH,
        trezorClient = trezorClient,
        identityValidator = identityValidator,
        firmwareVersionRecorder = firmwareVersionRecorder,
    )

    /** Runs [block] as the device session, so signBitcoin actually receives what the signer built. */
    private fun stubConnect(
        features: TrezorFeatures = features(),
        signResult: TrezorBtcSignResult = signResult(),
    ) {
        coEvery { session.getFeatures() } returns features
        coEvery { session.signBitcoin(any(), any(), any()) } returns signResult
        coEvery {
            trezorClient.connect(any<suspend TrezorClientSession.() -> TrezorBtcSignResult>())
        } coAnswers {
            firstArg<suspend TrezorClientSession.() -> TrezorBtcSignResult>()(session)
        }
    }

    private fun stubRequest(request: TransparentSigningRequest) {
        coEvery { wallet.transparentSigningRequest(transaction) } returns request
    }

    @Test
    fun sign_regularInput_assemblesAddressNWithScopeAndDindex() = runTest {
        stubRequest(request(inputs = listOf(signingInput(scope = 1, dindex = 7))))
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        val signTx = signTxSlot()
        val expected = listOf(hardened(84), hardened(0), hardened(0), 1, 7)
        assertEquals(expected, signTx.inputs.single().addressN)
    }

    @Test
    fun sign_asymmetricPrevTxid_passesThroughUnreversedToPrevHash() = runTest {
        stubRequest(request(inputs = listOf(signingInput(prevTxid = ASYMMETRIC_TXID))))
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        assertArrayEquals(hexDecode(ASYMMETRIC_TXID), signTxSlot().inputs.single().prevHash)
    }

    @Test
    fun sign_changeOutput_mapsToChangeWithAddressN() = runTest {
        stubRequest(request(outputs = listOf(changeOutput(scope = 1, dindex = 9))))
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        val output = signTxSlot().outputs.single() as TrezorBtcOutput.Change
        assertEquals(listOf(hardened(84), hardened(0), hardened(0), 1, 9), output.addressN)
    }

    @Test
    fun sign_addressOutput_passesAddressVerbatim() = runTest {
        val uaAddress = "u1someunifiedaddressstring"
        stubRequest(request(outputs = listOf(addressOutput(address = uaAddress))))
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        val output = signTxSlot().outputs.single() as TrezorBtcOutput.Address
        assertEquals(uaAddress, output.address)
    }

    @Test
    fun sign_anyRequest_alwaysUsesEmptyPrevTxMap() = runTest {
        stubRequest(request())
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        coVerify { session.signBitcoin(any(), any(), emptyMap()) }
    }

    @Test
    fun sign_shieldedBundle_refusedBeforeConnect() = runTest {
        stubRequest(request(shielded = ShieldedCounts(saplingSpends = 1, saplingOutputs = 0, orchardActions = 0)))

        var thrown: Throwable? = null
        try {
            signer().sign(wallet, ACCOUNT_INDEX, transaction)
        } catch (e: TrezorSigningException) {
            thrown = e
        }

        assertTrue("Expected TrezorSigningException, got $thrown", thrown is TrezorSigningException)
        verify { trezorClient wasNot Called }
    }

    @Test
    fun sign_nonV5Transaction_refusedBeforeConnect() = runTest {
        stubRequest(request(txVersion = 4))

        var thrown: Throwable? = null
        try {
            signer().sign(wallet, ACCOUNT_INDEX, transaction)
        } catch (e: TrezorSigningException) {
            thrown = e
        }

        assertTrue("Expected TrezorSigningException, got $thrown", thrown is TrezorSigningException)
        verify { trezorClient wasNot Called }
    }

    @Test
    fun sign_deviceSignatures_preservesCountAndOrderIntoApplyTransparentSignatures() = runTest {
        val sig0 = byteArrayOf(1, 1)
        val sig1 = byteArrayOf(2, 2)
        stubRequest(
            request(
                inputs = listOf(
                    signingInput(index = 5),
                    signingInput(index = 8),
                ),
            ),
        )
        stubConnect(signResult = signResult(signatures = listOf(sig0, sig1)))

        val result = signer().sign(wallet, ACCOUNT_INDEX, transaction)

        assertEquals(signedTransaction, result)
        coVerify {
            wallet.applyTransparentSignatures(
                transaction,
                intArrayOf(5, 8),
                arrayOf(sig0, sig1),
            )
        }
    }

    @Test
    fun sign_deviceFailsInsideSignBitcoin_neverAppliesSignatures() = runTest {
        stubRequest(request())
        coEvery { session.getFeatures() } returns features()
        coEvery { session.signBitcoin(any(), any(), any()) } throws
            TrezorSigningException("Device returned fewer signatures than inputs")
        coEvery {
            trezorClient.connect(any<suspend TrezorClientSession.() -> TrezorBtcSignResult>())
        } coAnswers {
            firstArg<suspend TrezorClientSession.() -> TrezorBtcSignResult>()(session)
        }

        var thrown: Throwable? = null
        try {
            signer().sign(wallet, ACCOUNT_INDEX, transaction)
        } catch (e: TrezorSigningException) {
            thrown = e
        }

        assertTrue(thrown is TrezorSigningException)
        coVerify(exactly = 0) { wallet.applyTransparentSignatures(any(), any(), any()) }
    }

    @Test
    fun sign_wrongDevice_refusedBeforeRecordAndSignBitcoin() = runTest {
        stubRequest(request())
        every { identityValidator.matchesDevice(DEVICE_ID, "some-other-device") } returns false
        stubConnect(features = features(deviceId = "some-other-device"))

        var thrown: Throwable? = null
        try {
            signer().sign(wallet, ACCOUNT_INDEX, transaction)
        } catch (e: TrezorSigningException) {
            thrown = e
        }

        assertTrue(thrown is TrezorSigningException)
        coVerify(exactly = 0) { firmwareVersionRecorder.record(any(), any()) }
        coVerify(exactly = 0) { session.signBitcoin(any(), any(), any()) }
    }

    @Test
    fun sign_matchingDevice_recordsFirmwareOncePerSession() = runTest {
        stubRequest(request())
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        coVerify(exactly = 1) { firmwareVersionRecorder.record(ACCOUNT_ID, any()) }
    }

    @Test
    fun sign_unifiedRecipientOnStaleFirmware_refusedBeforeSignBitcoinButRecordsVersion() = runTest {
        val uaAddress = "u1someunifiedaddressstring"
        stubRequest(request(outputs = listOf(addressOutput(address = uaAddress))))
        every { ZcashSdk.addressKind(uaAddress, any()) } returns ZcashAddressKind.UNIFIED
        stubConnect(features = features(firmwareVersion = "2.4.0"))

        var thrown: Throwable? = null
        try {
            signer().sign(wallet, ACCOUNT_INDEX, transaction)
        } catch (e: TrezorSigningException) {
            thrown = e
        }

        assertTrue(thrown is TrezorZcashUnsupportedAddressException)
        coVerify(exactly = 1) { firmwareVersionRecorder.record(ACCOUNT_ID, any()) }
        coVerify(exactly = 0) { session.signBitcoin(any(), any(), any()) }
    }

    @Test
    fun sign_unifiedRecipientOnCurrentFirmware_isAccepted() = runTest {
        val uaAddress = "u1someunifiedaddressstring"
        stubRequest(request(outputs = listOf(addressOutput(address = uaAddress))))
        every { ZcashSdk.addressKind(uaAddress, any()) } returns ZcashAddressKind.UNIFIED
        stubConnect(features = features(firmwareVersion = "2.6.0"))

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        coVerify(exactly = 1) { session.signBitcoin(any(), any(), any()) }
    }

    @Test
    fun sign_input_carriesSequenceIntact() = runTest {
        stubRequest(request(inputs = listOf(signingInput(sequence = 0xFFFFFFFEL))))
        stubConnect()

        signer().sign(wallet, ACCOUNT_INDEX, transaction)

        assertEquals(0xFFFFFFFEL, signTxSlot().inputs.single().sequence)
    }

    private fun signTxSlot() = io.mockk.slot<cash.p.terminal.trezorkit.client.TrezorBtcSignTx>()
        .also { coVerify { session.signBitcoin(any(), capture(it), any()) } }
        .captured

    private fun signingInput(
        index: Int = 0,
        prevTxid: String = DEFAULT_TXID,
        prevIndex: Int = 0,
        value: Long = 100_000L,
        sequence: Long = 0xFFFFFFFFL,
        scope: Int = 0,
        dindex: Int = 3,
    ) = TransparentSigningInput(
        index = index,
        prevTxid = prevTxid,
        prevIndex = prevIndex,
        value = value,
        sequence = sequence,
        scope = scope,
        dindex = dindex,
        scriptPubkey = "",
    )

    private fun addressOutput(index: Int = 0, value: Long = 90_000L, address: String = "t1recipient") =
        TransparentSigningOutput(index = index, value = value, address = address, isChange = false)

    private fun changeOutput(index: Int = 1, value: Long = 9_000L, scope: Int = 0, dindex: Int = 4) =
        TransparentSigningOutput(
            index = index,
            value = value,
            address = "",
            isChange = true,
            scope = scope,
            dindex = dindex,
        )

    private fun request(
        txVersion: Int = 5,
        shielded: ShieldedCounts = ShieldedCounts(0, 0, 0),
        inputs: List<TransparentSigningInput> = listOf(signingInput()),
        outputs: List<TransparentSigningOutput> = listOf(addressOutput()),
    ) = TransparentSigningRequest(
        txVersion = txVersion,
        versionGroupId = 0x26A7270AL,
        consensusBranchId = 0xC2D6D0B4L,
        expiryHeight = 123_456,
        lockTime = 0L,
        shielded = shielded,
        inputs = inputs,
        outputs = outputs,
    )

    private fun features(
        deviceId: String? = DEVICE_ID,
        internalModel: String? = "T2B1",
        firmwareVersion: String = "2.6.0",
    ) = TrezorFeatures(
        deviceId = deviceId,
        model = "Trezor Model T",
        internalModel = internalModel,
        firmwareVersion = firmwareVersion,
        passphraseProtection = false,
    )

    private fun signResult(signatures: List<ByteArray> = listOf(byteArrayOf(1, 2, 3))) =
        TrezorBtcSignResult(serializedTx = ByteArray(0), signatures = signatures)

    private fun hexDecode(hex: String) = ByteArray(hex.length / 2) {
        hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    private fun hardened(index: Int) = index or 0x80000000.toInt()

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val ACCOUNT_ID = "trezor-account-id"
        const val ACCOUNT_INDEX = 0
        const val DEVICE_ID = "trezor-device-id"
        const val DERIVATION_PATH = "m/84'/0'/0'"
        const val DEFAULT_TXID = "1111111111111111111111111111111111111111111111111111111111111111"
        // Asymmetric on purpose - a palindrome would not distinguish "unreversed" from "reversed".
        const val ASYMMETRIC_TXID = "aa11bb22cc33dd44ee55ff660011223344556677889900aabbccddeeff0011"
    }
}
