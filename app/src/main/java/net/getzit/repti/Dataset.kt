package net.getzit.repti

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.Math.toIntExact
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * A set of tasks, with their information and synchronization information.
 */
@Serializable(with = DatasetSerializer::class)
class Dataset {
    internal val tasksById = mutableMapOf<TaskId, Task>()
    internal val deletedTasks = mutableMapOf<TaskId, Timestamp>()

    /**
     * [Clock] to be used for update times, defaults to [Clock.systemDefaultZone]
     */
    @Transient
    var clock: Clock = Clock.systemDefaultZone()

    /**
     * All of the tasks, as an unmutable [Collection].
     *
     * The individual [Task] objects can be mutated.
     */
    val allTasks: Collection<Task>
        get() = tasksById.values

    /**
     * Creates a new, not-done [Task].
     *
     * @param name the name to be set
     */
    fun newTask(name: String): Task {
        var id: TaskId
        do {
            id = TaskId.random()
        } while (id in tasksById || id in deletedTasks)
        val task = Task(id, this)
        task.name = name
        tasksById[id] = task
        return task
    }

    /**
     * Deletes a task.
     *
     * @param task the [Task] to be deleted, which must belong to this dataset.
     */
    internal fun delete(task: Task) {
        tasksById.remove(task.id).also { assert(it == task) }
        deletedTasks[task.id] = Timestamp.now(clock)
    }

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
        for ((id, otherDeleted) in other.deletedTasks) {
            val task = tasksById[id]
            if (task == null) {
                // ensure we have the most recent deletion timestamp
                val ourDeleted = deletedTasks[id]
                if (ourDeleted == null || ourDeleted < otherDeleted) {
                    deletedTasks[id] = otherDeleted
                }
            } else if (task.lastUpdate <= otherDeleted) { // tie goes to deletion
                tasksById.remove(id)
                deletedTasks[id] = otherDeleted
            }
        }
        // Check for new and updated tasks
        for ((id, otherTask) in other.tasksById) {
            val ourDeleted = deletedTasks[id]
            if (ourDeleted == null) {
                val ourTask = tasksById[id]
                if (ourTask == null) {
                    tasksById[id] = otherTask.copy()
                } else {
                    ourTask.updateFrom(otherTask)
                }
            } else if (otherTask.lastUpdate > ourDeleted) { // tie goes to deletion
                deletedTasks -= id
                tasksById[id] = otherTask.copy()
            }
        }
    }

    /**
     * Creates a deep copy of this.
     */
    fun copy(): Dataset {
        val newCopy = Dataset()
        newCopy.deletedTasks.putAll(this.deletedTasks)
        for ((id, task) in this.tasksById) {
            newCopy.tasksById[id] = task.copy(newCopy)
        }
        return newCopy
    }
}

@Serializable
@SerialName("Dataset")
private class DatasetSurrogate(
    val tasks: Map<TaskId, TaskSurrogate>,
    val updates: Map<TaskId, Map<String, Timestamp>>,
    val deleted: Map<TaskId, Timestamp>
) {
    init {
        require((tasks.keys intersect deleted.keys).isEmpty())
    }
}

/**
 * Serializes a [Dataset].
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
 * - `(task id)` is lower-case alphanumeric of length 7
 * - `(date)` is the number of days since 2021-01-01
 * - `(timestamp)` is the number of seconds since 2021-01-01
 */
object DatasetSerializer : KSerializer<Dataset> {
    override val descriptor: SerialDescriptor = DatasetSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Dataset) {
        encoder.encodeSerializableValue(
            DatasetSurrogate.serializer(),
            DatasetSurrogate(
                tasks = value.tasksById.mapValues { (_, t) -> TaskSurrogate.from(t) },
                updates = value.tasksById.mapValues { (_, t) -> t.timestamps },
                deleted = value.deletedTasks,
            )
        )
    }

    override fun deserialize(decoder: Decoder): Dataset {
        val datasetSurrogate = decoder.decodeSerializableValue(DatasetSurrogate.serializer())
        val dataset = Dataset()
        for ((id, taskSurrogate) in datasetSurrogate.tasks) {
            dataset.tasksById[id] = taskSurrogate.toTask(dataset, datasetSurrogate.updates[id]!!)
        }
        dataset.deletedTasks.putAll(datasetSurrogate.deleted)
        return dataset
    }
}

