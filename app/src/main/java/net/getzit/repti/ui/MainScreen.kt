package net.getzit.repti.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.getzit.repti.R
import net.getzit.repti.Task
import net.getzit.repti.TaskId

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Full Preview", showSystemUi = true)
@Composable
fun MainUI(@PreviewParameter(TaskListPreviewParameterProvider::class) tasks: List<Task>?) {
    if (tasks != null) {
        MainScreen(tasks)
    } else {
        LoadingScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(tasks: List<Task>) {
    val scope = rememberCoroutineScope()
    var openNewTaskDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTaskId: TaskId? by rememberSaveable { mutableStateOf(null) }

    if (selectedTaskId != null && tasks.none { it.id == selectedTaskId }) {
        selectedTaskId = null
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedTaskId == null,
                exit = scaleOut(),
                enter = scaleIn(),
            ) {
                FloatingActionButton(onClick = { openNewTaskDialog = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cmd_create_new_task)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        TaskList(modifier = Modifier.padding(innerPadding), tasks = tasks)
    }

    if (openNewTaskDialog) {
        NewTaskDialog(
            onDismissRequest = { openNewTaskDialog = false },
            onConfirmRequest = { name ->
                scope.launchTaskRepository { newTask(name) }
                openNewTaskDialog = false
            },
        )
    }
}

@Composable
fun NewTaskDialog(onDismissRequest: () -> Unit, onConfirmRequest: (String) -> Unit) {
    val newTaskNameState = rememberSaveable { mutableStateOf("") }

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
                    stringResource(R.string.cmd_create_new_task),
                    style = MaterialTheme.typography.titleLarge
                )
                NameTextField(newTaskNameState = newTaskNameState, defaultFocus = true)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                    ) {
                        Text(stringResource(R.string.cmd_cancel))
                    }
                    TextButton(
                        onClick = { onConfirmRequest(newTaskNameState.value.trim()) },
                        enabled = newTaskNameState.value.trim().isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.cmd_create))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewNewTaskDialog() {
    NewTaskDialog(onDismissRequest = {}, onConfirmRequest = {})
}
