package net.getzit.repti.test

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import net.getzit.repti.MainActivity
import net.getzit.repti.R
import net.getzit.repti.TaskRepository
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

abstract class MainActivityTester {
    @get:Rule(order = 0)
    val composeRule = createComposeRule()

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext<MainActivity>()

    protected fun getString(@StringRes id: Int) = context.getString(id)

    @Test
    fun testCreateNewTask() = with(composeRule) {
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
        Assert.assertEquals(1, tasks.size)
        Assert.assertEquals(taskName, tasks.first().name)
    }

    @Test
    fun testCancelNewTask() = with(composeRule) {
        onNode(hasClickAction() and hasContentDescriptionExactly(getString(R.string.cmd_create_new_task))).performClick()
        with(onNode(isDialog())) {
            assertExists()
            onNode(hasClickAction() and hasTextExactly(getString(R.string.cmd_cancel))).performClick()
            assertDoesNotExist()
        }
    }
}