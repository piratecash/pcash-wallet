package cash.p.terminal.modules.manageaccount

import cash.p.terminal.modules.balance.HeaderNote
import org.jetbrains.compose.resources.StringResource

object ManageAccountModule {
    data class ViewState(
        val title: String,
        val newName: String,
        val canSave: Boolean,
        val closeScreen: Boolean,
        val headerNote: HeaderNote,
        val keyActions: List<KeyAction>,
        val backupActions: List<BackupItem>,
        val signedHashes: Int?,
        val canBeDuplicated: Boolean
    )

    enum class KeyAction {
        RecoveryPhrase,
        RecoveryPhraseMonero,
        PublicKeys,
        PrivateKeys,
        ResetToFactorySettings,
        ChangeAccessCode,
        AccessCodeRecovery,
        ForgotAccessCode,
        ViewKey,
        SpendKey
    }

    sealed class BackupItem{
        class ManualBackup(val showAttention: Boolean, val completed: Boolean = false) : BackupItem()
        class LocalBackup(val showAttention: Boolean) : BackupItem()
        class InfoText(val textRes: StringResource) : BackupItem()
    }
}
