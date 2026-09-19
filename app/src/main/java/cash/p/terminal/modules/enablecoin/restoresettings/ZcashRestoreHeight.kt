package cash.p.terminal.modules.enablecoin.restoresettings

import cash.p.terminal.core.managers.ZcashBirthdayProvider

/** A new wallet with no height entered starts from the shipped checkpoint, never from the genesis. */
fun TokenConfig.zcashBirthdayHeight(zcashBirthdayProvider: ZcashBirthdayProvider): Long? =
    birthdayHeight?.toLongOrNull()
        ?: zcashBirthdayProvider.getLatestCheckpointBlockHeight().takeIf { restoreAsNew }
