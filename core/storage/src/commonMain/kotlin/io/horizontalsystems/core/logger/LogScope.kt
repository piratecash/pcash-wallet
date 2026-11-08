package io.horizontalsystems.core.logger

/** Who is looking: journal rows belong to the user level they were written at. */
interface LogScope {
    val userLevel: Int

    /** The level to stamp a line with, or null to drop it: an account line is stamped only at a level that loaded it. */
    fun stampFor(accountId: String?): Int?
}
