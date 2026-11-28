package cash.p.terminal.feature.logging.photo

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File

internal class AndroidLoginPhotoExporter(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : LoginPhotoExporter {

    override suspend fun save(photo: LoginPhoto): PhotoSaveResult = withContext(dispatcherProvider.io) {
        try {
            val file = File(photo.key)
            if (!file.exists()) return@withContext PhotoSaveResult.FAILED

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "login_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext PhotoSaveResult.FAILED

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            PhotoSaveResult.SAVED
        } catch (e: Exception) {
            PhotoSaveResult.FAILED
        }
    }
}
