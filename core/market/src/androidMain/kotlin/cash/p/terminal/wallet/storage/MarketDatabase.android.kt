package cash.p.terminal.wallet.storage

import android.content.Context
import java.io.InputStream

internal const val INITIAL_COINS_ASSET = "initial_coins_list"

class AssetInitialCoinsSource(private val context: Context) : InitialCoinsSource {
    override fun open(): InputStream = context.assets.open(INITIAL_COINS_ASSET)
}
