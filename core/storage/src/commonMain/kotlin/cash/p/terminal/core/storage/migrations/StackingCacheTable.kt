package cash.p.terminal.core.storage.migrations

/** Shared by the Android and desktop 119 → 120 migrations; must match Room's schema of `StackingCache`. */
const val CREATE_STACKING_CACHE_TABLE =
    "CREATE TABLE IF NOT EXISTS `StackingCache` (`coin` TEXT NOT NULL, `address` TEXT NOT NULL, " +
        "`unpaid` TEXT NOT NULL, `totalIncome` TEXT NOT NULL, `nextAccrualAt` INTEGER, `nextPayoutAt` INTEGER, " +
        "`cachedBalance` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`coin`, `address`))"
