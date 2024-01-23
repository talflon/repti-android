package net.getzit.repti

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    lateinit var activityController: ActivityController<MainActivity>

    @Before
    fun startActivity() {
        activityController = Robolectric.buildActivity(MainActivity::class.java).also { it.setup() }
    }

    @After
    fun stopActivity() {
        if (::activityController.isInitialized) {
            activityController.close()
        }
    }

    private fun getString(id: Int) = activityController.get().baseContext.getString(id)

    @Test
    fun testCreateNewTask() = composeRule.run {
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

    @Test
    fun testCancelNewTask() = composeRule.run {
        onNode(hasClickAction() and hasContentDescriptionExactly(getString(R.string.cmd_create_new_task))).performClick()
        with(onNode(isDialog())) {
            assertExists()
            onNode(hasClickAction() and hasTextExactly(getString(R.string.cmd_cancel))).performClick()
            assertDoesNotExist()
        }
    }
}
