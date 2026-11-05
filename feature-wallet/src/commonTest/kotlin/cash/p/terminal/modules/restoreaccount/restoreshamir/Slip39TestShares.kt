package cash.p.terminal.modules.restoreaccount.restoreshamir

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.slip39.Slip39AccountTypeFactory
import cash.p.terminal.wallet.slip39.Slip39Shamir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

// Trezor firmware fixtures (tests/common.py).
internal object Slip39TestShares {
    val BASIC_20_3OF6 = listOf(
        "extra extend academic bishop cricket bundle tofu goat apart victim enlarge program behavior permit course armed jerky faint language modern",
        "extra extend academic acne away best indicate impact square oasis prospect painting voting guest either argue username racism enemy eclipse",
        "extra extend academic arcade born dive legal hush gross briefing talent drug much home firefly toxic analysis idea umbrella slice",
    )
    const val BASIC_EXT_20_FIRST =
        "enemy favorite academic acid cowboy phrase havoc level response walnut budget painting inside trash adjust froth kitchen learn tidy punish"
    val ADVANCED_20 = listOf(
        "eraser senior beard romp adorn nuclear spill corner cradle style ancient family general leader ambition exchange unusual garlic promise voice",
        "eraser senior ceramic snake clay various huge numb argue hesitate auction category timber browser greatest hanger petition script leaf pickup",
        "eraser senior ceramic shaft dynamic become junior wrist silver peasant force math alto coal amazing segment yelp velvet image paces",
        "eraser senior ceramic round column hawk trust auction smug shame alive greatest sheriff living perfect corner chest sled fumes adequate",
    )
    const val BIP39_12 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    /** The deterministic 1-of-1 share an account restored from [BASIC_20_3OF6] stores. */
    val BASIC_20_3OF6_CANONICAL: List<String> by lazy {
        val masterSecret = Slip39Shamir.combine(BASIC_20_3OF6.map(Slip39Shamir::parseShare))
        Slip39Shamir.split(masterSecret, threshold = 1, count = 1, SecureRandom()).single()
    }

    fun slip39Factory() = Slip39AccountTypeFactory(
        TestDispatcherProvider(Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined))
    )

    fun slip39Type(passphrase: String = ""): AccountType.Slip39 =
        runBlocking { slip39Factory().create(BASIC_20_3OF6_CANONICAL, passphrase) }
}
