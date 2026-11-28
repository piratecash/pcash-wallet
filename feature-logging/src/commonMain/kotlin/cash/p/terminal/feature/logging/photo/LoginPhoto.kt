package cash.p.terminal.feature.logging.photo

/** Platform-neutral handle of a stored login photo; only a [LoginPhotoSource] knows how to read [key]. */
data class LoginPhoto(val key: String)
