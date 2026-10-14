package io.horizontalsystems.core

interface LocalizedResources {
    fun getString(key: String): String

    fun getStringArrayItem(key: String, index: Int): String
}
