package cash.p.terminal.feature.logging.photo

enum class PhotoSaveResult { SAVED, FAILED, CANCELLED }

interface LoginPhotoExporter {
    /** Saves the photo to the user's storage. */
    suspend fun save(photo: LoginPhoto): PhotoSaveResult
}
