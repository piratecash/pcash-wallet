package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenQuery

val TokenQuery.Companion.customCoinPrefix: String
    get() = "custom-"

val TokenQuery.customCoinUid: String
    get() = "${TokenQuery.customCoinPrefix}${id}"