/**
 * Identifies a [Task], even if the task's name changes.
 */
@Serializable
@JvmInline
value class TaskId(val string: String) {
    companion object {
        const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
        const val LENGTH = 7

        fun random(r: Random = Random.Default) = TaskId(
            buildString(LENGTH) {
                for (i in 1..LENGTH) {
                    append(ALPHABET[r.nextInt(ALPHABET.length)])
                }
            })
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
        const val EPOCH: Long = 1609459200

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

    companion object {
        /**
         * 2021-01-01, in days since the regular epoch (1970-01-01)
         */
        const val EPOCH: Long = 18628

        fun today(clock: Clock = Clock.systemDefaultZone()) = of(LocalDate.now(clock))

        fun of(date: LocalDate) = Day(toIntExact(date.toEpochDay() - EPOCH))
    }
}

@Serializable
@SerialName("Task")
private class TaskSurrogate(val id: TaskId, val name: String, val done: Day?) {
    fun toTask(dataset: Dataset, timestamps: Map<String, Timestamp>) = Task(id, dataset).also {
        it._name = name
        it._done = done
        it.timestamps.putAll(timestamps)
    }

    companion object {
        fun from(task: Task) = TaskSurrogate(
            id = task.id,
            name = task.name,
            done = task.done,
        )
    }
}

/**
 * A task that can be completed, inside a [Dataset].
 *
 * @property id the task's [TaskId]
 * @property dataset the [Dataset]
 * @property name the task's name
 * @property done the [Day] in which the task was done, or [null] if not yet done
 */
class Task internal constructor(val id: TaskId, val dataset: Dataset) {
    internal var _name: String = ""
    var name: String
        get() = _name
        set(value) {
            _name = value
            timestamps["name"] = now()
        }

    internal var _done: Day? = null
    var done: Day?
        get() = _done
        set(value) {
            _done = value
            timestamps["done"] = now()
        }

    /**
     * Update timestamps for each property
     */
    internal val timestamps = mutableMapOf<String, Timestamp>()

    /**
     * The most recent update timestamp
     */
    val lastUpdate: Timestamp get() = timestamps.values.maxOrNull()!!

    /**
     * Set [done] to today.
     */
    fun doit() {
        done = Day.today(dataset.clock)
    }

    /**
     * Delete this task from its dataset.
     */
    fun delete() {
        dataset.delete(this)
    }

    private inline fun now() = Timestamp.now(dataset.clock)

    /**
     * Helper function for [updateFrom].
     *
     * If the update timestamp for `key` in `other` is newer than ours, run `setIt()` to use theirs.
     * If they're the same, check `tiebreaker()`. If `tiebreaker()` returns `true`, then we use theirs.
     */
    private inline fun ifHasNewerUpdate(
        other: Task,
        key: String,
        tiebreaker: () -> Boolean,
        setIt: () -> Unit,
    ) {
        other.timestamps[key]?.let { otherTimestamp ->
            val ourTimestamp = timestamps[key]
            if (ourTimestamp == null || ourTimestamp < otherTimestamp) {
                setIt()
                timestamps[key] = otherTimestamp
            } else if (ourTimestamp == otherTimestamp && tiebreaker()) {
                setIt()
            }
        }
    }

    /**
     * Update this task with newer updates from `other`.
     */
    internal fun updateFrom(other: Task) {
        // tie goes to lower id; these are random so this is arbitrary, just to be consistent
        ifHasNewerUpdate(other, "name", { other._name > _name }) { _name = other._name }
        // tie goes to later done, with null counting as the earliest
        ifHasNewerUpdate(other, "done", { compareValues(other._done, _done) > 0 }) {
            _done = other._done
        }
    }

    /**
     * Deep copy, optionally changing the [Dataset].
     */
    internal fun copy(dataset: Dataset = this.dataset) = Task(id, dataset).also {
        it._name = this._name
        it._done = this._done
        it.timestamps.clear()
        it.timestamps.putAll(this.timestamps)
    }
}
