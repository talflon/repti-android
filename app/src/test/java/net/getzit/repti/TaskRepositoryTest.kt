// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
        val repository = TaskRepository(MockDatasetStorage(dataset), this)
        assertEquals(null, repository.tasks.first())
        repository.tasks.drop(1)
        repository.ensureLoaded()
        assertEquals(listOf(task), repository.tasks.first())
    }

    @Test
    fun testNewTask() = runTest {
        val datasetStorage = MockDatasetStorage()
        val repository = TaskRepository(datasetStorage, this)
        val task = repository.newTask("test")
        assertEquals(listOf(task), repository.tasks.value)
        assertEquals(task, datasetStorage.load().getTask(task.id))
    }

    @Test
    fun testDeleteTask() = runTest {
        val datasetStorage = MockDatasetStorage()
        val repository = TaskRepository(datasetStorage, this)
        repository.delete(repository.newTask("delete me"))
        assertEquals(emptyList<Task>(), repository.tasks.value)
        assertEquals(emptyList<Task>(), datasetStorage.load().allTasks.toList())
    }

    @Test
    fun testUpdateTask() = runTest {
        val datasetStorage = MockDatasetStorage()
        val repository = TaskRepository(datasetStorage, this)
        val taskBefore = repository.newTask("before")
        val taskAfter = taskBefore.copy(name = "after")
        repository.update(taskAfter)
        assertEquals(listOf(taskAfter), repository.tasks.value)
        assertEquals(taskAfter, datasetStorage.load().getTask(taskAfter.id))
    }

    @Test
    fun testReplaceWithBackupSingleTask() = runTest {
        val srcRepository = TaskRepository(MockDatasetStorage(), this)
        val datasetStorage = MockDatasetStorage()
        val repository = TaskRepository(datasetStorage, this)
        val task = srcRepository.newTask("test")
        repository.replaceWithBackup(srcRepository.getBackup())
        assertEquals(listOf(task), repository.tasks.value)
        assertEquals(task, datasetStorage.load().getTask(task.id))
    }

    @Test
    fun testMergeFromBackupSingleTask() = runTest {
        val srcRepository = TaskRepository(MockDatasetStorage(), this)
        val datasetStorage = MockDatasetStorage()
        val repository = TaskRepository(datasetStorage, this)
        val task = srcRepository.newTask("test")
        repository.mergeFromBackup(srcRepository.getBackup())
        assertEquals(listOf(task), repository.tasks.value)
        assertEquals(task, datasetStorage.load().getTask(task.id))
    }
}
