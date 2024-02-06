package net.getzit.repti

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

abstract class MainActivityTester {
    @get:Rule
    val composeRule = createComposeRule()

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext<MainActivity>()

    protected fun getString(@StringRes id: Int) = context.getString(id)

    protected inline fun <T> inActivity(f: (ActivityScenario<MainActivity>) -> T) =
        ActivityScenario.launch(MainActivity::class.java).use(f)

    @Before
    fun clearTasks(): Unit = runBlocking {
        TaskRepository.instance.editDataset { it.clear() }
    }

    @Test
    fun testCreateNewTask() {
        inActivity {
            with(composeRule) {
                val taskName = "the name"
                onNode(hasClickAction() and hasContentDescriptionExactly(getString(R.string.cmd_create_new_task))).performClick()
                with(onNode(isDialog())) {
                    assertExists()
                    onNode(isFocused()).performTextInput(taskName)
                    onNode(hasClickAction() and hasTextExactly(getString(R.string.cmd_create))).performClick()
                    waitUntil { TaskRepository.instance.tasks.value!!.isNotEmpty() }
                    assertDoesNotExist()
                }
                val tasks = TaskRepository.instance.tasks.value!!
                assertEquals(1, tasks.size)
                assertEquals(taskName, tasks.first().name)
            }
        }
    }

    @Test
    fun testCancelNewTask() = inActivity {
        with(composeRule) {
            onNode(hasClickAction() and hasContentDescriptionExactly(getString(R.string.cmd_create_new_task))).performClick()
            with(onNode(isDialog())) {
                assertExists()
                onNode(hasClickAction() and hasTextExactly(getString(R.string.cmd_cancel))).performClick()
                assertDoesNotExist()
            }
        }
    }

    @Test
    fun testShowsTask() {
        val taskName = "this is the task that never ends"
        runBlocking {
            TaskRepository.instance.newTask(taskName)
        }
        inActivity {
            composeRule.onNodeWithText(taskName).assertExists()
        }
    }

    private fun getTaskItemByName(taskName: String): SemanticsNodeInteraction = composeRule.onNode(
        isSelectable() and hasAnyChild(hasTextExactly(taskName)),
        useUnmergedTree = true
    )

    private fun selectTaskByName(taskName: String) {
        getTaskItemByName(taskName).let {
            it.performClick()
            it.assertIsSelected()
        }
    }

    private fun assertDetailsCardVisible(taskName: String) {
        with(composeRule) {
            onNode(hasContentDescriptionExactly(getString(R.string.lbl_more_information_for_task))).let {
                it.assertIsDisplayed()
                it.onChildren().assertAny(hasTextExactly(taskName))
            }
        }
    }

    private fun assertNoDetailsCardVisible() {
        with(composeRule) {
            onNode(hasContentDescriptionExactly(getString(R.string.lbl_more_information_for_task))).let {
                it.assertDoesNotExist()
            }
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
    fun testOpenAnotherDetailsCard() {
        val taskNames = listOf("task A", "task B", "task C")
        runBlocking {
            with(TaskRepository.instance) {
                for (name in taskNames) {
                    newTask(name)
                }
            }
        }
        inActivity {
            for (name in taskNames) {
                selectTaskByName(name)
                assertDetailsCardVisible(name)
            }
        }
    }

    @Test
    fun testCloseDetailsCardBySecondClickOnMainList() {
        val taskName = "task for details"
        runBlocking {
            with(TaskRepository.instance) {
                newTask(taskName)
            }
        }
        inActivity {
            getTaskItemByName(taskName).let {
                it.performClick()
                it.performClick()
            }
            composeRule.waitForIdle()
            assertNoDetailsCardVisible()
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
                onNode(hasContentDescriptionExactly(getString(R.string.cmd_clear_day_done))).performClick()
            }
        }
        assertEquals(null, runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
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
                onNode(hasContentDescriptionExactly(getString(R.string.cmd_done_next_day))).performClick()
            }
        }
        assertEquals(
            origDone.plusDays(1) as Day?,
            runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
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
                onNode(hasContentDescriptionExactly(getString(R.string.cmd_done_previous_day))).performClick()
            }
        }
        assertEquals(
            origDone.minusDays(1) as Day?,
            runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
    }
}