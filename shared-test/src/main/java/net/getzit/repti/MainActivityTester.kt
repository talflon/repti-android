package net.getzit.repti

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
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
}