package net.getzit.repti.ui

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.getzit.repti.R
import net.getzit.repti.TaskRepository
import java.io.IOException

@Composable
fun rememberSaveBackup(): () -> Unit {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(CreateDocument(TaskRepository.BACKUP_MIME_TYPE)) { uri: Uri? ->
            if (uri != null) scope.launchTaskRepository {
                try {
                    Log.d("SaveBackup", "saving backup to $uri")
                    val backup = getBackup()
                    withContext(Dispatchers.IO) {
                        val outputStream = context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("File provider recently crashed")
                        Log.d("SaveBackup", "Opened output stream")
                        outputStream.writer().use {
                            it.write(backup)
                        }
                        Log.d("SaveBackup", "Written and closed file")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_backup_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: IOException) {
                    Log.e("SaveBackup", "Error saving backup", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_backup_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    val defaultFilename = stringResource(R.string.default_backup_filename)
    return { launcher.launch(defaultFilename) }
}