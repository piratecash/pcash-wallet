package cash.p.terminal.wallet.storage

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

private val insertTablePattern = Regex("""^INSERT OR REPLACE INTO (\w+) """)

/**
 * Locates the checked-in initial coins dump. Resolves both the module-relative working
 * directory (Gradle test task) and the repo-root working directory (other test runners).
 */
internal fun initialCoinsFile(): File {
    val moduleRelativeFile = File("src/main/assets/initial_coins_list")
    if (moduleRelativeFile.exists()) {
        return moduleRelativeFile
    }

    return File("core/wallet/src/main/assets/initial_coins_list")
}

/**
 * Validates a coins-list SQL dump against a real in-memory SQLite database, catching
 * regressions a mocked database cannot: statements that fail to execute, INSERT OR REPLACE
 * silently collapsing duplicate primary keys, and dangling token -> coin/blockchain
 * references. Requires a Robolectric test runner for the native SQLite engine.
 */
internal fun validateDumpSql(dump: String) {
    val statements = dump.lines().filter { it.isNotBlank() }

    val database = SQLiteDatabase.create(null)
    try {
        database.beginTransaction()
        try {
            statements.forEach { statement -> database.execSQL(statement) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        assertRowCountsMatchInserts(database, dump)
        assertNoForeignKeyViolations(database)
    } finally {
        database.close()
    }
}

/**
 * Counts VALUES tuples for [table] in [dump], independent of how many rows a single
 * `INSERT OR REPLACE` statement batches together (see DumpManager.CHUNK_SIZE). Scans
 * quote-aware so a literal '(' or ')' inside an escaped string value is not mistaken for a
 * tuple boundary.
 */
internal fun countValueRows(dump: String, table: String): Int {
    val prefix = "INSERT OR REPLACE INTO $table "
    return dump.lineSequence()
        .filter { it.startsWith(prefix) }
        .sumOf { countTuples(it) }
}

private fun countTuples(statement: String): Int {
    var depth = 0
    var tuples = 0
    var i = 0
    while (i < statement.length) {
        when (statement[i]) {
            '\'' -> {
                i = skipStringLiteral(statement, i) - 1 // loop's i++ below lands right after it
            }
            '(' -> {
                if (depth == 0) tuples++
                depth++
            }
            ')' -> depth--
        }
        i++
    }
    return tuples
}

/** Returns the index right after the closing quote of the string literal starting at [quoteStart], honoring '' escapes. */
private fun skipStringLiteral(statement: String, quoteStart: Int): Int {
    var i = quoteStart + 1
    while (i < statement.length) {
        if (statement[i] == '\'') {
            val isEscapedQuote = i + 1 < statement.length && statement[i + 1] == '\''
            if (isEscapedQuote) i++ else return i + 1
        }
        i++
    }
    return i
}

private fun assertRowCountsMatchInserts(database: SQLiteDatabase, dump: String) {
    val tables = dump.lineSequence()
        .mapNotNull { insertTablePattern.find(it)?.groupValues?.get(1) }
        .toSet()

    tables.forEach { table ->
        val expectedCount = countValueRows(dump, table)
        database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Row count mismatch for $table (duplicate primary key?)", expectedCount, cursor.getInt(0))
        }
    }
}

private fun assertNoForeignKeyViolations(database: SQLiteDatabase) {
    database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
        assertTrue("Dangling foreign key references found", cursor.count == 0)
    }
}
