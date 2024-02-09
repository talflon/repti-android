package net.getzit.repti

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun clearTasks() {
        TaskRepository.instance = TaskRepository.create(VarStorage(""))
    }

    private fun isButton(@StringRes id: Int): SemanticsMatcher {
        val cmd = getString(id)
        return hasClickAction() and (hasContentDescriptionExactly(cmd) or hasTextExactly(cmd))
    }

    private fun SemanticsNode.isPlaced(): Boolean {
        var layoutInfo: LayoutInfo? = this.layoutInfo
        while (layoutInfo != null) {
            if (!layoutInfo.isPlaced) return false
            layoutInfo = layoutInfo.parentInfo
        }
        return true
    }

    private fun assertGone(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) {
        for (node in composeRule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes()) {
            assertTrue("A node matching [${matcher.description}] isn't gone", !node.isPlaced())
        }
    }

    @Test
    fun testCreateNewTask() {
        val taskName = "the name"
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_create_new_task)).performClick()
                onNode(isDialog()).assertIsDisplayed()
                onNode(hasAnyAncestor(isDialog()) and isFocused()).performTextInput(taskName)
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_create)).performClick()
                assertGone(isDialog())
                waitForIdle()
            }
            val tasks = TaskRepository.instance.tasks.value!!
            assertEquals(1, tasks.size)
            assertEquals(taskName, tasks.first().name)
        }
    }

    @Test
    fun testCancelNewTask() = inActivity {
        with(composeRule) {
            onNode(isButton(R.string.cmd_create_new_task)).performClick()
            onNode(isDialog()).assertIsDisplayed()
            onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_cancel)).performClick()
            assertGone(isDialog())
        }
    }

    @Test
    fun testRefuseCreateUnnamedTask() {
        inActivity {
            with(composeRule) {
                onNode(isButton(R.string.cmd_create_new_task)).performClick()
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
                onNode(isButton(R.string.cmd_create_new_task)).performClick()
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

    private fun isTaskItemByName(taskName: String): SemanticsMatcher =
        isSelectable() and hasAnyChild(hasTextExactly(taskName))

    private fun getTaskItemByName(taskName: String): SemanticsNodeInteraction =
        composeRule.onNode(isTaskItemByName(taskName), useUnmergedTree = true)

    private fun selectTaskByName(taskName: String) {
        getTaskItemByName(taskName).let {
            it.performClick()
            it.assertIsSelected()
        }
    }

    private val isDetailsCard =
        hasContentDescriptionExactly(getString(R.string.lbl_more_information_for_task))

    private fun assertDetailsCardVisible(taskName: String) {
        composeRule.onNode(isDetailsCard).let {
            it.assertIsDisplayed()
            it.onChildren().assertAny(hasTextExactly(taskName))
        }
    }

    private fun assertNoDetailsCardVisible() {
        assertGone(isDetailsCard)
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
        val taskName = "task to toggle details"
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
            assertNoDetailsCardVisible()
        }
    }

    @Test
    fun testCloseDetailsCardByCloseButton() {
        val taskName = "task to close details"
        runBlocking {
            with(TaskRepository.instance) {
                newTask(taskName)
            }
        }
        inActivity {
            with(composeRule) {
                selectTaskByName(taskName)
                onNode(hasAnyAncestor(isDetailsCard) and isButton(R.string.cmd_close)).performClick()
                assertNoDetailsCardVisible()
            }
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
                onNode(hasAnyAncestor(isDetailsCard) and isButton(R.string.cmd_clear_day_done)).performClick()
                waitForIdle()
            }
            assertEquals(null, runBlocking { TaskRepository.instance.getTask(taskId)!!.done })
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
                onNode(hasAnyAncestor(isDetailsCard) and isButton(R.string.cmd_done_next_day)).performClick()
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
                onNode(hasAnyAncestor(isDetailsCard) and isButton(R.string.cmd_done_previous_day)).performClick()
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
                onNode(hasAnyAncestor(isDetailsCard) and isButton(R.string.cmd_delete)).performClick()
                assertNoDetailsCardVisible()
                assertGone(isTaskItemByName(taskName), true)
                waitForIdle()
            }
            assertNull(runBlocking { TaskRepository.instance.getTask(taskId) })
        }
    }
}