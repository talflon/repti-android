// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class MainActivityBackupTest : MainActivityTester() {
    @get:Rule
    val intentsRule = IntentsRule()

    private fun isSaveBackupAction(): Matcher<Intent> = allOf(
        IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT),
        IntentMatchers.hasType(TaskRepository.BACKUP_MIME_TYPE)
    )

    @Test
    fun testSaveBackup() {
        val uri = Uri.parse("content://net.getzit.repti.test/filename")
        val expected: String = runBlocking {
            val repo = TaskRepository.instance
            repo.newTask("a task")
                .copy(done = Day.today())
                .also { repo.update(it) }
            repo.getBackup()
        }
        inActivity {
            // create receiver for the write
            val saveResult = ByteArrayOutputStream()
            Shadows.shadowOf(context.contentResolver).registerOutputStream(uri, saveResult)
            // when asked to choose the file to save as, mock an immediate response to save to saveResult
            intending(isSaveBackupAction()).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK, Intent().apply { data = uri }
                )
            )
            with(composeRule) {
                onNode(isButton(R.string.cmd_menu)).performClick()
                onNode(isButton(R.string.cmd_save_backup) and isInMenu()).performClick()
            }
            intended(isSaveBackupAction())
            assertEquals(expected.trim(), saveResult.toString().trim())
        }
    }

    private fun isLoadBackupAction(): Matcher<Intent> =
        IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT)

    @Test
    fun testLoadBackupSynchronize() {
        val uri = Uri.parse("content://net.getzit.repti.test/filename")
        var oldTask: Task
        var newTask: Task
        var loadResult: String
        runBlocking {
            oldTask = TaskRepository.instance.newTask("the old task")
            val srcRepository = TaskRepository(MockDatasetStorage(), this)
            newTask = srcRepository.newTask("the new task")
            loadResult = srcRepository.getBackup()
        }
        inActivity {
            // create provider for the read
            Shadows.shadowOf(context.contentResolver)
                .registerInputStream(uri, loadResult.byteInputStream())
            // when asked to choose the file to save as, mock an immediate response to load from loadResult
            intending(isLoadBackupAction()).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK, Intent().apply { data = uri }
                )
            )
            with(composeRule) {
                onNode(isButton(R.string.cmd_menu)).performClick()
                onNode(isButton(R.string.cmd_load_backup) and isInMenu()).performClick()
                intended(isLoadBackupAction())
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_synchronize)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
            }
            TaskRepository.instance.tasks.value?.sortedBy { it.id.string }
            assertThat(TaskRepository.instance.tasks.value, containsInAnyOrder(oldTask, newTask))
        }
    }

    @Test
    fun testLoadBackupReplace() {
        val uri = Uri.parse("content://net.getzit.repti.test/filename")
        var newTask: Task
        var loadResult: String
        runBlocking {
            TaskRepository.instance.newTask("the old task")
            val srcRepository = TaskRepository(MockDatasetStorage(), this)
            newTask = srcRepository.newTask("the new task")
            loadResult = srcRepository.getBackup()
        }
        inActivity {
            // create provider for the read
            Shadows.shadowOf(context.contentResolver)
                .registerInputStream(uri, loadResult.byteInputStream())
            // when asked to choose the file to save as, mock an immediate response to load from loadResult
            intending(isLoadBackupAction()).respondWith(
                Instrumentation.ActivityResult(
                    Activity.RESULT_OK, Intent().apply { data = uri }
                )
            )
            with(composeRule) {
                onNode(isButton(R.string.cmd_menu)).performClick()
                onNode(isButton(R.string.cmd_load_backup) and isInMenu()).performClick()
                intended(isLoadBackupAction())
                onNode(hasAnyAncestor(isDialog()) and isButton(R.string.cmd_replace)).performClick()
                onNode(isDialog()).assertIsNotDisplayed()
            }
            TaskRepository.instance.tasks.value?.sortedBy { it.id.string }
            assertEquals(listOf(newTask), TaskRepository.instance.tasks.value)
        }
    }
}
