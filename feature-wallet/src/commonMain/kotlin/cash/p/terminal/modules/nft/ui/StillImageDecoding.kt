package cash.p.terminal.modules.nft.ui

import coil3.request.ImageRequest

/** Animated decoders ignore the requested size (the API 27 `GifDecoder` draws the full-size movie). */
internal expect fun ImageRequest.Builder.decodeAsStill(): ImageRequest.Builder
