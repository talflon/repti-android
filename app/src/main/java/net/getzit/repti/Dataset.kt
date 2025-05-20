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
 *      "order": [
 *        "(task id)",
 *        "(task id)"
 *      ],
 *      "updates": {
 *        "(task id)": {
 *          "name": (timestamp),
 *          "loc": (timestamp),
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
    internal val order = mutableListOf<TaskId>()
    internal val updates = mutableMapOf<TaskId, MutableMap<String, Timestamp>>()
    internal val deleted = mutableMapOf<TaskId, Timestamp>()

    /**
     * [Clock] to be used for update times, defaults to [Clock.systemDefaultZone]
     */
    @Transient
    var clock: Clock = Clock.systemDefaultZone()

    /**
     * All of the tasks, as an immutable [List].
     */
    val allTasks: List<Task>
        get() = order.map { id -> tasks.getValue(id) }

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
        order += id  // add to end of order
        updates[id] = mutableMapOf("name" to now, "loc" to now)
        return task
    }

    /**
     * Deletes a task.
     *
     * @param taskId the [TaskId] of the task to be deleted
     */
    fun delete(taskId: TaskId) {
        delete(taskId, Timestamp.now(clock))
    }

    /**
     * Deletes a task.
     *
     * @param task the [Task] to be deleted, which must belong to this dataset.
     */
    fun delete(task: Task) {
        delete(task.id)
    }

    /**
     * Deletes a task.
     *
     * @param taskId the [TaskId] of the task to be deleted
     * @param timestamp the [Timestamp] of the deletion
     */
    fun delete(taskId: TaskId, timestamp: Timestamp) {
        deleted[taskId] = timestamp
        tasks -= taskId
        order -= taskId
        updates -= taskId
    }

    fun update(task: Task) {
        val now = Timestamp.now(clock)
        val oldVersion = tasks.getValue(task.id)
        val timestamps = this.updates.getValue(task.id)
        if (task.name != oldVersion.name) {
            timestamps["name"] = now
        }
        if (task.done != oldVersion.done) {
            timestamps["done"] = now
        }
        tasks[task.id] = task
    }

    /**
     * Inserts a task into the order so that it comes before a given task.
     * If `beforeTaskId` is `null`, inserts it at the end.
     */
    fun moveTaskBefore(taskId: TaskId, beforeTaskId: TaskId?) {
        val oldIdx = order.indexOf(taskId)
        require(oldIdx >= 0) { "Task $taskId not in dataset" }
        val now = Timestamp.now(clock)
        if (beforeTaskId == null) {  // add to end
            if (oldIdx != order.size - 1) {  // if we're not already at the end
                order.removeAt(oldIdx)
                order += taskId
            }
        } else if (beforeTaskId != taskId && beforeTaskId != order.getOrNull(oldIdx + 1)) {
            order.remove(taskId)
            val beforeIdx = order.indexOf(beforeTaskId)
            require(beforeIdx >= 0) { "Task $beforeTaskId not in dataset" }
            order.add(beforeIdx, taskId)
        }
        this.updates.getValue(taskId)["loc"] = now
    }

    /**
     * Inserts a task into the order so that it comes after a given task.
     * If `afterTaskId` is `null`, inserts it at the beginning.
     */
    fun moveTaskAfter(taskId: TaskId, afterTaskId: TaskId?) {
        val oldIdx = order.indexOf(taskId)
        require(oldIdx >= 0) { "Task $taskId not in dataset" }
        val now = Timestamp.now(clock)
        if (afterTaskId == null) {  // add to beginning
            if (oldIdx != 0) {  // if we're not already at the beginning
                order.remove(taskId)
                order.add(0, taskId)
            }
        } else if (afterTaskId != taskId && afterTaskId != order.getOrNull(oldIdx - 1)) {
            order.remove(taskId)
            val afterIdx = order.indexOf(afterTaskId)
            require(afterIdx >= 0) { "Task $afterTaskId not in dataset" }
            order.add(afterIdx + 1, taskId)
        }
        this.updates.getValue(taskId)["loc"] = now
    }

    /**
     * Creates a copy of this, except `clock` is set to the default.
     */
    fun copy() = Dataset().also {
        it.tasks.putAll(this.tasks)
        it.order.addAll(this.order)
        it.updates.putAll(this.updates.mapValues { it.value.toMutableMap() })
        it.deleted.putAll(this.deleted)
    }

    /**
     * Gets the most recent update time for a given TaskId.
     * The task must exist in this Dataset.
     */
    fun lastUpdate(id: TaskId): Timestamp = updates.getValue(id).values.max()

    /**
     * Gets the most recent update time for any task in this Dataset, or null if there are no tasks.
     */
    fun lastUpdate(): Timestamp? = updates.values.maxOfOrNull { it.values.max() }

    /**
     * Synchronize this dataset by copying from another.
     *
     * Only copies newer changes. When there are competing changes with the same timestamp,
     * the tiebreaker is arbitrary but consistent.
     *
     * @param other the dataset to copy changes from, which will not itself be modified.
     */
    fun updateFrom(other: Dataset) {
        fun updateTask(id: TaskId, ourTask: Task, otherTask: Task) {
            val updatedTask = ourTask.updateFrom(
                otherTask,
                ourUpdates = this.updates.getValue(id),
                otherUpdates = other.updates.getValue(id)
            )
            if (updatedTask != ourTask) {
                this.tasks[id] = updatedTask
            }
        }

        if (this.order == other.order) {
            for ((id, otherDeleted) in other.deleted) {
                // ensure we have the most recent deletion timestamp
                val ourDeleted = this.deleted[id]
                if (ourDeleted == null || ourDeleted < otherDeleted) {
                    this.deleted[id] = otherDeleted
                }
            }
            for ((id, otherTask) in other.tasks) {
                updateTask(id, ourTask = this.tasks[id]!!, otherTask = otherTask)
            }
        } else {
            // merge orders before we start modifying ourself
            val newOrder = mergedOrder(other)

            // Check for deletions in other
            for ((id, otherDeleted) in other.deleted) {
                val ourTask = this.tasks[id]
                if (ourTask == null) {
                    // ensure we have the most recent deletion timestamp
                    val ourDeleted = this.deleted[id]
                    if (ourDeleted == null || ourDeleted < otherDeleted) {
                        this.deleted[id] = otherDeleted
                    }
                } else if (this.lastUpdate(id) <= otherDeleted) { // tie goes to deletion
                    delete(id, otherDeleted)
                }
            }
            for ((id, otherTask) in other.tasks) {
                val ourDeleted = this.deleted[id]
                if (ourDeleted == null) {
                    val ourTask = this.tasks[id]
                    if (ourTask == null) { // new task from other
                        this.tasks[id] = otherTask.copy()
                        this.updates[id] = other.updates.getValue(id).toMutableMap()
                    } else { // task exists in both; update individual fields
                        updateTask(id, ourTask = ourTask, otherTask = otherTask)
                    }
                } else if (other.lastUpdate(id) > ourDeleted) { // tie goes to deletion
                    // task was deleted here, but updated more recently in other
                    this.deleted -= id
                    this.tasks[id] = otherTask.copy()
                    this.updates[id] = other.updates.getValue(id).toMutableMap()
                }
                // else: the task was most recently deleted
            }

            this.order.clear()
            newOrder.filterTo(this.order) { it in this.tasks }
        }
        // ensure we have the most recent loc timestamps
        for ((taskId, updates) in this.updates) {
            val otherLoc = other.updates[taskId]?.get("loc")
            if (otherLoc != null && otherLoc > updates.getValue("loc")) {
                updates["loc"] = otherLoc
            }
        }
    }

    /**
     * Compares location timestamps with another Dataset, for given id(s)
     */
    private fun compareLocs(ourId: TaskId, other: Dataset, theirId: TaskId = ourId): Int =
        nullsFirst<Timestamp>().compare(
            this.updates[ourId]?.get("loc"),
            other.updates[theirId]?.get("loc")
        )

    /**
     * Compares all location timestamps to determine which is overall a "newer" ordering
     */
    private fun compareLocs(other: Dataset): Int {
        val ourLocs = ArrayDeque<Timestamp>()
        this.updates.values.mapNotNullTo(ourLocs) { it["loc"] }
        ourLocs.sort()
        val theirLocs = ArrayDeque<Timestamp>()
        other.updates.values.mapNotNullTo(theirLocs) { it["loc"] }
        theirLocs.sort()
        while (true) {
            if (ourLocs.isEmpty()) {
                return if (theirLocs.isEmpty()) 0 else -1
            } else if (theirLocs.isEmpty()) {
                return 1
            } else {
                val cmp = ourLocs.last().compareTo(theirLocs.last())
                if (cmp != 0) return cmp
                else {
                    ourLocs.removeLast()
                    theirLocs.removeLast()
                }
            }
        }
    }

    /**
     * Returns if the other Dataset has a newer location for their taskId
     */
    private fun isTheirLocNewer(taskId: TaskId, other: Dataset): Boolean =
        compareLocs(taskId, other, taskId) < 0

    /**
     * Returns an order that's merged from the two Datasets,
     * resolving conflicts based on which positions are newer.
     * Does not modify either Dataset.
     */
    internal fun mergedOrder(other: Dataset): List<TaskId> {
        // Prepare queues of the items whose locations we trust,
        // because they weren't moved more recently in the other.
        // Ones whose locations have equal times stay in both queues.
        // Add in reverse order for more efficient stack pop later.
        val fromUs = ArrayDeque<TaskId>()
        this.order.reversed().filterTo(fromUs) { !this.isTheirLocNewer(it, other) }
        val fromThem = ArrayDeque<TaskId>()
        other.order.reversed().filterTo(fromThem) { !other.isTheirLocNewer(it, this) }
        val newOrder = mutableListOf<TaskId>()
        // comparison of the overall ordering, to use when we have no good way to choose
        val tiebreakerComparison: Int by lazy {
            this.compareLocs(other) orCompare {
                (compareBy<Dataset> { d -> d.order.joinToString(",") { it.string } })
                    .compare(this, other)
            }
        }

        while (true) {
            // Iteratively pop the item from the front of each order with the
            // most recently updated location.
            val ourId = fromUs.lastOrNull()
            val theirId = fromThem.lastOrNull()
            if (ourId == null) {
                if (theirId == null) {  // done
                    return newOrder
                } else {  // add rest of fromThem
                    fromThem.removeLast()
                    if (theirId !in newOrder) {
                        newOrder += theirId
                    }
                }
            } else if (theirId == null) {  // add rest of fromUs
                fromUs.removeLast()
                if (ourId !in newOrder) {
                    newOrder += ourId
                }
            } else if (ourId in newOrder) {  // skip duplicate
                fromUs.removeLast()
            } else if (theirId in newOrder) {  // skip duplicate
                fromThem.removeLast()
            } else if (ourId == theirId) {
                newOrder += fromUs.removeLast()
                fromThem.removeLast()
            } else {
                val whose = if ((this.compareLocs(ourId, other, theirId)
                            orCompare { tiebreakerComparison }) > 0
                ) {
                    fromUs
                } else fromThem
                newOrder += whose.removeLast()
            }
        }
    }

    fun clear() {
        this.tasks.clear()
        this.order.clear()
        this.updates.clear()
        this.deleted.clear()
    }

    override fun toString(): String = Json.encodeToString(this)

    internal fun requireValid() {
        require(tasks.size == updates.size)
        require(tasks.size == order.size)
        for (id in deleted.keys) {
            require(id.valid)
            require(id !in tasks)
            require(id !in order)
            require(id !in updates)
        }
        for ((id, task) in tasks) {
            require(id.valid)
            require(id == task.id)
            require(id in order)
            val updates = this.updates[id]
            require(updates != null)
            require("name" in updates)
            require("loc" in updates)
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

private inline infix fun Int.orCompare(comparison: () -> Int) =
    if (this != 0) this else comparison()
