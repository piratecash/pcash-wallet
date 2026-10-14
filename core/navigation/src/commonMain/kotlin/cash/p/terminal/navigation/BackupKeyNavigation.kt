package cash.p.terminal.navigation

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
data class BackupKeyInput(val accountId: String) : CommonParcelable
