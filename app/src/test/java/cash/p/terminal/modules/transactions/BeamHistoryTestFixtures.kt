package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.mockk
import java.math.BigDecimal

internal fun beamHistoryRecord(
    direction: BeamTransactionDirection = BeamTransactionDirection.Outgoing,
    status: BeamTransactionStatus = BeamTransactionStatus.Completed,
    kernelId: String? = "kernel-id",
    failureReason: String? = null,
    counterparty: String? = null,
): BeamTransactionRecord {
    val blockchain = Blockchain(BlockchainType.Beam, "Beam", null)
    val token = Token(Coin("beam", "Beam", "BEAM"), blockchain, TokenType.Native, 8)
    val transaction = BeamTransaction(
        id = "transaction-id", direction = direction, amount = 100_000_000, fee = 100,
        createdAtEpochSeconds = 1_000, minHeight = null, proofHeight = 100,
        kernelId = kernelId, status = status, failureReason = failureReason,
        counterparty = counterparty,
    )
    return BeamTransactionRecord(
        transaction = transaction,
        source = TransactionSource(blockchain, mockk(relaxed = true), null),
        token = token,
        uid = "beam:transaction-id",
        blockHeight = 100,
        amount = if (direction == BeamTransactionDirection.Incoming) BigDecimal.ONE else -BigDecimal.ONE,
        fee = BigDecimal("0.000001"),
    )
}
