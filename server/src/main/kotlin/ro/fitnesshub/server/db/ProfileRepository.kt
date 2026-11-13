package ro.fitnesshub.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import ro.fitnesshub.api.ProfileDto

class ProfileRepository(private val db: AppDatabase) {

    /** The user's profile, or an empty one if they never saved it. */
    suspend fun get(uid: String): ProfileDto = db.query {
        Profiles.selectAll().where { Profiles.userId eq uid }.singleOrNull()?.toProfile() ?: ProfileDto()
    }

    /** Saves the whole profile (insert the first time, update after that). */
    suspend fun put(uid: String, profile: ProfileDto): ProfileDto = db.query {
        Profiles.upsert {
            it[Profiles.userId] = uid
            it[Profiles.displayName] = profile.displayName
            it[Profiles.heightCm] = profile.heightCm
            it[Profiles.weightKg] = profile.weightKg
            it[Profiles.weeklyGoal] = profile.weeklyGoal
            it[Profiles.updatedAt] = System.currentTimeMillis()
        }
        profile
    }

    private fun ResultRow.toProfile() = ProfileDto(
        displayName = this[Profiles.displayName],
        heightCm = this[Profiles.heightCm],
        weightKg = this[Profiles.weightKg],
        weeklyGoal = this[Profiles.weeklyGoal],
    )
}
