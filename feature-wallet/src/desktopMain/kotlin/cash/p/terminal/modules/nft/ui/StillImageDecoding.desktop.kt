package cash.p.terminal.modules.nft.ui

import coil3.request.ImageRequest

/** The desktop image loader has no animated decoder. */
internal actual fun ImageRequest.Builder.decodeAsStill(): ImageRequest.Builder = this
