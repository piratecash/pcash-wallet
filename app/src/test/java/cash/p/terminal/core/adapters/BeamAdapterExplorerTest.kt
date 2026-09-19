package cash.p.terminal.core.adapters

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.TransactionExplorerData
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class BeamAdapterExplorerTest {
    private val sdk = mockk<BeamWalletSession> {
        every { state } returns MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
        every { balance } returns MutableStateFlow(BeamBalance())
        every { transactions } returns MutableStateFlow(emptyList())
    }
    private val session = mockk<BeamSessionOwner.Session> {
        every { accountId } returns "beam-account"
        every { wallet } returns sdk
        every { receiveAddress } returns
            BeamAddress("public-offline-test", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
    }
    private val owner = mockk<BeamSessionOwner>(relaxed = true) {
        every { current } returns session
    }

    @Test
    fun getTransactionExplorerData_registeredTransaction_linksToTheKernelOnTheMainnetExplorer() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(dispatcher)

        assertEquals("explorer.beam.mw", adapter.explorerTitle)
        assertEquals(
            listOf(
                TransactionExplorerData(
                    "explorer.beam.mw",
                    "https://explorer.beam.mw/block?kernel_id=$KERNEL_ID",
                )
            ),
            adapter.getTransactionExplorerData(beamRecord(kernelId = KERNEL_ID)),
        )
    }

    @Test
    fun getTransactionExplorerData_testnetWallet_neverLinksToTheMainnetExplorer() = runTest {
        every { session.receiveAddress } returns
            BeamAddress("public-offline-test", BeamAddressType.PublicOffline, BeamNetwork.Testnet)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(dispatcher)

        assertEquals("testnet.explorer.beam.mw", adapter.explorerTitle)
        assertEquals(
            listOf(
                TransactionExplorerData(
                    "testnet.explorer.beam.mw",
                    "https://testnet.explorer.beam.mw/block?kernel_id=$KERNEL_ID",
                )
            ),
            adapter.getTransactionExplorerData(beamRecord(kernelId = KERNEL_ID)),
        )
    }

    @Test
    fun getTransactionExplorerData_withoutKernelId_offersNoButton() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(dispatcher)

        // Not yet registered on chain, and the degenerate blank the DB can also hold.
        assertEquals(
            emptyList<TransactionExplorerData>(),
            adapter.getTransactionExplorerData(beamRecord(kernelId = null)),
        )
        assertEquals(
            emptyList<TransactionExplorerData>(),
            adapter.getTransactionExplorerData(beamRecord(kernelId = "   ")),
        )
    }

    @Test
    fun getTransactionExplorerData_kernelIdWithoutProof_offersNoButton() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(dispatcher)

        // An outgoing transaction is given its kernel id at signing, long before the kernel reaches
        // a block, so a kernel id alone would produce a link the explorer cannot resolve.
        assertEquals(
            emptyList<TransactionExplorerData>(),
            adapter.getTransactionExplorerData(
                beamRecord(kernelId = KERNEL_ID, proofHeight = null, status = BeamTransactionStatus.Registering)
            ),
        )
        assertEquals(
            emptyList<TransactionExplorerData>(),
            adapter.getTransactionExplorerData(
                beamRecord(kernelId = KERNEL_ID, proofHeight = null, status = BeamTransactionStatus.Failed)
            ),
        )
    }

    @Test
    fun getTransactionExplorerData_foreignRecord_offersNoButton() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(dispatcher)

        // The base implementation would have produced a link from the wallet-local hash.
        assertEquals(
            emptyList<TransactionExplorerData>(),
            adapter.getTransactionExplorerData(mockk<TransactionRecord>(relaxed = true)),
        )
    }

    @Test
    fun getTransactionExplorerData_offlineSignedItem_linksToItsKernel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(dispatcher)

        val link = TransactionExplorerData("explorer.beam.mw", "https://explorer.beam.mw/block?kernel_id=$KERNEL_ID")
        assertEquals(listOf(link), adapter.getTransactionExplorerData(offlineRecord(KERNEL_ID)))
        // A wallet-local id is not a kernel id, so it gets no link.
        assertEquals(
            emptyList<TransactionExplorerData>(),
            adapter.getTransactionExplorerData(offlineRecord("beam-tx-1")),
        )
    }

    @Test
    fun getTransactionUrl_walletLocalId_staysEmpty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertEquals("", adapter(dispatcher).getTransactionUrl("beam-tx-1"))
    }

    // blockHeight mirrors what BeamTransactionRecordConverter derives from proofHeight.
    private fun beamRecord(
        kernelId: String?,
        proofHeight: Long? = 100,
        status: BeamTransactionStatus = BeamTransactionStatus.Completed,
    ) = BeamTransactionRecord(
        transaction = BeamTransaction(
            id = "beam-tx-1",
            direction = BeamTransactionDirection.Incoming,
            amount = 1,
            fee = 0,
            createdAtEpochSeconds = 1_700_000_000,
            minHeight = null,
            proofHeight = proofHeight,
            kernelId = kernelId,
            status = status,
            failureReason = null,
        ),
        source = mockk(relaxed = true),
        token = mockk(relaxed = true),
        uid = "beam-tx-1",
        blockHeight = proofHeight?.toInt(),
        amount = BigDecimal.ONE,
        fee = BigDecimal.ZERO,
    )

    private fun offlineRecord(hash: String) = PendingTransactionRecord(
        uid = "offline-signed:$hash",
        transactionHash = hash,
        timestamp = 1_700_000_000,
        source = mockk(relaxed = true),
        token = mockk(relaxed = true),
        amount = BigDecimal.ONE,
        toAddress = "",
        fromAddress = "",
        expiresAt = Long.MAX_VALUE,
        memo = null,
    )

    private fun adapter(dispatcher: CoroutineDispatcher) = testBeamAdapter(owner, session, dispatcher)

    private companion object {
        const val KERNEL_ID = "8a2b7c1d9e0f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b"
    }
}
