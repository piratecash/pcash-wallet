package cash.p.terminal.modules.nft.ui

import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest

internal actual fun ImageRequest.Builder.decodeAsStill(): ImageRequest.Builder =
    decoderFactory(BitmapFactoryDecoder.Factory())
