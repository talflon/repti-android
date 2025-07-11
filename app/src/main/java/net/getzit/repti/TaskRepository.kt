// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TaskRepository(
    private val localSource: Storage<Dataset>,
    private val externalScope: CoroutineScope
) {
    private val datasetMutex = Mutex()

    private var dataset: Dataset? = null

    private val _tasks: MutableStateFlow<List<Task>?> = MutableStateFlow(null)
    val tasks: StateFlow<List<Task>?> get() = _tasks

    private fun onDatasetUpdate(dataset: Dataset?) {
        _tasks.value = dataset?.allTasks?.toList()
    }

    private suspend inline fun <T> withDataset(crossinline run: suspend (Dataset) -> T): T =
        externalScope.async {
            datasetMutex.withLock {
                run(dataset ?: localSource.load().also {
                    dataset = it
                    onDatasetUpdate(it)
                })
            }
        }.await()

    private suspend inline fun <T> modifyDataset(crossinline run: suspend (Dataset) -> T): T =
        withDataset {
            run(it).also {
                dataset?.apply { localSource.save(this) }
                onDatasetUpdate(dataset)
            }
        }

    suspend fun <T> editDataset(run: suspend (Dataset) -> T): T = modifyDataset(run)

    suspend fun ensureLoaded() {
        withDataset { }
    }

    suspend fun ensureSaved() {
        localSource.flush()
    }

    /**
     * Creates a new, not-done [Task].
     *
     * @param name the name to be set
     */
    suspend fun newTask(name: String): Task = modifyDataset { it.newTask(name) }

    /**
     * Deletes a task.
     *
     * @param taskId the [TaskId] of the task to be deleted
     */
    suspend fun delete(taskId: TaskId) {
        modifyDataset { it.delete(taskId) }
    }

    /**
     * Deletes a task.
     *
     * @param task the [Task] to be deleted, which must belong to this repository.
     */
    suspend fun delete(task: Task) {
        delete(task.id)
    }

    suspend fun update(task: Task) {
        modifyDataset { it.update(task) }
    }

    suspend fun getTask(id: TaskId) = withDataset { it.getTask(id) }

    suspend fun getBackup(): String = withDataset { it.toString() }

    suspend fun replaceWithBackup(backup: String) {
        val backupDataset = Dataset.fromString(backup)
        modifyDataset {
            this.dataset = backupDataset
        }
    }

    suspend fun mergeFromBackup(backup: String) {
        val backupDataset = Dataset.fromString(backup)
        modifyDataset {
            it.updateFrom(backupDataset)
        }
    }

    companion object {
        const val BACKUP_MIME_TYPE = "application/x.repti.backup+json"
        lateinit var instance: TaskRepository

        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(SupervisorJob()),
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ) = TaskRepository(
            localSource = DelayedSaveStorage(
                underlying = DatasetJsonStorage(
                    LocalFileStorage.forPath(
                        path = context.getString(R.string.path_dataset_file),
                        context = context,
                        ioDispatcher = ioDispatcher,
                    )
                ),
                delay = 15.seconds,
                scope = scope,
            ),
            externalScope = scope,
        )

        fun create(
            storage: Storage<String>,
            scope: CoroutineScope = CoroutineScope(SupervisorJob()),
        ) = TaskRepository(
            localSource = DatasetJsonStorage(storage),
            externalScope = scope,
        )
    }
}

interface Storage<T> {
    suspend fun load(): T
    suspend fun save(value: T)
    suspend fun flush() {}
}

class DatasetJsonStorage(private val stringStorage: Storage<String>) : Storage<Dataset> {
    override suspend fun load(): Dataset {
        val encoded = stringStorage.load()
        return if (encoded.isEmpty()) {
            Dataset()
        } else {
            Json.decodeFromString(encoded)
        }
    }

    override suspend fun save(value: Dataset) {
        stringStorage.save(Json.encodeToString(value))
    }

    override suspend fun flush() = stringStorage.flush()
}

class LocalFileStorage(
    val file: File,
    private val ioDispatcher: CoroutineDispatcher,
) : Storage<String> {
    private val mutex = Mutex()

    override suspend fun load(): String = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                file.bufferedReader().readText()
            } catch (e: FileNotFoundException) {
                ""
            }
        }
    }

    override suspend fun save(value: String) = withContext(ioDispatcher) {
        mutex.withLock {
            file.bufferedWriter().use { it.write(value) }
        }
    }

    companion object {
        fun forPath(
            path: String,
            context: Context,
            ioDispatcher: CoroutineDispatcher
        ) = LocalFileStorage(File(context.filesDir, path), ioDispatcher)
    }
}

class VarStorage<T>(var value: T) : Storage<T> {
    override suspend fun load(): T = value

    override suspend fun save(value: T) {
        this.value = value
    }
}

class DelayedSaveStorage<T>(
    private val underlying: Storage<T>,
    delay: Duration,
    scope: CoroutineScope,
) : Storage<T> {
    private val timeTrigger =
        IdleTimeoutTrigger<T>(timeoutMillis = delay.inWholeMilliseconds, scope = scope) {
            underlying.save(it)
            Log.d("DelayedSaveStorage", "Value saved")
        }

    override suspend fun load() = underlying.load()

    override suspend fun save(value: T) {
        timeTrigger.trigger(value)
    }

    override suspend fun flush() {
        timeTrigger.runNowIfTriggered()
    }
}
