package net.getzit.repti

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRepositoryTest {
    @Test
    fun testEnsureLoaded() = runTest {
        val dataset = Dataset()
        val task = dataset.newTask("test")
        val repository = TaskRepository(MockStorage(dataset), this)
        assertEquals(null, repository.tasks.first())
        repository.tasks.drop(1)
        repository.ensureLoaded()
        assertEquals(listOf(task), repository.tasks.first())
    }

    @Test
    fun testNewTask() = runTest {
        val datasetStorage = MockStorage(Dataset())
        val repository = TaskRepository(datasetStorage, this)
        val task = repository.newTask("test")
        assertEquals(listOf(task), repository.tasks.value)
        assertEquals(task, datasetStorage.value.getTask(task.id))
    }

    @Test
    fun testDeleteTask() = runTest {
        val datasetStorage = MockStorage(Dataset())
        val repository = TaskRepository(datasetStorage, this)
        repository.delete(repository.newTask("delete me"))
        assertEquals(emptyList<Task>(), repository.tasks.value)
        assertEquals(emptyList<Task>(), datasetStorage.value.allTasks.toList())
    }

    @Test
    fun testUpdateTask() = runTest {
        val datasetStorage = MockStorage(Dataset())
        val repository = TaskRepository(datasetStorage, this)
        val taskBefore = repository.newTask("before")
        val taskAfter = taskBefore.copy(name = "after")
        repository.update(taskAfter)
        assertEquals(listOf(taskAfter), repository.tasks.value)
        assertEquals(taskAfter, datasetStorage.value.getTask(taskAfter.id))
    }
}
