package ro.fitnesshub.server.db

import org.jetbrains.exposed.v1.jdbc.upsert

class UserRepository(private val db: AppDatabase) {

    /** Creates the user's row the first time they call the API; changes nothing afterwards. */
    suspend fun ensure(uid: String) {
        db.query {
            // One statement (MERGE in H2), so two first requests at the same time can't both insert.
            Users.upsert(onUpdateExclude = listOf(Users.createdAt)) {
                it[Users.uid] = uid
                it[Users.createdAt] = System.currentTimeMillis()
            }
        }
    }
}
