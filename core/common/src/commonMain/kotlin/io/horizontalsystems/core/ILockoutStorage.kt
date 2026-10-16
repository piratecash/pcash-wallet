package io.horizontalsystems.core

interface ILockoutStorage {
    var failedAttempts: Int?
    var lockoutUptime: Long?
}
