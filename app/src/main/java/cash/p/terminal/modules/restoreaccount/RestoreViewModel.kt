package cash.p.terminal.modules.restoreaccount

import androidx.lifecycle.ViewModel
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import cash.p.terminal.wallet.AccountType

class RestoreViewModel: ViewModel() {

    var accountType: AccountType? = null
        private set

    var accountName: String = ""
        private set

    var manualBackup: Boolean = false
        private set

    var fileBackup: Boolean = false
        private set

    private var importDraft: MnemonicImportDraft? = null
    val mnemonicDraft: MnemonicImportDraft
        get() = importDraft ?: MnemonicImportDraft()

    fun setDraft(draft: MnemonicImportDraft) {
        if (importDraft != draft) accountType = null
        importDraft = draft
    }

    fun initializeDraft(draft: MnemonicImportDraft?) {
        if (importDraft == null) importDraft = draft ?: MnemonicImportDraft()
    }

    private val _scannedText = MutableStateFlow<String?>(null)
    val scannedText = _scannedText.asStateFlow()

    fun onScannedText(text: String) {
        _scannedText.value = text
    }

    fun takeScannedText(): String? = _scannedText.getAndUpdate { null }

    private val _tokenConfigResult = MutableStateFlow<TokenConfigResult?>(null)
    val tokenConfigResult = _tokenConfigResult.asStateFlow()

    private var tokenConfigResultId = 0

    var tokenInitialConfig: TokenConfig? = null
        private set

    fun setAccountData(accountType: AccountType, accountName: String, manualBackup: Boolean, fileBackup: Boolean) {
        this.accountType = accountType
        this.accountName = accountName
        this.manualBackup = manualBackup
        this.fileBackup = fileBackup
    }

    fun setTokenInitialConfig(config: TokenConfig?) {
        tokenInitialConfig = config
    }

    fun setTokenConfig(config: TokenConfig) {
        _tokenConfigResult.value = TokenConfigResult.Entered(nextTokenConfigResultId(), config)
        tokenInitialConfig = null
    }

    fun cancelTokenConfig() {
        _tokenConfigResult.value = TokenConfigResult.Cancelled(nextTokenConfigResultId())
        tokenInitialConfig = null
    }

    fun clearTokenConfigResult(id: Int) {
        if (_tokenConfigResult.value?.id == id) {
            _tokenConfigResult.value = null
        }
    }

    private fun nextTokenConfigResultId(): Int {
        tokenConfigResultId += 1
        return tokenConfigResultId
    }
}

sealed interface TokenConfigResult {
    val id: Int

    data class Entered(override val id: Int, val config: TokenConfig) : TokenConfigResult
    data class Cancelled(override val id: Int) : TokenConfigResult
}
