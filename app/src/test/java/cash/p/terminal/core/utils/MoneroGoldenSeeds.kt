package cash.p.terminal.core.utils

/**
 * Legacy Monero seeds with the secret keys native Monero derives from them, computed by an
 * independent reference implementation. Throwaway test vector — never a funded wallet.
 */
internal object MoneroGoldenSeeds {

    val SEED_0 =
        ("tasked eight afraid laboratory tail feline rift reinvest vane cafe bailed foggy " +
                "dormant paper jigsaw king hazard suture king dapper dummy jolted dating " +
                "dwindling king").split(" ")
    const val SPEND_KEY_0 = "96c80ac86d8b8374af5ea0376334d536a81c31ce3cd14874aa76fc0a9ddec60b"
    const val VIEW_KEY_0 = "bc403444975d3a0b1ed4210094e4dc1ffab42cf63474c89e75f424032dc7ef0e"

    val SEED_1 =
        ("palace pairing axes mohawk rekindle excess awful juvenile shipped talent nibs " +
                "efficient dapper biggest swung fight pact innocent emerge issued titans " +
                "affair nearby noises emerge").split(" ")
    const val SPEND_KEY_1 = "1138b972b514bf8f77c1f44f4df2f0aa9afc0cb884d958c89b2f7f6e41e22e04"
    const val VIEW_KEY_1 = "5007d9f32b939e4d6cbdf0f81d784d762829e8ea5a3f435bd967ad1faeebd806"
}
