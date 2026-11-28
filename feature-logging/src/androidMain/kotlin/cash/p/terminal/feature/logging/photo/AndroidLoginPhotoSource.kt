package cash.p.terminal.feature.logging.photo

import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File

internal class AndroidLoginPhotoSource(
    private val dispatcherProvider: DispatcherProvider
) : LoginPhotoSource {

    override suspend fun read(photo: LoginPhoto): ByteArray? = withContext(dispatcherProvider.io) {
        File(photo.key).takeIf { it.exists() }?.readBytes()
    }
}
