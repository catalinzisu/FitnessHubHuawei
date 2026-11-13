package ro.fitnesshub.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ro.fitnesshub.server.TemplateExerciseDto
import ro.fitnesshub.server.TemplateRequest
import ro.fitnesshub.server.TemplateResponse

/** Training days. Every function only sees the templates of user [uid]. */
class TemplateRepository(private val db: AppDatabase) {

    suspend fun list(uid: String): List<TemplateResponse> = db.query {
        val rows = Templates.selectAll().where { Templates.userId eq uid }.orderBy(Templates.id).toList()
        // One query for the exercises of all templates, instead of one per template.
        val exercises = TemplateExercises.selectAll()
            .where { TemplateExercises.templateId inList rows.map { it[Templates.id] } }
            .orderBy(TemplateExercises.position to SortOrder.ASC)
            .groupBy({ it[TemplateExercises.templateId] }, { it.toExercise() })
        rows.map { it.toTemplate(exercises[it[Templates.id]].orEmpty()) }
    }

    /** Null if there is no such template or it belongs to someone else. */
    suspend fun get(uid: String, templateId: Long): TemplateResponse? = db.query { load(uid, templateId) }

    suspend fun create(uid: String, request: TemplateRequest): TemplateResponse = db.query {
        val newId = Templates.insert {
            it[Templates.userId] = uid
            it[Templates.name] = request.name.trim()
            it[Templates.weekdays] = request.weekdays.joinToString(",")
            it[Templates.updatedAt] = System.currentTimeMillis()
        }[Templates.id]
        insertExercises(newId, request.exercises)
        load(uid, newId)!!
    }

    /** Replaces name, days and the whole exercise list. Null if the template isn't the user's. */
    suspend fun update(uid: String, templateId: Long, request: TemplateRequest): TemplateResponse? = db.query {
        val changed = Templates.update({ (Templates.id eq templateId) and (Templates.userId eq uid) }) {
            it[Templates.name] = request.name.trim()
            it[Templates.weekdays] = request.weekdays.joinToString(",")
            it[Templates.updatedAt] = System.currentTimeMillis()
        }
        if (changed == 0) {
            null
        } else {
            TemplateExercises.deleteWhere { TemplateExercises.templateId eq templateId }
            insertExercises(templateId, request.exercises)
            load(uid, templateId)
        }
    }

    /** True if something was deleted. Its exercises go with it (ON DELETE CASCADE). */
    suspend fun delete(uid: String, templateId: Long): Boolean = db.query {
        Templates.deleteWhere { (Templates.id eq templateId) and (Templates.userId eq uid) } > 0
    }

    // ---- Helpers; they run inside the caller's transaction ----

    private fun load(uid: String, templateId: Long): TemplateResponse? {
        val row = Templates.selectAll()
            .where { (Templates.id eq templateId) and (Templates.userId eq uid) }
            .singleOrNull() ?: return null
        val exercises = TemplateExercises.selectAll()
            .where { TemplateExercises.templateId eq templateId }
            .orderBy(TemplateExercises.position to SortOrder.ASC)
            .map { it.toExercise() }
        return row.toTemplate(exercises)
    }

    private fun insertExercises(templateId: Long, exercises: List<TemplateExerciseDto>) {
        TemplateExercises.batchInsert(exercises.withIndex()) { (index, exercise) ->
            this[TemplateExercises.templateId] = templateId
            this[TemplateExercises.position] = index
            this[TemplateExercises.exerciseName] = exercise.exerciseName.trim()
            this[TemplateExercises.primaryMuscle] = exercise.primaryMuscle
            this[TemplateExercises.targetSets] = exercise.targetSets
            this[TemplateExercises.repsMin] = exercise.repsMin
            this[TemplateExercises.repsMax] = exercise.repsMax
            this[TemplateExercises.restSeconds] = exercise.restSeconds
        }
    }

    private fun ResultRow.toTemplate(exercises: List<TemplateExerciseDto>) = TemplateResponse(
        id = this[Templates.id],
        name = this[Templates.name],
        weekdays = this[Templates.weekdays].split(',').filter { it.isNotBlank() },
        exercises = exercises,
        updatedAt = this[Templates.updatedAt],
    )

    private fun ResultRow.toExercise() = TemplateExerciseDto(
        exerciseName = this[TemplateExercises.exerciseName],
        primaryMuscle = this[TemplateExercises.primaryMuscle],
        targetSets = this[TemplateExercises.targetSets],
        repsMin = this[TemplateExercises.repsMin],
        repsMax = this[TemplateExercises.repsMax],
        restSeconds = this[TemplateExercises.restSeconds],
    )
}
