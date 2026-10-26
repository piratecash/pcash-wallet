package cash.p.terminal.core.managers

import com.m2049r.xmrwallet.model.Wallet
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException

fun storeMoneroWalletSafely(
    failureMessage: String,
    store: () -> Int,
    onNativeFault: (HardwareWalletOperationException) -> Unit,
) {
    val status = store()
    if (status == MONERO_STORE_OK) return
    val failure = HardwareWalletOperationException(
        HardwareWalletErrorCode.StoreFailed,
        failureMessage,
    )
    if (status == MONERO_STORE_NATIVE_FAULT) {
        onNativeFault(failure)
    }
    throw failure
}

const val MONERO_STORE_OK = 0
const val MONERO_STORE_NATIVE_FAULT = 2

fun Wallet.Status?.toHardwareWalletCloseFailure(
    fallbackDetail: String,
) = HardwareWalletOperationException(
    this?.hardwareWalletError ?: HardwareWalletErrorCode.Protocol,
    this?.errorString?.takeIf(String::isNotBlank) ?: fallbackDetail,
)
