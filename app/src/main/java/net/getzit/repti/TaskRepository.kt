package net.getzit.repti

import android.content.Context
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

class TaskRepository(
    private val localSource: Storage<Dataset>,
    private val externalScope: CoroutineScope
) {
    private val datasetMutex = Mutex()

    private var dataset: Dataset? = null

    private val _tasks: MutableStateFlow<List<Task>?> = MutableStateFlow(null)
    val tasks: StateFlow<List<Task>?> get() = _tasks

    private suspend inline fun <T> withDataset(crossinline run: suspend (Dataset) -> T): T =
        externalScope.async {
            datasetMutex.withLock {
                run(dataset ?: localSource.load().also {
                    dataset = it
                    _tasks.value = it.allTasks.toList()
                })
            }
        }.await()

    private suspend inline fun <T> modifyDataset(crossinline run: suspend (Dataset) -> T): T =
        withDataset { dataset ->
            run(dataset).also {
                localSource.save(dataset)
                _tasks.value = dataset.allTasks.toList()
            }
        }

    suspend fun <T> editDataset(run: suspend (Dataset) -> T): T = modifyDataset(run)

    suspend fun ensureLoaded() {
        withDataset { }
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

    companion object {
        lateinit var instance: TaskRepository

        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(SupervisorJob()),
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ) =
            TaskRepository(
                localSource = DatasetJsonStorage(
                    LocalFileStorage.forPath(
                        path = context.getString(R.string.path_dataset_file),
                        context = context,
                        ioDispatcher = ioDispatcher,
                    )
                ),
                externalScope = scope,
            )
    }
}

interface Storage<T> {
    suspend fun load(): T
    suspend fun save(value: T)
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
