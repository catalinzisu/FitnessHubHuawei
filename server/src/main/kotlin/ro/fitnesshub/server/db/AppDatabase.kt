package ro.fitnesshub.server.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException

/**
 * The server's H2 database. Production uses a file ([DEFAULT_URL]); tests use a fresh in-memory
 * database each, so they never touch real data.
 */
class AppDatabase(url: String = DEFAULT_URL) {

    private val database = Database.connect(url, driver = "org.h2.Driver")

    init {
        // Creates missing tables on start-up. Existing tables and their data are left alone.
        transaction(database) { SchemaUtils.create(*allTables) }
    }

    /**
     * Runs [block] in one transaction: all of it is saved, or nothing if it throws.
     * JDBC calls block the thread, so they run on [Dispatchers.IO], away from the threads
     * that handle requests.
     */
    suspend fun <T> query(block: suspend JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(database) { block() } }

    /** True if a trivial query works. Used by GET /health. */
    suspend fun isReachable(): Boolean = try {
        query { exec("SELECT 1") }
        true
    } catch (e: SQLException) {
        false
    }

    companion object {
        /** server/db/fitnesshub.mv.db when started with `./gradlew :server:run`. Change it with DB_URL. */
        const val DEFAULT_URL = "jdbc:h2:file:./db/fitnesshub"

        /** A private in-memory database that lives until the JVM exits. */
        fun inMemory(name: String): AppDatabase = AppDatabase("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1")
    }
}
