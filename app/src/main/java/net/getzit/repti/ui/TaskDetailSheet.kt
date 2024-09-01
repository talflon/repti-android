package net.getzit.repti.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.getzit.repti.Day
import net.getzit.repti.R
import net.getzit.repti.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    val scope = rememberCoroutineScope()
    val sheetContentDescription = stringResource(R.string.lbl_more_information_for_task)
    var openDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var openEditDialog by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        modifier = Modifier.semantics { contentDescription = sheetContentDescription },
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier.padding(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = {
                    openDeleteDialog = true
                }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.cmd_delete),
                    )
                }
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    text = task.name,
                    style = typography.headlineLarge
                )
                Button(onClick = {
                    openEditDialog = true
                }) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.cmd_edit_task),
                    )
                }
            }
            HorizontalDivider()
            TaskDoneQuickSetter(task)
        }
    }

    if (openDeleteDialog) {
        AlertDialog(
            icon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.cmd_delete)
                )
            },
            title = { Text(stringResource(R.string.qst_delete)) },
            text = { Text(stringResource(R.string.dsc_delete)) },
            onDismissRequest = { openDeleteDialog = false },
            confirmButton = {
                Button(
                    onClick = { scope.launchTaskRepository { delete(task) } },
                ) {
                    Text(stringResource(R.string.cmd_delete))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { openDeleteDialog = false },
                ) {
                    Text(stringResource(R.string.cmd_cancel))
                }
            }
        )
    } else if (openEditDialog) {
        EditTaskNameDialog(
            oldName = task.name,
            onDismissRequest = { openEditDialog = false },
            onConfirmRequest = {
                openEditDialog = false
                scope.launchTaskRepository {
                    update(task.copy(name = it))
                }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun PreviewTaskDetailSheet(@PreviewParameter(TaskPreviewParameterProvider::class) task: Task) {
    TaskDetailSheet(
        task = task,
        onDismiss = {},
        sheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    )
}

@Preview
@Composable
fun TaskDoneQuickSetter(@PreviewParameter(TaskPreviewParameterProvider::class) task: Task) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = {
            scope.launchTaskRepository {
                update(task.copy(done = task.done!!.minusDays(1)))
            }
        }, enabled = task.done != null) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cmd_done_previous_day)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                scope.launchTaskRepository {
                    update(task.copy(done = Day.today()))
                }
            }, enabled = task.done != Day.today()) {
                Text(stringResource(R.string.cmd_done_today))
            }
            Text(
                textAlign = TextAlign.Center,
                text = formatDoneWhen(day = task.done),
                style = typography.bodyLarge,
            )
            Button(onClick = {
                scope.launchTaskRepository {
                    update(task.copy(done = null))
                }
            }, enabled = task.done != null) {
                Text(stringResource(R.string.cmd_clear))
            }
        }
        Button(onClick = {
            scope.launchTaskRepository {
                update(task.copy(done = task.done!!.plusDays(1)))
            }
        }, enabled = task.done != null && task.done < Day.today()) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.cmd_done_next_day)
            )
        }
    }
}

@Composable
fun EditTaskNameDialog(
    oldName: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    val newTaskNameState = rememberSaveable { mutableStateOf(oldName) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.cmd_edit_task),
                    style = typography.titleLarge
                )
                NameTextField(newTaskNameState = newTaskNameState)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                    ) {
                        Text(stringResource(R.string.cmd_cancel))
                    }
                    Button(
                        onClick = { onConfirmRequest(newTaskNameState.value.trim()) },
                        enabled = newTaskNameState.value.trim()
                            .let { it.isNotEmpty() && it != oldName },
                    ) {
                        Text(stringResource(R.string.cmd_save))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewEditTaskNameDialog(@PreviewParameter(TaskPreviewParameterProvider::class) task: Task) {
    EditTaskNameDialog(oldName = task.name, onDismissRequest = {}, onConfirmRequest = {})
}
