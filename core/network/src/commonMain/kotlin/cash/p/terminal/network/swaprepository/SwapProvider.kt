package cash.p.terminal.network.swaprepository

enum class SwapProvider(val title: String) {
    CHANGENOW("ChangeNow"),
    QUICKEX("Quickex"),
    // Discontinued; kept only so stored history rows load (Room reads the provider back by name).
    PAYCORE("PayCore"),
    EXOLIX("Exolix"),
    THORCHAIN("THORChain"),
    MAYA("Maya Protocol"),
    UNSTOPPABLE("Unstoppable"),
    YIFI("YiFi"),
    PCASH_BACKEND("P.CASH"),
}
