package cash.p.terminal.wallet.storage

import java.io.InputStream

private const val INITIAL_COINS_RESOURCE = "initial_coins_list"

class ClasspathInitialCoinsSource : InitialCoinsSource {
    override fun open(): InputStream =
        checkNotNull(javaClass.classLoader.getResourceAsStream(INITIAL_COINS_RESOURCE)) {
            "Missing classpath resource: $INITIAL_COINS_RESOURCE"
        }
}
