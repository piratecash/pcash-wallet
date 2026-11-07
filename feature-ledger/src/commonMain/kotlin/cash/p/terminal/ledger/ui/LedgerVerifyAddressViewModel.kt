package cash.p.terminal.ledger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.ledger.domain.LedgerAddressVerification
import cash.p.terminal.wallet.Wallet
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LedgerVerifyAddressViewModel(
    private val wallet: Wallet,
    private val verification: LedgerAddressVerification,
) : ViewModel() {

    val available = verification.isAvailable(wallet.token.tokenQuery)

    var verifying by mutableStateOf(false)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    private var verifyJob: Job? = null

    fun verify(address: String) {
        if (verifyJob?.isActive == true) return

        verifyJob = viewModelScope.launch {
            verifying = true
            error = null
            try {
                verification.verify(wallet, address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Ledger address verification failed" }
                error = e
            } finally {
                verifying = false
            }
        }
    }

    private companion object {
        val logger = Logger.withTag("LedgerVerifyAddressViewModel")
    }
}
