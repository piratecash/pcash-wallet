package cash.p.terminal.feature.logging.photo

interface LoginPhotoSource {
    suspend fun read(photo: LoginPhoto): ByteArray?
}
