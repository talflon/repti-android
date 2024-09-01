package net.getzit.repti

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule

abstract class MainActivityTester {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    protected val context: Context
        get() = ApplicationProvider.getApplicationContext<MainActivity>()

    protected fun getString(@StringRes id: Int) = context.getString(id)

    protected inline fun <T> inActivity(f: (ActivityScenario<MainActivity>) -> T) =
        ActivityScenario.launch(MainActivity::class.java).use(f)

    @Before
    fun clearTasks() {
        TaskRepository.instance = TaskRepository.create(VarStorage(""))
    }

    protected fun isButton(@StringRes id: Int): SemanticsMatcher {
        val cmd = getString(id)
        return hasClickAction() and (hasContentDescriptionExactly(cmd) or hasTextExactly(cmd))
    }

    protected fun isInMenu() =
        hasAnyAncestor(hasContentDescriptionExactly(getString(R.string.lbl_menu)))

    protected fun isNotInMenu() = isInMenu().not()

    protected fun isTaskItemByName(taskName: String): SemanticsMatcher =
        isSelectable() and hasAnyChild(hasTextExactly(taskName))

    protected fun getTaskItemByName(taskName: String): SemanticsNodeInteraction =
        composeRule.onNode(isTaskItemByName(taskName), useUnmergedTree = true)

    protected fun selectTaskByName(taskName: String) {
        getTaskItemByName(taskName).let {
            it.performClick()
            it.assertIsSelected()
        }
    }

    protected fun isDetailsCard() =
        hasContentDescriptionExactly(getString(R.string.lbl_more_information_for_task))

    protected fun assertDetailsCardVisible(taskName: String) {
        composeRule.onNode(isDetailsCard()).let {
            it.assertIsDisplayed()
            it.onChildren().assertAny(hasTextExactly(taskName))
        }
    }

    protected fun assertNoDetailsCardVisible() {
        composeRule.onNode(isDetailsCard()).assertIsNotDisplayed()
    }
}