package net.getzit.repti.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import net.getzit.repti.R
import net.getzit.repti.Task
import net.getzit.repti.TaskId
import net.getzit.repti.ui.theme.ReptiTheme

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(name = "Dark Mode", uiMode = UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true)
@Composable
fun MainUI(@PreviewParameter(TaskListPreviewParameterProvider::class) tasks: List<Task>?) {
    ReptiTheme(dynamicColor = false) {
        if (tasks != null) {
            MainScreen(tasks)
        } else {
            LoadingScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(tasks: List<Task>) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var openNewTaskDialog by rememberSaveable { mutableStateOf(false) }
    val loadBackupDialogState = rememberLoadBackupDialogState()
    var selectedTaskId: TaskId? by rememberSaveable { mutableStateOf(null) }

    if (selectedTaskId != null && tasks.none { it.id == selectedTaskId }) {
        selectedTaskId = null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainMenu(
                startNewTask = { openNewTaskDialog = true },
                loadBackupDialogState = loadBackupDialogState
            )
        },
    ) {
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
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.primaryContainer,
                        titleContentColor = colorScheme.primary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cmd_menu)
                            )
                        }
                    }
                )
            },
        ) { innerPadding ->
            TaskList(modifier = Modifier.padding(innerPadding), tasks = tasks)
        }
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
    LoadBackupDialog(loadBackupDialogState)
}

@Composable
fun MainMenu(startNewTask: () -> Unit, loadBackupDialogState: MutableState<String?>) {
    val contentDescription = stringResource(R.string.lbl_menu)
    ModalDrawerSheet(
        modifier = Modifier.semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(R.string.app_name),
            style = typography.titleSmall,
        )
        MenuItem(startNewTask, R.string.cmd_create_new_task, icon = Icons.Rounded.Add)
        MenuItem(rememberSaveBackup(), R.string.cmd_save_backup)
        MenuItem(rememberLoadBackup(loadBackupDialogState), R.string.cmd_load_backup)
    }
}

@Composable
fun MenuItem(onClick: () -> Unit, labelText: String, icon: ImageVector? = null) {
    NavigationDrawerItem(
        label = { Text(labelText) },
        selected = false,
        onClick = onClick,
        icon = { if (icon != null) Icon(icon, contentDescription = null) },
    )
}

@Composable
fun MenuItem(onClick: () -> Unit, @StringRes labelText: Int, icon: ImageVector? = null) {
    MenuItem(onClick, stringResource(labelText), icon)
}

@Preview
@Composable
fun PreviewMainMenu() {
    MainMenu(startNewTask = {}, loadBackupDialogState = rememberLoadBackupDialogState())
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
                    style = typography.titleLarge
                )
                NameTextField(newTaskNameState = newTaskNameState, defaultFocus = true)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    OutlinedButton(onDismissRequest) { Text(stringResource(R.string.cmd_cancel)) }
                    Button(
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
