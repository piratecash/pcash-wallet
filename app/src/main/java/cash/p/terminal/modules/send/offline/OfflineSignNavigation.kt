package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.rememberExistingViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.reflect.KClass

private const val RetryProgressMinVisibleMillis = 1200L

internal data class OfflineSignRouteState(
    val confirmationData: SendConfirmationData,
    val blockchainName: String,
    val coinMaxAllowedDecimals: Int,
    val feeCoinMaxAllowedDecimals: Int,
    val rate: CurrencyValue?,
    val signState: OfflineSignState,
)

internal interface OfflineSignCapableViewModel {
    val wallet: Wallet
    val coinMaxAllowedDecimals: Int
    val feeCoinMaxAllowedDecimals: Int
    val coinRate: CurrencyValue?
    val offlineSigningController: OfflineSigningController<*>

    // Inputs the shared confirmation gate needs. isSynced/hasAdapterError/syncRetrying/
    // retryAdapterSync are provided by BaseSendViewModel; offlineSignSupported and
    // sendResult are concrete properties on each send view model.
    val offlineSignSupported: Boolean

    /**
     * Send progress, which [shouldShowOfflineSyncBlocker] reads to keep the confirmation screen
     * in place once a send is in flight.
     *
     * Implementations MUST publish [SendResult.Sending] synchronously in their click handler,
     * before launching the send coroutine. Setting it inside the coroutine leaves a window in
     * which the send is already under way while this still reads null — losing connectivity in
     * that window shows the blocker and its offline-sign button, which can broadcast the same
     * transaction twice.
     */
    val sendResult: SendResult?
    val isSynced: Boolean
    val hasAdapterError: Boolean
    val syncRetrying: Boolean
    val isEffectivelySynced: Boolean
    fun retryAdapterSync()

    val offlineSignState: OfflineSignState
        get() = offlineSigningController.signState
    val offlineSignedTransaction: OfflineSignedTransaction?
        get() = offlineSigningController.signedTransaction

    fun getConfirmationData(): SendConfirmationData
    fun onClickSignOffline(format: OfflineTransactionFormat)

    fun resetOfflineSignState() = offlineSigningController.resetSignState()
    fun onOfflineTransferClosed() = offlineSigningController.closeTransfer()
}

private fun OfflineSignCapableViewModel.offlineSignRouteState(): OfflineSignRouteState? =
    tryOrNull { getConfirmationData() }?.let { confirmationData ->
        OfflineSignRouteState(
            confirmationData = confirmationData,
            blockchainName = wallet.token.blockchain.name,
            coinMaxAllowedDecimals = coinMaxAllowedDecimals,
            feeCoinMaxAllowedDecimals = feeCoinMaxAllowedDecimals,
            rate = coinRate,
            signState = offlineSignState,
        )
    }

class OfflineSignPage(val sendViewModel: KClass<out ViewModel>) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.rememberOfflineSignViewModel(sendViewModel) ?: return
        val state = viewModel.offlineSignRouteState()
        if (state == null) {
            LaunchedEffect(Unit) {
                navigation.navigateUpFrom(this@OfflineSignPage)
            }
            return
        }

        val onLeave: () -> Unit = {
            viewModel.resetOfflineSignState()
            navigation.navigateUpSafely()
        }
        OfflineSignScreen(
            confirmationData = state.confirmationData,
            blockchainName = state.blockchainName,
            coinMaxAllowedDecimals = state.coinMaxAllowedDecimals,
            feeCoinMaxAllowedDecimals = state.feeCoinMaxAllowedDecimals,
            rate = state.rate,
            signState = state.signState,
            callbacks = OfflineSignCallbacks(
                onBackClick = onLeave,
                onCancelClick = onLeave,
                onSignClick = viewModel::onClickSignOffline,
                onSignStateConsumed = viewModel::resetOfflineSignState,
                onSigned = { format ->
                    navigation.slideFromRight(OfflineTransactionTransferPage(sendViewModel, format))
                },
            ),
        )
    }
}

class OfflineTransactionTransferPage(
    val sendViewModel: KClass<out ViewModel>,
    val format: OfflineTransactionFormat,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.rememberOfflineSignViewModel(sendViewModel) ?: return
        val qrCodeSaver: OfflineQrCodeSaver = koinInject()
        OfflineTransactionTransferScreen(
            transaction = viewModel.offlineSignedTransaction,
            selectedFormat = format,
            qrCodeSaver = qrCodeSaver,
            onBackClick = navigation::navigateUpSafely,
            onDoneClick = {
                viewModel.onOfflineTransferClosed()
                navigation.removeLastUntil(SendPage::class, true)
            },
        )
    }
}

@Composable
private fun HSNavigation.rememberOfflineSignViewModel(
    sendViewModel: KClass<out ViewModel>
): OfflineSignCapableViewModel? =
    rememberExistingViewModel(SendPage::class, sendViewModel) as? OfflineSignCapableViewModel

@Composable
internal fun OfflineSyncRetryProgressEffect(
    retrying: Boolean,
    isConnected: Boolean,
    isSynced: Boolean,
    hasAdapterError: Boolean,
    onRetryFinish: () -> Unit,
) {
    val currentOnRetryFinish = rememberUpdatedState(onRetryFinish)
    LaunchedEffect(retrying, isConnected, hasAdapterError, isSynced) {
        if (!retrying) return@LaunchedEffect

        when {
            isConnected && isSynced && !hasAdapterError -> currentOnRetryFinish.value()
            !isConnected || hasAdapterError -> {
                delay(RetryProgressMinVisibleMillis)
                currentOnRetryFinish.value()
            }
        }
    }
}
