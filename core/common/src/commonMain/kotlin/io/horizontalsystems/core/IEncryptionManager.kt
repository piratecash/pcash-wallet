package io.horizontalsystems.core

interface IEncryptionManager {
    fun encrypt(data: String): String
    fun decrypt(data: String): String
}
