package net.getzit.repti

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

abstract class MainActivityTaskTester : MainActivityTester() {
    @Test
    fun testCreateNewTaskFromFAB() {
        val taskName = "the name"
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_create_new_task) and isNotInMenu()).performClick()
                onNode(isDialog()).assertIsDisplayed()
                onNode(hasAnyAncestor(isDialog()) and isFocused()).performTextInput(taskName)
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_create)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                waitForIdle()
            }
            val tasks = TaskRepository.instance.tasks.value!!
            assertEquals(1, tasks.size)
            assertEquals(taskName, tasks.first().name)
        }
    }

    @Test
    fun testCreateNewTaskFromMenu() {
        val taskName = "from the menu!"
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_menu)).performClick()
                onNode(isButton(R.string.cmd_create_new_task) and isInMenu()).performClick()
                onNode(isDialog()).assertIsDisplayed()
                onNode(hasAnyAncestor(isDialog()) and isFocused()).performTextInput(taskName)
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_create)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                waitForIdle()
            }
            val tasks = TaskRepository.instance.tasks.value!!
            assertEquals(1, tasks.size)
            assertEquals(taskName, tasks.first().name)
        }
    }

    @Test
    fun testCreateNewTaskTrimsWhitespace() {
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_create_new_task) and isNotInMenu()).performClick()
                onNode(isDialog()).assertIsDisplayed()
                onNode(hasAnyAncestor(isDialog()) and isFocused()).performTextInput("  name   ")
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_create)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                waitForIdle()
            }
            val tasks = TaskRepository.instance.tasks.value!!
            assertEquals(1, tasks.size)
            assertEquals("name", tasks.first().name)
        }
    }

    @Test
    fun testCancelNewTask(): Unit = inActivity {
        with(composeRule) {
            onNode(isButton(R.string.cmd_create_new_task) and isNotInMenu()).performClick()
            onNode(isDialog()).assertIsDisplayed()
            onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_cancel)).performClick()
            onNode(isDialog()).assertIsNotDisplayed()
        }
    }

    @Test
    fun testRefuseCreateUnnamedTask() {
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_create_new_task) and isNotInMenu()).performClick()
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_create)).performClick()
                waitForIdle()
            }
            assertEquals(emptyList<Task>(), TaskRepository.instance.tasks.value)
        }
    }

    @Test
    fun testRefuseCreateTaskNameCleared() {
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_create_new_task) and isNotInMenu()).performClick()
                onNode(hasAnyAncestor(isDialog()) and isFocused()).performTextInput("blah blah")
                onNode(hasAnyAncestor(isDialog()) and isFocused()).performTextClearance()
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_create)).performClick()
                waitForIdle()
            }
            assertEquals(emptyList<Task>(), TaskRepository.instance.tasks.value)
        }
    }

    @Test
    fun testShowsTask() {
        val taskName = "this is the task that never ends"
        runBlocking {
            TaskRepository.instance.newTask(taskName)
        }
        inActivity {
            composeRule.onNodeWithText(taskName).assertIsDisplayed()
        }
    }

    @Test
    fun testOpenDetailsCard() {
        val taskName = "task for details"
        runBlocking {
            with(TaskRepository.instance) {
                newTask(taskName)
            }
        }
        inActivity {
            selectTaskByName(taskName)
            assertDetailsCardVisible(taskName)
        }
    }

    @Test
    fun testClearDayDone() {
        val taskName = "my task"
        val taskId = runBlocking {
            with(TaskRepository.instance) {
                val task = newTask(taskName)
                update(task.copy(done = Day.today()))
                task.id
            }
        }
        inActivity {
            selectTaskByName(taskName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_clear)).performClick()
                waitForIdle()
            }
            assertEquals(null, runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
        }
    }

    @Test
    fun testSetDoneTodayInDetails() {
        val taskName = "TASK"
        val taskId = runBlocking {
            TaskRepository.instance.newTask(taskName).id
        }
        inActivity {
            selectTaskByName(taskName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_done_today)).performClick()
                waitForIdle()
            }
            assertEquals(
                Day.today() as Day?,
                runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
        }
    }

    @Test
    fun testIncDayDone() {
        val taskName = "task goes up"
        val origDone = Day.today().minusDays(100)
        val taskId = runBlocking {
            with(TaskRepository.instance) {
                val task = newTask(taskName)
                update(task.copy(done = origDone))
                task.id
            }
        }
        inActivity {
            selectTaskByName(taskName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_done_next_day)).performClick()
                waitForIdle()
            }
            assertEquals(
                origDone.plusDays(1) as Day?,
                runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
        }
    }

    @Test
    fun testDecDayDone() {
        val taskName = "task goes down"
        val origDone = Day.today().minusDays(100)
        val taskId = runBlocking {
            with(TaskRepository.instance) {
                val task = newTask(taskName)
                update(task.copy(done = origDone))
                task.id
            }
        }
        inActivity {
            selectTaskByName(taskName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_done_previous_day)).performClick()
                waitForIdle()
            }
            assertEquals(
                origDone.minusDays(1) as Day?,
                runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
        }
    }

    @Test
    fun testDeleteTask() {
        val taskName = "task to delete"
        val taskId = runBlocking {
            TaskRepository.instance.newTask(taskName).id
        }
        inActivity {
            selectTaskByName(taskName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_delete)).performClick()
                onNode(isDialog()).assertIsDisplayed()
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_delete)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                assertNoDetailsCardVisible()
                onNode(isTaskItemByName(taskName), true).assertDoesNotExist()
                waitForIdle()
            }
            assertNull(runBlocking { TaskRepository.instance.getTask(taskId) })
        }
    }

    @Test
    fun testCancelDeleteTask() {
        val taskName = "task to delete"
        val task = runBlocking {
            TaskRepository.instance.newTask(taskName)
        }
        inActivity {
            selectTaskByName(taskName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_delete)).performClick()
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_cancel)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                assertDetailsCardVisible(taskName)
                getTaskItemByName(taskName).assertExists()
                waitForIdle()
            }
            assertEquals(task, runBlocking { TaskRepository.instance.getTask(task.id) })
        }
    }

    // TODO appears to have spurious errors
    @Test
    fun testRenameTask() {
        val origName = "task one"
        val newName = "task two"
        val task = runBlocking {
            TaskRepository.instance.newTask(origName)
        }
        inActivity {
            selectTaskByName(origName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_edit_task)).performClick()
                onNode(hasAnyAncestor(isDialog()) and hasContentDescriptionExactly(getString(R.string.lbl_name))).run {
                    assertTextEquals(getString(R.string.lbl_name), origName)
                    performTextClearance()
                    performTextInput(newName)
                }
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_save)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                assertDetailsCardVisible(newName)   // rarely, this is still showing the old name. why?
                getTaskItemByName(newName).assertExists()
                waitForIdle()
            }
            assertEquals(
                task.copy(name = newName),
                runBlocking { TaskRepository.instance.getTask(task.id) })
        }
    }

    @Test
    fun testCancelRenameTask() {
        val origName = "task one"
        val newName = "task two"
        val task = runBlocking {
            TaskRepository.instance.newTask(origName)
        }
        inActivity {
            selectTaskByName(origName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_edit_task)).performClick()
                onNode(hasAnyAncestor(isDialog()) and hasContentDescriptionExactly(getString(R.string.lbl_name))).run {
                    performTextClearance()
                    performTextInput(newName)
                }
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_cancel)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                assertDetailsCardVisible(origName)
                getTaskItemByName(origName).assertExists()
                waitForIdle()
            }
            assertEquals(task, runBlocking { TaskRepository.instance.getTask(task.id) })
        }
    }

    @Test
    fun testRenameTaskTrimsWhitespace() {
        val origName = "task one"
        runBlocking {
            TaskRepository.instance.newTask(origName)
        }
        inActivity {
            selectTaskByName(origName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_edit_task)).performClick()
                onNode(hasAnyAncestor(isDialog()) and hasContentDescriptionExactly(getString(R.string.lbl_name))).run {
                    performTextClearance()
                    performTextInput("   name  ")
                }
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_save)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
                waitForIdle()
            }
            val tasks = TaskRepository.instance.tasks.value!!
            assertEquals(1, tasks.size)
            assertEquals("name", tasks.first().name)
        }
    }

    @Test
    fun testRefuseRenameTaskEmpty() {
        val origName = "don't erase me"
        runBlocking {
            TaskRepository.instance.newTask(origName)
        }
        inActivity {
            selectTaskByName(origName)
            with(composeRule) {
                onNode(hasAnyAncestor(isDetailsCard()) and isButton(R.string.cmd_edit_task)).performClick()
                onNode(hasAnyAncestor(isDialog()) and hasContentDescriptionExactly(getString(R.string.lbl_name))).run {
                    performTextClearance()
                }
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_save)).performClick()
                getTaskItemByName(origName).assertExists()
                waitForIdle()
            }
            val tasks = TaskRepository.instance.tasks.value!!
            assertEquals(1, tasks.size)
            assertEquals(origName, tasks.first().name)
        }
    }

    @Test
    fun testSetDoneTodayWithSwipe() {
        val taskName = "swipe me"
        val taskId = runBlocking {
            TaskRepository.instance.newTask(taskName).id
        }
        inActivity {
            getTaskItemByName(taskName).performTouchInput { swipeRight() }
            composeRule.waitForIdle()
            assertEquals(
                Day.today() as Day?,
                runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
        }
    }
}
