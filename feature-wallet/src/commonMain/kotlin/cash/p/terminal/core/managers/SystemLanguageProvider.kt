package cash.p.terminal.core.managers

/** Language of the device itself, unaffected by the in-app language override. */
interface SystemLanguageProvider {
    val language: String
}
