package cash.p.terminal.core

interface WalletOnboardingStorage {
    var termsAccepted: Boolean
    var mainShowedOnce: Boolean
    var passphraseTermsAgreed: Boolean
    var safetyRulesAgreed: Boolean
}
