package cash.p.terminal.feature.logging.photo

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import org.koin.compose.koinInject

private val LocalLoginPhotoImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("ProvideLoginPhotoImageLoader is not on the composition path")
}

/** Photos are private: the loader has no disk cache, so nothing ever lands in a plain-file cache. */
@Composable
internal fun ProvideLoginPhotoImageLoader(content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
        return
    }
    val source = koinInject<LoginPhotoSource>()
    val context = LocalPlatformContext.current
    val imageLoader = remember(source, context) { buildLoginPhotoImageLoader(source, context) }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
    CompositionLocalProvider(LocalLoginPhotoImageLoader provides imageLoader, content = content)
}

@Composable
internal fun LoginPhotoImage(
    photo: LoginPhoto?,
    placeholder: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val painter = photo?.let {
        rememberAsyncImagePainter(model = it, imageLoader = LocalLoginPhotoImageLoader.current)
    }
    val loadFailed = painter?.state?.collectAsStateWithLifecycle()?.value is AsyncImagePainter.State.Error
    if (painter == null || loadFailed) {
        placeholder()
    } else {
        Image(painter = painter, contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

private fun buildLoginPhotoImageLoader(source: LoginPhotoSource, context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(LoginPhotoFetcher.Factory(source)) }
        .diskCache(null)
        .build()

private class LoginPhotoFetcher(
    private val photo: LoginPhoto,
    private val source: LoginPhotoSource,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = source.read(photo) ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val source: LoginPhotoSource) : Fetcher.Factory<LoginPhoto> {
        override fun create(data: LoginPhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            LoginPhotoFetcher(data, source, options)
    }
}
