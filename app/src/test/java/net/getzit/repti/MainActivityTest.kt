package net.getzit.repti

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class MainActivityTest : MainActivityTester() {
    @get:Rule
    val intentsRule = IntentsRule()

    private fun isSaveBackupAction(): Matcher<Intent> = allOf(
        IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT),
        IntentMatchers.hasType(TaskRepository.BACKUP_MIME_TYPE)
    )

    @Test
    fun testSaveDataset() {
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
}
