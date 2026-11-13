package ro.fitnesshub.server.db

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Checks the schema itself: tables exist, links between them work, rules are enforced. */
class DatabaseTest {

    private val db = AppDatabase.inMemory("db-test-${System.nanoTime()}")

    private suspend fun addUser(uid: String) = db.query {
        Users.insert {
            it[Users.uid] = uid
            it[createdAt] = 0
        }
    }

    private suspend fun addWorkout(uid: String, clientId: Long): Long = db.query {
        Workouts.insert {
            it[userId] = uid
            it[Workouts.clientId] = clientId
            it[startedAt] = 1_000
            it[endedAt] = 2_000
        }[Workouts.id]
    }

    @Test
    fun startsEmptyAndReachable(): Unit = runBlocking {
        assertTrue(db.isReachable())
        assertEquals(0, db.query { Users.selectAll().count() })
    }

    @Test
    fun deletingAUser_deletesAllTheirData(): Unit = runBlocking {
        addUser("alice")
        addUser("bob")
        db.query {
            Profiles.insert {
                it[userId] = "alice"
                it[weeklyGoal] = 3
                it[updatedAt] = 0
            }
            val templateId = Templates.insert {
                it[userId] = "alice"
                it[name] = "Push A"
                it[weekdays] = "MONDAY"
                it[updatedAt] = 0
            }[Templates.id]
            TemplateExercises.insert {
                it[TemplateExercises.templateId] = templateId
                it[position] = 0
                it[exerciseName] = "Barbell Bench Press"
                it[primaryMuscle] = "chest"
                it[targetSets] = 3
                it[repsMin] = 8
                it[repsMax] = 12
                it[restSeconds] = 120
            }
        }
        val workoutId = addWorkout("alice", clientId = 1)
        addWorkout("bob", clientId = 1)
        db.query {
            WorkoutSets.insert {
                it[WorkoutSets.workoutId] = workoutId
                it[exerciseName] = "Barbell Bench Press"
                it[primaryMuscle] = "chest"
                it[setNumber] = 1
                it[weightKg] = 80.0
                it[reps] = 8
                it[isWarmup] = false
            }
        }

        db.query { Users.deleteWhere { Users.uid eq "alice" } }

        db.query {
            assertEquals(0, Profiles.selectAll().count())
            assertEquals(0, Templates.selectAll().count())
            assertEquals(0, TemplateExercises.selectAll().count())
            assertEquals(0, WorkoutSets.selectAll().count())
            // Bob's data is untouched.
            assertEquals(1, Workouts.selectAll().count())
            assertEquals("bob", Workouts.selectAll().single()[Workouts.userId])
        }
    }

    @Test
    fun sameWorkoutUploadedTwice_isRejected(): Unit = runBlocking {
        addUser("alice")
        addWorkout("alice", clientId = 7)

        assertFailsWith<ExposedSQLException> { addWorkout("alice", clientId = 7) }
        // A failed transaction saves nothing: still one workout.
        assertEquals(1, db.query { Workouts.selectAll().count() })
    }

    @Test
    fun dataForAnUnknownUser_isRejected(): Unit = runBlocking {
        assertFailsWith<ExposedSQLException> { addWorkout("nobody", clientId = 1) }
    }

    @Test
    fun ensureUser_createsOnceAndKeepsTheFirstDate(): Unit = runBlocking {
        val users = UserRepository(db)
        users.ensure("alice")
        val firstCreatedAt = db.query { Users.selectAll().single()[Users.createdAt] }
        Thread.sleep(5)

        users.ensure("alice")

        db.query {
            assertEquals(1, Users.selectAll().count())
            assertEquals(firstCreatedAt, Users.selectAll().single()[Users.createdAt])
        }
    }
}
