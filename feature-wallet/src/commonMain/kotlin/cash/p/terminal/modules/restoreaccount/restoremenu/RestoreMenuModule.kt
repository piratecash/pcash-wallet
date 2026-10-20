package cash.p.terminal.modules.restoreaccount.restoremenu

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_PrivateKey
import cash.p.terminal.resources.Restore_RecoveryPhrase
import org.jetbrains.compose.resources.StringResource

object RestoreMenuModule {
    enum class RestoreOption(val titleRes: StringResource) {
        RecoveryPhrase(Res.string.Restore_RecoveryPhrase),
        PrivateKey(Res.string.Restore_PrivateKey)
    }
}
