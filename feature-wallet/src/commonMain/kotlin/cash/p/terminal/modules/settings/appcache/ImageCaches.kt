package cash.p.terminal.modules.settings.appcache

import coil3.PlatformContext
import coil3.SingletonImageLoader

fun clearImageCaches(context: PlatformContext) {
    SingletonImageLoader.get(context).run {
        diskCache?.clear()
        memoryCache?.clear()
    }
}
