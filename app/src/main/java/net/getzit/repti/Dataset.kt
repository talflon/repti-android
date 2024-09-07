// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.lang.Math.toIntExact
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * A set of tasks, with their information and synchronization information.
 *
 * The serialization format, expressed in JSON, is:
 *
 *    {
 *      "tasks": {
 *        "(task id)": {
 *          "name": "(name)",
 *          "done": (date),
 *        }
 *      },
 *      "updates": {
 *        "(task id)": {
 *          "name": (timestamp),
 *          "done": (timestamp),
 *        }
 *      },
 *      "deleted": {
 *        "(task id)": (timestamp)
 *      }
 *    }
 *
 * where
 * - `(task id)` is lower-case alphanumeric of length 7 (see [TaskId])
 * - `(date)` is the number of days since 2021-01-01 (see [Day])
 * - `(timestamp)` is the number of seconds since 2021-01-01 (see [Timestamp])
 */
@Serializable
class Dataset {
    internal val tasks = mutableMapOf<TaskId, Task>()
    internal val updates = mutableMapOf<TaskId, MutableMap<String, Timestamp>>()
    internal val deleted = mutableMapOf<TaskId, Timestamp>()

    /**
     * [Clock] to be used for update times, defaults to [Clock.systemDefaultZone]
     */
    @Transient
    var clock: Clock = Clock.systemDefaultZone()

    /**
     * All of the tasks, as an immutable [Collection].
     */
    val allTasks: Collection<Task>
        get() = tasks.values

    fun getTask(id: TaskId): Task? = tasks[id]

    /**
     * Creates a new, not-done [Task].
     *
     * @param name the name to be set
     */
    fun newTask(name: String): Task {
        val now = Timestamp.now(clock)
        var id: TaskId
        do {
            id = TaskId.random()
        } while (id in tasks || id in deleted)
        val task = Task(id, name, null)
        tasks[id] = task
        updates[id] = mutableMapOf("name" to now)
        return task
    }

    /**
     * Deletes a task.
     *
     * @param taskId the [TaskId] of the task to be deleted
     */
    fun delete(taskId: TaskId) {
        deleted[taskId] = Timestamp.now(clock)
        tasks -= taskId
        updates -= taskId
    }

    /**
     * Deletes a task.
     *
     * @param task the [Task] to be deleted, which must belong to this dataset.
     */
    fun delete(task: Task) {
        delete(task.id)
    }

    fun update(task: Task) {
        val now = Timestamp.now(clock)
        val oldVersion = tasks[task.id]!!
        val timestamps = this.updates[task.id]!!
        if (task.name != oldVersion.name) {
            timestamps["name"] = now
        }
        if (task.done != oldVersion.done) {
            timestamps["done"] = now
        }
        tasks[task.id] = task
    }

    /**
     * Creates a copy of this, except `clock` is set to the default.
     */
    fun copy() = Dataset().also {
        it.tasks.putAll(this.tasks)
        it.updates.putAll(this.updates.mapValues { it.value.toMutableMap() })
        it.deleted.putAll(this.deleted)
    }

    fun lastUpdate(id: TaskId): Timestamp = updates[id]!!.values.max()

    /**
     * Synchronize this dataset by copying from another.
     *
     * Only copies newer changes. When there are competing changes with the same timestamp,
     * the tiebreaker is arbitrary but consistent.
     *
     * @param other the dataset to copy changes from, which will not itself be modified.
     */
    fun updateFrom(other: Dataset) {
        // Check for deletions
        for ((id, otherDeleted) in other.deleted) {
            val task = this.tasks[id]
            if (task == null) {
                // ensure we have the most recent deletion timestamp
                val ourDeleted = this.deleted[id]
                if (ourDeleted == null || ourDeleted < otherDeleted) {
                    this.deleted[id] = otherDeleted
                }
            } else if (this.lastUpdate(id) <= otherDeleted) { // tie goes to deletion
                this.tasks -= id
                this.updates -= id
                this.deleted[id] = otherDeleted
            }
        }
        // Check for new and updated tasks
        for ((id, otherTask) in other.tasks) {
            val ourDeleted = this.deleted[id]
            if (ourDeleted == null) {
                val ourTask = this.tasks[id]
                if (ourTask == null) {
                    this.tasks[id] = otherTask.copy()
                    this.updates[id] = other.updates[id]!!.toMutableMap()
                } else { // task exists in both; update individual fields
                    val updatedTask = ourTask.updateFrom(
                        otherTask,
                        ourUpdates = this.updates[id]!!,
                        otherUpdates = other.updates[id]!!
                    )
                    if (updatedTask != ourTask) {
                        this.tasks[id] = updatedTask
                    }
                }
            } else if (other.lastUpdate(id) > ourDeleted) { // tie goes to deletion
                this.deleted -= id
                this.tasks[id] = otherTask.copy()
                this.updates[id] = other.updates[id]!!.toMutableMap()
            }
        }
    }

    fun clear() {
        this.tasks.clear()
        this.updates.clear()
        this.deleted.clear()
    }

    override fun toString(): String = Json.encodeToString(this)

    internal fun requireValid() {
        require(tasks.size == updates.size)
        for (id in deleted.keys) {
            require(id.valid)
            require(id !in tasks)
            require(id !in updates)
        }
        for ((id, task) in tasks) {
            require(id.valid)
            require(id == task.id)
            val updates = this.updates[id]
            require(updates != null)
            require("name" in updates)
        }
    }

