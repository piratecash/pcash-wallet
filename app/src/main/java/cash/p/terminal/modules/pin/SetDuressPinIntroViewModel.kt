package cash.p.terminal.modules.pin

import androidx.lifecycle.ViewModel
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.ISystemInfoManager

class SetDuressPinIntroViewModel(
    systemInfoManager: ISystemInfoManager,
    accountManager: IAccountManager,
) : ViewModel() {
    val biometricAuthSupported = systemInfoManager.biometricAuthSupported
    val shouldShowSelectAccounts = accountManager.accounts.isNotEmpty()
}
