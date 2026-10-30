package cash.p.terminal.trezor.signer

import cash.p.terminal.wallet.hardware.BtcPreviousTransactionProvider as HardwareBtcPreviousTransactionProvider

/** Kept under the Trezor package so existing Trezor callers keep their name. */
typealias BtcPreviousTransactionProvider = HardwareBtcPreviousTransactionProvider
