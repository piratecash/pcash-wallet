package cash.p.terminal.modules.backuplocal.password

import cash.p.terminal.ui_compose.entities.DataState

object BackupLocalPasswordModule {

    data class UiState(
        val passphraseState: DataState.Error?,
        val passphraseConfirmState: DataState.Error?,
        val showButtonSpinner: Boolean,
        val backupData: ByteArray?,
        val closeScreen: Boolean,
        val error: String?,
        // Duress backup fields
        val duressBackupEnabled: Boolean = false,
        val duressBackupAvailable: Boolean = false,
        val pinEnabled: Boolean = false,
        val duressPassphraseState: DataState.Error? = null,
        val duressPassphraseConfirmState: DataState.Error? = null
    )
}
