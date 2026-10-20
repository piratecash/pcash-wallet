package cash.p.terminal.core

import kotlinx.coroutines.flow.Flow

interface IBackupManager {
    val allBackedUp: Boolean
    val allBackedUpFlow: Flow<Boolean>
}
