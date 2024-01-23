package net.getzit.repti

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun getString(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun ensureCreated() {
        runBlocking { composeRule.awaitIdle() }
    }

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
