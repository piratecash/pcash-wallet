package cash.p.terminal.ledger.zcash

import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.zcash.ZcashAction
import cash.p.terminal.ledgerkit.zcash.ZcashActionBundle
import cash.p.terminal.ledgerkit.zcash.ZcashAppClient
import cash.p.terminal.ledgerkit.zcash.ZcashKeyOrigin
import cash.p.terminal.ledgerkit.zcash.ZcashTransaction
import cash.p.terminal.ledgerkit.zcash.ZcashTransparentInput
import cash.p.terminal.ledgerkit.zcash.ZcashTransparentOutput
import cash.p.zcash.ExternalAction
import cash.p.zcash.ExternalActionBundle
import cash.p.zcash.ExternalKeyDerivation
import cash.p.zcash.ExternalSignatures
import cash.p.zcash.ExternalSigningView
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.ZcashWallet

/** Signs a PCZT planned for an external signer on the Zcash app, which shows its outputs; proofs need no key. */
class LedgerZcashSigner(private val key: LedgerSigningKey) {

    suspend fun sign(wallet: ZcashWallet, transaction: PreparedTransaction): PreparedTransaction {
        val proved = wallet.proveForExternalSigner(transaction)
        val request = wallet.externalSigningView(proved, LEDGER_ZCASH_ACCOUNT).toLedger()
        val signatures = key.sign { transport, _ -> ZcashAppClient(transport).signTransaction(request) }
        return wallet.applyExternalSignatures(
            proved,
            ExternalSignatures(signatures.orchard, signatures.ironwood, signatures.transparent),
        )
    }
}

internal fun ExternalSigningView.toLedger() = ZcashTransaction(
    txVersion = txVersion,
    versionGroupId = versionGroupId,
    consensusBranchId = consensusBranchId,
    fallbackLockTime = fallbackLockTime,
    expiryHeight = expiryHeight.toLong(),
    coinType = coinType.toLong(),
    txModifiable = txModifiable,
    transparentInputs = transparentInputs.map {
        ZcashTransparentInput(
            prevoutTxid = it.prevoutTxid.hexToByteArray(),
            prevoutIndex = it.prevoutIndex.toLong(),
            sequence = it.sequence,
            value = it.value,
            scriptPubkey = it.scriptPubkey.hexToByteArray(),
            sighashType = it.sighashType,
            derivation = it.derivation.toLedger(),
        )
    },
    transparentOutputs = transparentOutputs.map {
        ZcashTransparentOutput(it.value, it.scriptPubkey.hexToByteArray(), it.derivation?.toLedger())
    },
    orchard = orchard?.toLedger(),
    ironwood = ironwood?.toLedger(),
)

private fun ExternalKeyDerivation.toLedger() =
    ZcashKeyOrigin(pubkey.hexToByteArray(), seedFingerprint.hexToByteArray(), path.toDerivationPath())

// The app tells Orchard and Ironwood notes apart by the bundle it receives them in, not by a version field.
private fun ExternalActionBundle.toLedger() = ZcashActionBundle(
    actions = actions.map { it.toLedger() },
    flags = flags,
    valueBalance = valueBalance,
    anchor = anchor.hexToByteArray(),
    signIndices = signIndices,
)

private fun ExternalAction.toLedger() = ZcashAction(
    cvNet = cvNet.hexToByteArray(),
    nullifier = nullifier.hexToByteArray(),
    rk = rk.hexToByteArray(),
    spendRecipient = spendRecipient.hexToByteArray(),
    spendValue = spendValue,
    spendRho = spendRho.hexToByteArray(),
    spendRseed = spendRseed.hexToByteArray(),
    alpha = alpha.hexToByteArray(),
    seedFingerprint = seedFingerprint.hexToByteArray(),
    path = path.toDerivationPath(),
    cmx = cmx.hexToByteArray(),
    ephemeralKey = ephemeralKey.hexToByteArray(),
    encCiphertext = encCiphertext.hexToByteArray(),
    outCiphertext = outCiphertext.hexToByteArray(),
    recipient = recipient.hexToByteArray(),
    value = value,
    rseed = rseed.hexToByteArray(),
    rcv = rcv.hexToByteArray(),
)

// The SDK writes each index with its hardened bit set, as the device reads it.
private fun List<Long>.toDerivationPath() = DerivationPath(map { it.toUInt() })