    companion object {
        fun fromString(s: String): Dataset =
            Json.decodeFromString<Dataset>(s).also { it.requireValid() }
    }
}

/**
 * Identifies a [Task], even if the task's name changes.
 */
@Serializable(with = TaskIdAsStringSerializer::class)
@Parcelize
class TaskId(val string: String) : Parcelable {
    companion object {
        const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
        const val ALPHABET_LENGTH = ALPHABET.length
        const val LENGTH = 7
        private const val NUM_IDS = 78364164096L  // = 36 raised to 7th power

        fun random(r: Random = Random.Default): TaskId = fromLong(r.nextLong(NUM_IDS))

        fun fromLong(long: Long) = TaskId(
            buildString(LENGTH) {
                var i = long
                while (length < LENGTH) {
                    append(ALPHABET[i.mod(ALPHABET_LENGTH)])
                    i /= ALPHABET_LENGTH
                }
            })
    }

    @Transient
    val valid: Boolean
        get() = string.length == LENGTH && string.all { it in ALPHABET }

    override fun toString() = string

    override fun hashCode() = string.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return string == (other as TaskId).string
    }
}

class TaskIdAsStringSerializer : KSerializer<TaskId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TaskId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder) = TaskId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: TaskId) {
        encoder.encodeString(value.string)
    }
}

/**
 * Stores a timestamp as the number of seconds since 2021-01-01.
 */
@Serializable
@JvmInline
value class Timestamp internal constructor(private val epochSecs: Int) : Comparable<Timestamp> {
    val asInstant: Instant get() = Instant.ofEpochSecond(epochSecs + EPOCH)

    override fun compareTo(other: Timestamp) = epochSecs.compareTo(other.epochSecs)

    companion object {
        /**
         * 2021-01-01, in seconds since the regular epoch (1970-01-01)
         */
        const val EPOCH: Long = Day.EPOCH * 86400L

        fun now(clock: Clock = Clock.systemDefaultZone()) = of(Instant.now(clock))

        fun of(instant: Instant) = Timestamp(toIntExact(instant.epochSecond - EPOCH))
    }
}

/**
 * Stores a date as the number of days since 2021-01-01.
 */
@Serializable
@JvmInline
value class Day internal constructor(private val epochDays: Int) : Comparable<Day> {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDays + EPOCH)

    override fun compareTo(other: Day) = epochDays.compareTo(other.epochDays)

    fun plusDays(days: Int) = Day(epochDays + days)

    fun minusDays(days: Int) = Day(epochDays - days)

    fun daysAfter(day: Day): Int = this.epochDays - day.epochDays

    val millis: Long get() = (epochDays + EPOCH) * MILLIS_IN_DAY

    companion object {
        /**
         * 2021-01-01, in days since the regular epoch (1970-01-01)
         */
        const val EPOCH: Long = 18628

        private const val MILLIS_IN_DAY: Long = 86_400_000

        fun today(clock: Clock = Clock.systemDefaultZone()) = of(LocalDate.now(clock))

        fun of(date: LocalDate) = Day(toIntExact(date.toEpochDay() - EPOCH))

        fun fromMillis(millis: Long) = Day(toIntExact((millis / MILLIS_IN_DAY - EPOCH)))
    }
}

/**
 * Represents a task that can be completed.
 *
 * @property id the task's [TaskId], to look it up in a [Dataset]
 * @property name the task's name
 * @property done the [Day] in which the task was done, or [null] if not yet done
 */
@Serializable
data class Task(val id: TaskId, val name: String, val done: Day?) {
    /**
     * Update this task with newer updates from `other`, returning the result.
     *
     * @param other the [Task] to take updates from
     * @param ourUpdates timestamps for our updates, to be modified with new updates
     * @param otherUpdates timestamps for `other`'s updates (won't be changed)
     */
    internal fun updateFrom(
        other: Task,
        ourUpdates: MutableMap<String, Timestamp>,
        otherUpdates: Map<String, Timestamp>,
    ) = copy(
        name = ifHasNewerUpdate("name", other, ourUpdates, otherUpdates) { it.name },
        done = ifHasNewerUpdate("done", other, ourUpdates, otherUpdates) { it.done },
    )

    /**
     * Helper function for [updateFrom].
     *
     * If the update timestamp for `key` in `other` is newer than ours, returns their value.
     * Otherwise, returns our value.
     * If the timestamps are the same, we use the greater non-null value, as a tiebreaker.
     * If their value is returned, updates `ourUpdates` with their update timestamp.
     */
    private inline fun <NT, T : Comparable<NT>?> ifHasNewerUpdate(
        key: String,
        other: Task,
        ourUpdates: MutableMap<String, Timestamp>,
        otherUpdates: Map<String, Timestamp>,
        getter: (Task) -> T,
    ): T {
        val otherTimestamp = otherUpdates[key]
        return if (otherTimestamp == null) {
            getter(this)
        } else {
            val ourTimestamp = ourUpdates[key]
            if (ourTimestamp == null || ourTimestamp < otherTimestamp) {
                ourUpdates[key] = otherTimestamp
                getter(other)
            } else if (ourTimestamp == otherTimestamp) { // tiebreaker
                val ourValue = getter(this)
                val otherValue = getter(other)
                if (compareValues(otherValue, ourValue) > 0) {
                    ourUpdates[key] = otherTimestamp
                    otherValue
                } else {
                    ourValue
                }
            } else {
                getter(this)
            }
        }
    }
}
