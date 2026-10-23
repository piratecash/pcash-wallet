package cash.p.terminal.ledger.zcash

import cash.p.terminal.ledger.account.AccountFixtures.account
import cash.p.terminal.ledger.account.AccountFixtures.signingKey
import cash.p.terminal.ledger.account.AccountFixtures.storedKey
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledger.zcash.ZcashFixtures.DEVICE_UFVK
import cash.p.terminal.ledger.zcash.ZcashFixtures.unified
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.wallet.hardware.HardwareCancelledException
import cash.p.zcash.ExternalAction
import cash.p.zcash.ExternalActionBundle
import cash.p.zcash.ExternalKeyDerivation
import cash.p.zcash.ExternalSignatures
import cash.p.zcash.ExternalSigningView
import cash.p.zcash.ExternalTransparentInput
import cash.p.zcash.ExternalTransparentOutput
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.ZcashWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerZcashSignerTest {

    private val stored = storedKey(unified, DEVICE_UFVK)
    private val wallet = mockk<ZcashWallet> {
        coEvery { proveForExternalSigner(PLANNED) } returns PROVED
        coEvery { externalSigningView(PROVED, LEDGER_ZCASH_ACCOUNT) } returns view(V5, orchard = bundle())
        coEvery { applyExternalSignatures(PROVED, any()) } returns SIGNED
    }

    @Test
    fun toLedger_v5View_mapsHexToBytesAndPathsWithHardenedBit() {
        val transaction = view(V5, orchard = bundle()).toLedger()

        assertEquals(V5, transaction.txVersion)
        assertEquals(EXPIRY.toLong(), transaction.expiryHeight)
        assertEquals(COIN_TYPE.toLong(), transaction.coinType)
        val input = transaction.transparentInputs.single()
        assertContentEquals(TXID.hexToByteArray(), input.prevoutTxid)
        assertEquals(PREVOUT_INDEX.toLong(), input.prevoutIndex)
        assertEquals(listOf(HARDENED + 44u, HARDENED + 133u, HARDENED, 0u, 3u), input.derivation.path.indices)
        assertContentEquals(PUBKEY.hexToByteArray(), input.derivation.pubkey)
        assertNull(transaction.transparentOutputs.first().derivation)
        assertEquals(listOf(HARDENED + 44u, HARDENED + 133u, HARDENED, 1u, 0u), transaction.transparentOutputs.last().derivation?.path?.indices)
        val action = checkNotNull(transaction.orchard).actions.single()
        assertEquals(listOf(HARDENED + 32u, HARDENED + 133u, HARDENED), action.path.indices)
        assertContentEquals(RECIPIENT.hexToByteArray(), action.recipient)
        assertEquals(listOf(0), transaction.orchard?.signIndices)
        assertNull(transaction.ironwood)
    }

    @Test
    fun toLedger_v6View_keepsIronwoodBundleApartFromOrchard() {
        val transaction = view(V6, orchard = null, ironwood = bundle(valueBalance = -5)).toLedger()

        assertEquals(V6, transaction.txVersion)
        assertNull(transaction.orchard)
        assertEquals(-5, transaction.ironwood?.valueBalance)
        assertEquals(listOf(0), transaction.ironwood?.signIndices)
    }

    @Test
    fun sign_deviceSigns_appliesItsSignaturesByIndex() = runTest {
        val device = FakeZcashApp(answers = mapOf(SIGN_ORCHARD to ORCHARD_SIGNATURE, SIGN_TRANSPARENT to DER_SIGNATURE))
        val applied = slot<ExternalSignatures>()
        coEvery { wallet.applyExternalSignatures(PROVED, capture(applied)) } returns SIGNED

        val signed = LedgerZcashSigner(signingKey(device, stored)).sign(wallet, PLANNED)

        assertContentEquals(SIGNED.bytes, signed.bytes)
        assertContentEquals(ORCHARD_SIGNATURE, applied.captured.orchard.getValue(0))
        assertContentEquals(DER_SIGNATURE, applied.captured.transparent.getValue(0))
        assertTrue(applied.captured.ironwood.isEmpty())
    }

    @Test
    fun sign_deviceOutOfKeyDerivations_failsAsKeyDerivationLimit() = runTest {
        val device = FakeZcashApp(statuses = mapOf(SIGN_ORCHARD to NOT_ENOUGH_MEMORY))

        assertFailsWith<LedgerException.KeyDerivationLimit> {
            LedgerZcashSigner(signingKey(device, stored)).sign(wallet, PLANNED)
        }
        coVerify(exactly = 0) { wallet.applyExternalSignatures(any(), any()) }
    }

    @Test
    fun sign_otherDeviceConnected_neverAppliesSignatures() = runTest {
        assertFailsWith<LedgerIdentityMismatchException> { signer(LedgerIdentityMismatchException()).sign(wallet, PLANNED) }

        coVerify(exactly = 0) { wallet.applyExternalSignatures(any(), any()) }
    }

    @Test
    fun sign_rejectedOnDevice_failsAsCancellation() = runTest {
        assertFailsWith<HardwareCancelledException> { signer(LedgerRejectedException()).sign(wallet, PLANNED) }

        coVerify(exactly = 0) { wallet.applyExternalSignatures(any(), any()) }
    }

    private fun signer(failure: Exception): LedgerZcashSigner {
        val access = mockk<LedgerAppAccess> { coEvery { signWithLedgerApp<Any>(any(), any(), any()) } throws failure }
        return LedgerZcashSigner(LedgerSigningKey(access, account, stored))
    }

    private fun view(version: Int, orchard: ExternalActionBundle?, ironwood: ExternalActionBundle? = null) =
        ExternalSigningView(
            txVersion = version,
            versionGroupId = 0x26A7270AL,
            consensusBranchId = 0xC8E71055L,
            fallbackLockTime = null,
            expiryHeight = EXPIRY,
            coinType = COIN_TYPE,
            txModifiable = 0,
            transparentInputs = listOf(
                ExternalTransparentInput(TXID, PREVOUT_INDEX, null, 50_000, SCRIPT, SIGHASH_ALL, derivation(0, 3)),
            ),
            transparentOutputs = listOf(
                ExternalTransparentOutput(60_000, SCRIPT, null),
                ExternalTransparentOutput(30_000, SCRIPT, derivation(1, 0)),
            ),
            orchard = orchard,
            ironwood = ironwood,
        )

    private fun derivation(chain: Long, index: Long) =
        ExternalKeyDerivation(PUBKEY, FIELD, listOf(HARDENED_LONG + 44, HARDENED_LONG + 133, HARDENED_LONG, chain, index))

    private fun bundle(valueBalance: Long = 0) = ExternalActionBundle(
        actions = listOf(action()),
        flags = 3,
        valueBalance = valueBalance,
        anchor = FIELD,
        notePlaintextVersion = 2,
        signIndices = listOf(0),
    )

    private fun action() = ExternalAction(
        cvNet = FIELD, nullifier = FIELD, rk = FIELD, spendRecipient = RECIPIENT, spendValue = 100_000,
        spendRho = FIELD, spendRseed = FIELD, alpha = FIELD, seedFingerprint = FIELD,
        path = listOf(HARDENED_LONG + 32, HARDENED_LONG + 133, HARDENED_LONG),
        cmx = FIELD, ephemeralKey = FIELD, encCiphertext = "ab".repeat(580), outCiphertext = "cd".repeat(80),
        recipient = RECIPIENT, recipientAddress = ZcashFixtures.DEVICE_ORCHARD_ADDRESS, value = 60_000,
        rseed = FIELD, rcv = FIELD,
    )

    private companion object {
        const val V5 = 5
        const val V6 = 6
        const val EXPIRY = 3_500_040
        const val COIN_TYPE = 133
        const val PREVOUT_INDEX = 1
        const val SIGHASH_ALL = 1
        const val SIGN_TRANSPARENT = 0x55
        const val SIGN_ORCHARD = 0x57
        const val NOT_ENOUGH_MEMORY = 0x6A84
        const val HARDENED = 0x8000_0000u
        const val HARDENED_LONG = 0x8000_0000L
        const val TXID = "1111111111111111111111111111111111111111111111111111111111111111"
        const val FIELD = "2222222222222222222222222222222222222222222222222222222222222222"
        const val PUBKEY = ZcashFixtures.ACCOUNT_KEY_COMPRESSED
        const val SCRIPT = "76a914000000000000000000000000000000000000000088ac"
        val RECIPIENT = "33".repeat(43)
        val PLANNED = PreparedTransaction(byteArrayOf(1))
        val PROVED = PreparedTransaction(byteArrayOf(2))
        val SIGNED = PreparedTransaction(byteArrayOf(3))
        val ORCHARD_SIGNATURE = ByteArray(64) { 7 }

        // A strict DER signature with small r and s, then SIGHASH_ALL, as the app returns it.
        val DER_SIGNATURE = ("3006020101020101" + "01").hexToByteArray()
    }
}
