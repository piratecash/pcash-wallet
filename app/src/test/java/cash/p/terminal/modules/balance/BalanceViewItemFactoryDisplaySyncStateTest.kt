package cash.p.terminal.modules.balance

import cash.p.terminal.R
import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.zcashMnemonicAccount
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.zcashTransparentWallet
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

class BalanceViewItemFactoryDisplaySyncStateTest {

    private val numberFormatter = mockk<IAppNumberFormatter>()
    private val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    private val factory = BalanceViewItemFactory(offlineModeManager)
    private val currency = Currency("USD", "$", 2, 0)
    private val wallet = WalletFactory.previewWallet()

    @Before
    fun setUp() {
        stopKoin()
        every { numberFormatter.formatCoinFull(any(), any(), any()) } returns "1"
        every { numberFormatter.format(any(), any(), any(), any(), any()) } answers {
            firstArg<Number>().toInt().toString()
        }

        startKoin {
            modules(
                module {
                    single { numberFormatter }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun viewItem_balanceSyncedTxNotSynced_displaysTxErrorWithoutBlockingActions() {
        val viewItem = factory.viewItem(
            item = balanceItem(transactionsSyncState = AdapterState.NotSynced(historyError)),
            currency = currency,
            hideBalance = false,
            watchAccount = false,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = true,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
        )

        assertTrue(viewItem.failedIconVisible)
        assertEquals(historyError.message, viewItem.errorMessage)
        assertEquals(OperationAvailability.Available, viewItem.sendAvailability)
        assertEquals(OperationAvailability.Available, viewItem.swapAvailability)
        assertFalse(viewItem.primaryValue.dimmed)
    }

    @Test
    fun viewItem2_balanceSyncedTxNotSynced_displaysTxErrorWithoutBlockingActions() {
        val viewItem = factory.viewItem2(
            item = balanceItem(transactionsSyncState = AdapterState.NotSynced(historyError)),
            currency = currency,
            roundingAmount = false,
            hideBalance = false,
            watchAccount = false,
            isSwipeToDeleteEnabled = true,
            balanceViewType = BalanceViewType.CoinThenFiat,
            networkAvailable = true,
            showStackingUnpaid = false,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
        )

        assertTrue(viewItem.failedIconVisible)
        assertEquals(historyError.message, viewItem.errorMessage)
        assertEquals(OperationAvailability.Available, viewItem.sendAvailability)
        assertEquals(OperationAvailability.Available, viewItem.swapAvailability)
        assertFalse(viewItem.primaryValue.dimmed)
    }

    @Test
    fun viewItem_networkPaused_returnsOfflineStateWithoutSyncIndicators() {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns true

        val viewItem = factory.viewItem(
            item = balanceItem(transactionsSyncState = AdapterState.NotSynced(historyError)),
            currency = currency,
            hideBalance = false,
            watchAccount = false,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = true,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
        )

        assertTrue(viewItem.offline)
        assertEquals(null, viewItem.syncingTextValue)
        assertFalse(viewItem.failedIconVisible)
    }

    @Test
    fun viewItem_networkNotPaused_keepsPreviousSyncBehavior() {
        every { offlineModeManager.isNetworkPaused(any<OfflineKey>()) } returns false

        val viewItem = factory.viewItem(
            item = balanceItem(transactionsSyncState = AdapterState.NotSynced(historyError)),
            currency = currency,
            hideBalance = false,
            watchAccount = false,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = true,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
        )

        assertFalse(viewItem.offline)
        assertTrue(viewItem.failedIconVisible)
        assertEquals(historyError.message, viewItem.errorMessage)
    }

    @Test
    fun viewItem_transparentZcashPendingOnly_hidesShieldFunds() {
        val viewItem = factory.viewItem(
            item = balanceItem(
                wallet = zcashTransparentWallet(),
                balanceData = BalanceData(
                    available = BigDecimal.ZERO,
                    pending = ZcashAdapter.MINERS_FEE + BigDecimal.ONE
                )
            ),
            currency = currency,
            hideBalance = false,
            watchAccount = false,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = true,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
        )

        assertFalse(viewItem.isShowShieldFunds)
    }

    @Test
    fun viewItem_transparentZcashAvailableAboveFee_showsShieldFunds() {
        val viewItem = factory.viewItem(
            item = balanceItem(
                wallet = zcashTransparentWallet(),
                balanceData = BalanceData(
                    available = ZcashAdapter.MINERS_FEE + BigDecimal.ONE,
                    pending = BigDecimal.ZERO
                )
            ),
            currency = currency,
            hideBalance = false,
            watchAccount = false,
            balanceViewType = BalanceViewType.CoinThenFiat,
            isSwappable = true,
            displayDiffOptionType = DisplayDiffOptionType.BOTH,
        )

        assertTrue(viewItem.isShowShieldFunds)
    }

    @Test
    fun viewItem_snapshotRestoreStages_displayLocalizedStage() {
        val cases = mapOf(
            AdapterState.SnapshotRestoreStage.ResolvingBirthday to R.string.beam_restore_resolving_birthday,
            AdapterState.SnapshotRestoreStage.DownloadingSnapshot to R.string.beam_restore_downloading_snapshot,
            AdapterState.SnapshotRestoreStage.ValidatingSnapshot to R.string.beam_restore_validating_snapshot,
            AdapterState.SnapshotRestoreStage.CountingShieldedOutputs to
                R.string.beam_restore_counting_shielded_outputs,
            AdapterState.SnapshotRestoreStage.ScanningWalletOutputs to R.string.beam_restore_scanning_wallet_outputs,
            AdapterState.SnapshotRestoreStage.ImportingSnapshot to R.string.beam_restore_importing_snapshot,
            AdapterState.SnapshotRestoreStage.CatchingUp to R.string.beam_restore_catching_up,
        )

        cases.forEach { (stage, textResource) ->
            val viewItem = viewItem(
                AdapterState.Syncing(substatus = AdapterState.Substatus.SnapshotRestore(stage))
            )

            assertEquals(Translator.getString(textResource), viewItem.syncingTextValue)
            assertEquals(SyncingProgressType.Spinner, viewItem.syncingProgress.type)
        }
    }

    @Test
    fun viewItem_snapshotDownloadKnownTotal_displaysMiBAndProgressRing() {
        val viewItem = viewItem(
            AdapterState.Syncing(
                progress = 50.0,
                substatus = AdapterState.Substatus.SnapshotRestore(
                    stage = AdapterState.SnapshotRestoreStage.DownloadingSnapshot,
                    downloadedBytes = MEBIBYTE,
                    totalBytes = 2 * MEBIBYTE,
                ),
            )
        )

        assertEquals(SyncingProgress(SyncingProgressType.ProgressWithRing, 50.0), viewItem.syncingProgress)
        assertEquals(
            Translator.getString(R.string.beam_restore_downloading_snapshot_progress, "1", "2"),
            viewItem.syncingTextValue,
        )
    }

    @Test
    fun viewItem_snapshotDownloadUnknownTotal_displaysDownloadedMiBAndSpinner() {
        val viewItem = viewItem(
            AdapterState.Syncing(
                substatus = AdapterState.Substatus.SnapshotRestore(
                    stage = AdapterState.SnapshotRestoreStage.DownloadingSnapshot,
                    downloadedBytes = MEBIBYTE,
                ),
            )
        )

        assertEquals(SyncingProgressType.Spinner, viewItem.syncingProgress.type)
        assertEquals(
            Translator.getString(R.string.beam_restore_downloading_snapshot_downloaded, "1"),
            viewItem.syncingTextValue,
        )
    }

    @Test
    fun viewItem_catchingUpKnownProgress_displaysStagePercentAndProgressRing() {
        val viewItem = viewItem(
            AdapterState.Syncing(
                progress = 100.0,
                substatus = AdapterState.Substatus.SnapshotRestore(
                    AdapterState.SnapshotRestoreStage.CatchingUp,
                ),
            )
        )

        assertEquals(SyncingProgress(SyncingProgressType.ProgressWithRing, 100.0), viewItem.syncingProgress)
        assertEquals(
            Translator.getString(R.string.beam_restore_catching_up_progress, "100"),
            viewItem.syncingTextValue,
        )
        assertTrue(viewItem.primaryValue.dimmed)
    }

    @Test
    fun viewItem_scanningKnownProgress_keepsStageSpinner() {
        val viewItem = viewItem(
            AdapterState.Syncing(
                progress = 25.0,
                substatus = AdapterState.Substatus.SnapshotRestore(
                    AdapterState.SnapshotRestoreStage.ScanningWalletOutputs,
                ),
            )
        )

        assertEquals(SyncingProgressType.Spinner, viewItem.syncingProgress.type)
        assertEquals(
            Translator.getString(R.string.beam_restore_scanning_wallet_outputs),
            viewItem.syncingTextValue,
        )
    }

    // R13: one added entry in each of two shared allow-lists. BEAM must gain the progress ring
    // and the 10% starting value; every other chain's classification must be exactly as before.
    @Test
    fun syncingProgress_beamGainsTheProgressRingWhileOtherChainsAreUnchanged() {
        val beamRing = viewItem(AdapterState.Syncing(progress = 42.0), walletFor(BlockchainType.Beam))
        assertEquals(SyncingProgress(SyncingProgressType.ProgressWithRing, 42.0), beamRing.syncingProgress)

        // Before any real progress arrives BEAM starts where Bitcoin and Zcash start, not at an
        // empty ring: the default and the allow-list entry only make sense together.
        val beamDefault = viewItem(AdapterState.Syncing(progress = null), walletFor(BlockchainType.Beam))
        assertEquals(SyncingProgress(SyncingProgressType.Spinner, 10.0), beamDefault.syncingProgress)

        val bitcoin = viewItem(AdapterState.Syncing(progress = 42.0), walletFor(BlockchainType.Bitcoin))
        assertEquals(SyncingProgress(SyncingProgressType.ProgressWithRing, 42.0), bitcoin.syncingProgress)

        // Ethereum is not in the allow-list and must stay a spinner at its own 50% default.
        val ethereum = viewItem(AdapterState.Syncing(progress = 42.0), walletFor(BlockchainType.Ethereum))
        assertEquals(SyncingProgress(SyncingProgressType.Spinner, 42.0), ethereum.syncingProgress)
        val ethereumDefault = viewItem(AdapterState.Syncing(progress = null), walletFor(BlockchainType.Ethereum))
        assertEquals(SyncingProgress(SyncingProgressType.Spinner, 50.0), ethereumDefault.syncingProgress)
    }

    @Test
    fun lockedBalance_beamExplainsTheMaxPrivacyLockWhileOtherChainsKeepTheTimeLockText() {
        val locked = BalanceData(available = BigDecimal.ONE, timeLocked = BigDecimal.TEN)

        val beam = lockedValue(walletFor(BlockchainType.Beam), locked)
        assertEquals(R.string.Balance_LockedAmount_Title, (beam.infoTitle as TranslatableString.ResString).id)
        assertEquals(R.string.beam_locked_balance_info, (beam.info as TranslatableString.ResString).id)

        val bitcoin = lockedValue(walletFor(BlockchainType.Bitcoin), locked)
        assertEquals(R.string.Info_LockTime_Title, (bitcoin.infoTitle as TranslatableString.ResString).id)
        assertEquals(R.string.Info_ProcessingBalance_Description, (bitcoin.info as TranslatableString.ResString).id)
    }

    private fun lockedValue(wallet: Wallet, balanceData: BalanceData) = factory.viewItem(
        item = balanceItem(wallet = wallet, balanceData = balanceData),
        currency = currency,
        hideBalance = false,
        watchAccount = false,
        balanceViewType = BalanceViewType.CoinThenFiat,
        isSwappable = true,
        displayDiffOptionType = DisplayDiffOptionType.BOTH,
    ).lockedValues.single()

    private fun walletFor(blockchainType: BlockchainType): Wallet {
        val token = Token(
            coin = Coin(uid = "coin", name = "Coin", code = "COIN"),
            blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
            type = TokenType.Native,
            decimals = 8,
        )
        return checkNotNull(WalletFactory(mockk(relaxed = true)).create(token, zcashMnemonicAccount(), null))
    }

    private fun viewItem(state: AdapterState, wallet: Wallet) = factory.viewItem(
        item = balanceItem(state = state, wallet = wallet),
        currency = currency,
        hideBalance = false,
        watchAccount = false,
        balanceViewType = BalanceViewType.CoinThenFiat,
        isSwappable = true,
        displayDiffOptionType = DisplayDiffOptionType.BOTH,
    )

    private fun viewItem(state: AdapterState) = factory.viewItem(
        item = balanceItem(state = state),
        currency = currency,
        hideBalance = false,
        watchAccount = false,
        balanceViewType = BalanceViewType.CoinThenFiat,
        isSwappable = true,
        displayDiffOptionType = DisplayDiffOptionType.BOTH,
    )

    private fun balanceItem(
        state: AdapterState = AdapterState.Synced,
        transactionsSyncState: AdapterState = state,
        wallet: Wallet = this.wallet,
        balanceData: BalanceData = BalanceData(available = BigDecimal.ONE),
    ) = BalanceItem(
        wallet = wallet,
        balanceData = balanceData,
        state = state,
        sendAllowed = true,
        coinPrice = null,
        transactionsSyncState = transactionsSyncState,
    )

    private companion object {
        const val MEBIBYTE = 1024L * 1024
        val historyError = Exception("history endpoint down")
    }
}
