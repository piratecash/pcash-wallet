package io.horizontalsystems.core.logger

import java.util.UUID

class AppLogger(
    private val scope: List<String> = listOf(),
    private val accountId: String? = null,
) {

    constructor(group: String) : this(listOf(group))

    private val actionId: String
        get() = scope.joinToString(":")

    fun getScopedUnique() : AppLogger {
        return getScoped(UUID.randomUUID().toString())
    }

    fun getScoped(scope: String) : AppLogger {
        return AppLogger(this.scope + scope, accountId)
    }

    /** Lines are written only while [accountId] is visible at the current user level. */
    fun forAccount(accountId: String): AppLogger = AppLogger(scope, accountId)

    fun info(message: String) {
        AppLog.info(actionId, message, accountId)
    }

    fun warning(message: String, e: Throwable) {
        AppLog.warning(actionId, message, e, accountId)
    }

    fun warning(message: String) {
        AppLog.warning(actionId, message, accountId)
    }
}
