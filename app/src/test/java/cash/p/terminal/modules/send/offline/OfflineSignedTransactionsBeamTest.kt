package cash.p.terminal.modules.send.offline

import cash.p.beam.BeamOfflineSendState
import cash.p.beam.BeamSendDeliveryMode
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendResolution
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.modules.send.beam.BeamOfflineOperations
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// BEAM rows of "Signed offline": own exports come from the SDK inventory, relayed imports from Room.
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSignedTransactionsBeamTest : OfflineSignedTransactionsTestBase() {

    @Test
    fun init_beamOperationsMixedStates_onlyExportedListed() = runTest(dispatcher) {
        val beamWallet = wallet(beamToken)
        val exported = beamOperation(operationId = "op-exported", state = BeamOfflineSendState.Exported)
        val signed = beamOperation(operationId = "op-signed", state = BeamOfflineSendState.Signed)
        val signing = beamOperation(operationId = "op-signing", state = BeamOfflineSendState.Signing)
        setupState(entities = emptyList(), wallets = listOf(beamWallet))
        val beamOps = mockk<BeamOfflineOperations> {
            every { observe(account.id) } returns
                flowOf(BeamOfflineOperations.Inventory(listOf(exported, signed, signing)))
        }

        val viewModel = viewModel(this, beamOps)
        advanceUntilIdle()

        assertEquals(listOf("beam-offline:op-exported"), viewModel.uiState.items.map { it.uid })
    }

    @Test
    fun init_beamExportedOperationWithCreationTime_timestampFromOperation() = runTest(dispatcher) {
        val beamWallet = wallet(beamToken)
        val operation = beamOperation().copy(createdAtEpochSeconds = 555_000L)
        setupState(entities = emptyList(), wallets = listOf(beamWallet))
        val beamOps = mockk<BeamOfflineOperations> {
            every { observe(account.id) } returns flowOf(BeamOfflineOperations.Inventory(listOf(operation)))
        }

        val viewModel = viewModel(this, beamOps)
        advanceUntilIdle()

        assertEquals(555_000L, viewModel.uiState.items.single().transactionItem.record.timestamp)
    }

    @Test
    fun init_beamExportedNoCreationTime_fallbackTimestampNonZeroAndStable() = runTest(dispatcher) {
        val beamWallet = wallet(beamToken)
        val operation = beamOperation()
        every { accountManager.activeAccountStateFlow } returns MutableStateFlow(
            ActiveAccountState.ActiveAccount(account)
        )
        every { walletManager.activeWalletsFlow } returns MutableStateFlow(listOf(beamWallet))
        every { repository.observe(account.id) } returns MutableStateFlow(emptyList())
        val beamFlow = MutableStateFlow(BeamOfflineOperations.Inventory(listOf(operation)))
        val beamOps = mockk<BeamOfflineOperations> { every { observe(account.id) } returns beamFlow }

        val viewModel = viewModel(this, beamOps)
        advanceUntilIdle()
        val firstTimestamp = viewModel.uiState.items.single().transactionItem.record.timestamp
        assertEquals(true, firstTimestamp > 0)

        beamFlow.value = BeamOfflineOperations.Inventory(listOf(operation.copy(observedProofHeight = 5)))
        advanceUntilIdle()
        val secondTimestamp = viewModel.uiState.items.single().transactionItem.record.timestamp

        assertEquals(firstTimestamp, secondTimestamp)
    }

    @Test
    fun init_beamInventoryEmpty_noBeamRows() = runTest(dispatcher) {
        val beamWallet = wallet(beamToken)
        setupState(entities = emptyList(), wallets = listOf(beamWallet))
        val beamOps = mockk<BeamOfflineOperations> {
            every { observe(account.id) } returns flowOf(BeamOfflineOperations.Inventory(emptyList()))
        }

        val viewModel = viewModel(this, beamOps)
        advanceUntilIdle()

        assertEquals(emptyList<OfflineSignedTransactionViewItem>(), viewModel.uiState.items)
    }

    @Test
    fun init_relayedBeamImport_listedUnlessItIsOwnExport() = runTest(dispatcher) {
        val beamWallet = wallet(beamToken)
        val own = beamOperation(operationId = "op-own")
        val foreign = beamImportEntity(txHash = "kernel-foreign")
        val ownRelayed = beamImportEntity(txHash = "kernel-op-own")
        setupState(entities = listOf(foreign, ownRelayed), wallets = listOf(beamWallet))
        val beamOps = mockk<BeamOfflineOperations> {
            every { observe(account.id) } returns flowOf(BeamOfflineOperations.Inventory(listOf(own)))
        }

        val viewModel = viewModel(this, beamOps)
        advanceUntilIdle()

        assertEquals(
            listOf("offline-signed:kernel-foreign", "beam-offline:op-own"),
            viewModel.uiState.items.map { it.uid },
        )
        val relayed = viewModel.uiState.items.first()
        assertEquals("BEAM", relayed.transactionItem.record.token.coin.code)
        assertEquals("1.5", relayed.transactionItem.record.mainValue?.decimalValue?.abs()?.toPlainString())
        assertStatusColor(relayed, ColorName.Remus)
    }

    private fun beamImportEntity(txHash: String) = OfflineSignedTransactionEntity(
        accountId = account.id,
        txHash = txHash,
        blockchainTypeUid = "beam",
        tokenQueryId = "beam|native",
        sourceTokenQueryId = "beam|native",
        coinUid = "beam",
        coinCode = "BEAM",
        coinName = "Beam",
        tokenDecimals = 8,
        amount = "1.5",
        feeTokenQueryId = "beam|native",
        feeAtomic = "1000",
        solanaBlockHash = null,
        solanaLastValidBlockHeight = null,
        tonValidUntil = null,
        tonSenderAddress = null,
        tonSeqno = null,
        tronExpiration = null,
        stellarSourceAccountId = null,
        stellarSequenceNumber = null,
        stellarValidUntil = null,
        toAddress = "",
        rawHex = "00",
        pcashPayload = "pcash:tx:v1:beam:body",
        createdAt = 1_700_000_000_000L,
        status = OfflineSignedTransactionStatus.Broadcasted.value,
        broadcastAttempts = 1,
        lastBroadcastAt = null,
        broadcastedAt = null,
        lastError = null,
    )

    private fun beamOperation(
        operationId: String = "op-1",
        transactionId: String = "tx-1",
        state: BeamOfflineSendState = BeamOfflineSendState.Exported,
    ) = BeamSendOperation(
        operationId = operationId, transactionId = transactionId, requestHash = "hash",
        amount = 100_000_000, fee = 1_000, resolution = BeamSendResolution.Prepared(transactionId),
        deliveryMode = BeamSendDeliveryMode.Offline, offlineState = state,
        contextId = "context", rules = "rules", mainKernelId = "kernel-$operationId",
        observedProofHeight = 0,
    )

    private val beamToken = Token(
        coin = Coin(uid = "beam", name = "Beam", code = "BEAM"),
        blockchain = Blockchain(BlockchainType.Beam, "Beam", null),
        type = TokenType.Native,
        decimals = 8,
    )
}
