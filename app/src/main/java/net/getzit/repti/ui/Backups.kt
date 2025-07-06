// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti.ui

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.getzit.repti.R
import net.getzit.repti.TaskRepository
import java.io.IOException

@Composable
fun rememberSaveBackup(): () -> Unit {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(CreateDocument(TaskRepository.BACKUP_MIME_TYPE)) { uri: Uri? ->
            if (uri != null) scope.launchTaskRepository {
                try {
                    Log.d("Backup", "saving backup to $uri")
                    val backup = getBackup()
                    withContext(Dispatchers.IO) {
                        val outputStream = ctx.contentResolver.openOutputStream(uri)
                            ?: throw IOException("File provider recently crashed")
                        Log.d("Backup", "Opened output stream")
                        outputStream.writer().use {
                            it.write(backup)
                        }
                        Log.d("Backup", "Written and closed file")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, R.string.msg_backup_saved, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: IOException) {
                    Log.e("Backup", "Error saving backup", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            ctx, R.string.msg_backup_save_error, Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    val defaultFilename = stringResource(R.string.default_backup_filename)
    return { launcher.launch(defaultFilename) }
}

@Composable
fun rememberLoadBackup(state: MutableState<String?>): () -> Unit {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var loadedBackup by state
    val launcher =
        rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
            if (uri != null) scope.launchTaskRepository {
                try {
                    Log.d("Backup", "loading backup from $uri")
                    loadedBackup = withContext(Dispatchers.IO) {
                        val inputStream = ctx.contentResolver.openInputStream(uri)
                            ?: throw IOException("File provider recently crashed")
                        Log.d("Backup", "Opened input stream")
                        val backup = inputStream.reader().use {
                            it.readText()
                        }
                        Log.d("Backup", "Read and closed file")
                        backup
                    }
                } catch (e: IOException) {
                    Log.e("Backup", "Error loading backup", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            ctx, R.string.msg_backup_load_error, Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    // We can't pass our real MIME type in because Android doesn't recognize it,
    // so let user pick from all files.
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
fun rememberLoadBackupDialogState() = rememberSaveable { mutableStateOf<String?>(null) }

@Composable
fun LoadBackupDialog(
    state: MutableState<String?>,
) {
    var loadedBackup: String? by state
    if (loadedBackup != null) {
        val scope = rememberCoroutineScope()
        val ctx = LocalContext.current

        val onDismissRequest = { loadedBackup = null }
        val onSynchronizeRequest: () -> Unit = {
            scope.launchTaskRepository {
                loadedBackup.also {
                    if (it != null) {
                        try {
                            mergeFromBackup(it)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx, R.string.msg_backup_loaded, Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: IllegalArgumentException) {
                            Log.e("Backup", "Error loading replacement backup", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx, R.string.msg_backup_load_error, Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        loadedBackup = null
                    }
                }
            }
        }
        val onReplaceRequest: () -> Unit = {
            scope.launchTaskRepository {
                loadedBackup.also {
                    if (it != null) {
                        try {
                            replaceWithBackup(it)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx, R.string.msg_backup_loaded, Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: IllegalArgumentException) {
                            Log.e("Backup", "Error merging from backup", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    ctx, R.string.msg_backup_load_error, Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        loadedBackup = null
                    }
                }
            }
        }

        Dialog(onDismissRequest = onDismissRequest) {
            val dialogPaneTitle = stringResource(R.string.ttl_load_backup)
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .semantics { paneTitle = dialogPaneTitle },
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.cmd_load_backup), style = typography.titleLarge)
                    Text(stringResource(R.string.msg_backup_load_method_q))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(onSynchronizeRequest) { Text(stringResource(R.string.cmd_synchronize)) }
                        Text(
                            stringResource(R.string.msg_backup_explain_synchronize),
                            style = typography.bodySmall
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(onReplaceRequest) { Text(stringResource(R.string.cmd_replace)) }
                        Text(
                            stringResource(R.string.msg_backup_explain_replace),
                            style = typography.bodySmall
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = stringResource(R.string.msg_warning)
                        )
                        Text(
                            stringResource(R.string.msg_warn_no_undo_plural),
                            fontWeight = FontWeight.Bold,
                            style = typography.bodyLarge
                        )
                    }
                    OutlinedButton(onDismissRequest) { Text(stringResource(R.string.cmd_cancel)) }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewLoadBackupDialog() {
    LoadBackupDialog(rememberSaveable { mutableStateOf("") })
}
