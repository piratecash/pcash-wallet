package cash.p.terminal.modules.paycore

object PayCoreAssetResolver {
    private const val RUB_COIN_UID = "rub"

    fun coinCode(coinUid: String): String? = if (coinUid == RUB_COIN_UID) "RUB" else null
}
