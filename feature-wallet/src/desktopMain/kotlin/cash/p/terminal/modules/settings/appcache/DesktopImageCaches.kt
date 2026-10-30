package cash.p.terminal.modules.settings.appcache

import coil3.PlatformContext

fun clearDesktopImageCaches() = clearImageCaches(PlatformContext.INSTANCE)
