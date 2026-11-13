package ro.fitnesshub.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import ro.fitnesshub.api.WorkoutRequest
import ro.fitnesshub.api.WorkoutResponse
import ro.fitnesshub.api.WorkoutSetDto

/** Result of an upload: the stored workout, and whether it is new (false = it was already there). */
data class UploadResult(val workout: WorkoutResponse, val created: Boolean)

/** Finished workouts. Every function only sees the workouts of user [uid]. */
class WorkoutRepository(private val db: AppDatabase) {

    /** Workouts started at or after [since] (all of them if null), oldest first. */
    suspend fun list(uid: String, since: Long?): List<WorkoutResponse> = db.query {
        val rows = Workouts.selectAll()
            .where {
                if (since == null) {
                    Workouts.userId eq uid
                } else {
                    (Workouts.userId eq uid) and (Workouts.startedAt greaterEq since)
                }
            }
            .orderBy(Workouts.startedAt to SortOrder.ASC)
            .toList()
        // One query for the sets of all workouts, instead of one per workout.
        val sets = WorkoutSets.selectAll()
            .where { WorkoutSets.workoutId inList rows.map { it[Workouts.id] } }
            .orderBy(WorkoutSets.id to SortOrder.ASC)
            .groupBy({ it[WorkoutSets.workoutId] }, { it.toSet() })
        rows.map { it.toWorkout(sets[it[Workouts.id]].orEmpty()) }
    }

    /**
     * Stores a workout with its sets. If this user already uploaded a workout with the same
     * clientId (e.g. the app retried after a timeout), nothing is added and the stored one is returned.
     */
    suspend fun upload(uid: String, request: WorkoutRequest): UploadResult = db.query {
        val existing = Workouts.selectAll()
            .where { (Workouts.userId eq uid) and (Workouts.clientId eq request.clientId) }
            .singleOrNull()
        if (existing != null) {
            UploadResult(load(existing), created = false)
        } else {
            val newId = Workouts.insert {
                it[Workouts.userId] = uid
                it[Workouts.clientId] = request.clientId
                it[Workouts.title] = request.title?.trim()
                it[Workouts.startedAt] = request.startedAt
                it[Workouts.endedAt] = request.endedAt
                it[Workouts.avgHr] = request.avgHr
                it[Workouts.maxHr] = request.maxHr
                it[Workouts.kcal] = request.kcal
            }[Workouts.id]
            WorkoutSets.batchInsert(request.sets) { set ->
                this[WorkoutSets.workoutId] = newId
                this[WorkoutSets.exerciseName] = set.exerciseName.trim()
                this[WorkoutSets.primaryMuscle] = set.primaryMuscle
                this[WorkoutSets.setNumber] = set.setNumber
                this[WorkoutSets.weightKg] = set.weightKg
                this[WorkoutSets.reps] = set.reps
                this[WorkoutSets.rpe] = set.rpe
                this[WorkoutSets.isWarmup] = set.isWarmup
            }
            val row = Workouts.selectAll().where { Workouts.id eq newId }.single()
            UploadResult(load(row), created = true)
        }
    }

    /** True if something was deleted. Its sets go with it (ON DELETE CASCADE). */
    suspend fun delete(uid: String, workoutId: Long): Boolean = db.query {
        Workouts.deleteWhere { (Workouts.id eq workoutId) and (Workouts.userId eq uid) } > 0
    }

    // ---- Helpers; they run inside the caller's transaction ----

    private fun load(row: ResultRow): WorkoutResponse {
        val sets = WorkoutSets.selectAll()
            .where { WorkoutSets.workoutId eq row[Workouts.id] }
            .orderBy(WorkoutSets.id to SortOrder.ASC)
            .map { it.toSet() }
        return row.toWorkout(sets)
    }

    private fun ResultRow.toWorkout(sets: List<WorkoutSetDto>) = WorkoutResponse(
        id = this[Workouts.id],
        clientId = this[Workouts.clientId],
        title = this[Workouts.title],
        startedAt = this[Workouts.startedAt],
        endedAt = this[Workouts.endedAt],
        avgHr = this[Workouts.avgHr],
        maxHr = this[Workouts.maxHr],
        kcal = this[Workouts.kcal],
        sets = sets,
    )

    private fun ResultRow.toSet() = WorkoutSetDto(
        exerciseName = this[WorkoutSets.exerciseName],
        primaryMuscle = this[WorkoutSets.primaryMuscle],
        setNumber = this[WorkoutSets.setNumber],
        weightKg = this[WorkoutSets.weightKg],
        reps = this[WorkoutSets.reps],
        rpe = this[WorkoutSets.rpe],
        isWarmup = this[WorkoutSets.isWarmup],
    )
}
