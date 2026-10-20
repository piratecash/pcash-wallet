package cash.p.terminal.core.utils

/**
 * Matches one mnemonic word. Java's `\S` is ASCII-only and never breaks on U+3000,
 * the ideographic space joining Japanese BIP39 phrases; ICU on Android has no `(?U)`.
 */
val MNEMONIC_WORD_REGEX = Regex("[^\\p{Z}\\u0009-\\u000D\\u0085]+")
