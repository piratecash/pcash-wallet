package cash.p.terminal.feature.logging.history

import cash.p.terminal.feature.logging.photo.LoginPhoto

data class LoginRecordViewItem(
    val id: Long,
    val photo: LoginPhoto?,
    val isSuccessful: Boolean,
    val isDuressMode: Boolean,
    val walletName: String?,
    val formattedTime: String,
    val relativeTime: String
)
