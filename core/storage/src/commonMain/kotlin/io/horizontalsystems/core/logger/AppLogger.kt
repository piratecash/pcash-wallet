package io.horizontalsystems.core.logger

import java.util.UUID

class AppLogger(private val scope: List<String> = listOf()) {

    constructor(group: String) : this(listOf(group))

    private val actionId: String
        get() = scope.joinToString(":")

    fun getScopedUnique() : AppLogger {
        return getScoped(UUID.randomUUID().toString())
    }

    fun getScoped(scope: String) : AppLogger {
        return AppLogger(this.scope + scope)
    }

    fun info(message: String) {
        AppLog.info(actionId, message)
    }

    fun warning(message: String, e: Throwable) {
        AppLog.warning(actionId, message, e)
    }

    fun warning(message: String) {
        AppLog.warning(actionId, message)
    }
}
