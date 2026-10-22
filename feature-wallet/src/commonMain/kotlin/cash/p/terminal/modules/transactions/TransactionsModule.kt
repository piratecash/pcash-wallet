package cash.p.terminal.modules.transactions

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource

data class TransactionWallet(
    val token: Token?,
    val source: TransactionSource,
    val badge: String?
)

data class FilterToken(
    val token: Token,
    val source: TransactionSource,
)
