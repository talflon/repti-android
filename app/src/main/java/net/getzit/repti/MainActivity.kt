package net.getzit.repti

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.getzit.repti.ui.theme.ReptiTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val tasks by TaskRepository.instance.tasks.collectAsState()
            ReptiTheme(dynamicColor = false) {
                MainUI(tasks)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TaskRepository.instance.ensureLoaded()
            }
        }
    }
}

val idlingResource = CountingIdlingResource("MainActivity", true).also {
    IdlingRegistry.getInstance().register(it)
}

inline fun CoroutineScope.launchIdling(crossinline block: suspend CoroutineScope.() -> Unit) {
    idlingResource.increment()
    var launched = false
    try {
        launch {
            try {
                block()
            } finally {
                idlingResource.decrement()
            }
        }
        launched = true
    } finally {
        if (!launched) idlingResource.decrement()
    }
}

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
    val listState = rememberLazyListState()

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
        Box(Modifier.padding(innerPadding)) {
            LazyColumn(
                state = listState,
            ) {
                items(items = tasks, key = { it.id.string }) { task ->
                    TaskListItem(
                        task = task,
                        selected = task.id == selectedTaskId,
                        onClick = { selectedTaskId = task.id })
                }
            }
            if (listState.canScrollBackward) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                        .alpha(0.75f),
                )
            }
            if (listState.canScrollForward) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                        .alpha(0.75f),
                )
            }
        }

    }

    if (selectedTaskId != null) {
        TaskDetailCard(
            task = tasks.first { it.id == selectedTaskId },
            closeCard = { selectedTaskId = null },
        )
    }

    if (openNewTaskDialog) {
        NewTaskDialog(
            onDismissRequest = { openNewTaskDialog = false },
            onConfirmRequest = { name ->
                scope.launchIdling {
                    TaskRepository.instance.newTask(name)
                }
                openNewTaskDialog = false
            },
        )
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.width(64.dp),
        )
    }
}

fun formatDone(day: Day?): String =
    if (day == null) {
        "—"
    } else {
        Day.today().daysAfter(day).toString()
    }

@Composable
@ReadOnlyComposable
fun formatDoneLong(day: Day?): String =
    if (day == null) {
        stringResource(R.string.lbl_not_done)
    } else {
        when (val daysAgo = Day.today().daysAfter(day)) {
            0 -> stringResource(R.string.lbl_today)
            1 -> stringResource(R.string.lbl_yesterday)
            else -> stringResource(R.string.lbl_days_ago, daysAgo)
        }
    }

@Composable
fun TaskListItem(
    task: Task,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .padding(8.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .selectable(
                selected = selected,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = task.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDone(task.done), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launchIdling {
                    TaskRepository.instance.update(task.copy(done = Day.today()))
                }
            }) {
                Icon(Icons.Rounded.Done, stringResource(R.string.cmd_mark_task_done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailCard(
    task: Task,
    closeCard: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cardContentDescription = stringResource(R.string.lbl_more_information_for_task)
    var openDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var openEditDialog by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        modifier = Modifier.semantics { contentDescription = cardContentDescription },
        onDismissRequest = closeCard,
    ) {
        val insetPadding = WindowInsets.safeDrawing.asPaddingValues()
        Column(
            Modifier
                .absolutePadding(bottom = insetPadding.calculateBottomPadding())
                .padding(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
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
                        .padding(4.dp),
                    text = task.name,
                    style = MaterialTheme.typography.titleLarge
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    scope.launchIdling {
                        TaskRepository.instance.update(task.copy(done = null))
                    }
                }) {
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.cmd_clear_day_done)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    scope.launchIdling {
                        TaskRepository.instance.update(task.copy(done = task.done!!.minusDays(1)))
                    }
                }, enabled = task.done != null) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cmd_done_previous_day)
                    )
                }
                Text(
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    text = formatDoneLong(day = task.done),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = {
                    scope.launchIdling {
                        TaskRepository.instance.update(task.copy(done = task.done!!.plusDays(1)))
                    }
                }, enabled = task.done != null && task.done < Day.today()) {
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.cmd_done_next_day)
                    )
                }
            }
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
                TextButton(
                    onClick = {
                        scope.launchIdling {
                            TaskRepository.instance.delete(task)
                        }
                    },
                ) {
                    Text(stringResource(R.string.cmd_delete))
                }
            },
            dismissButton = {
                TextButton(
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
                scope.launchIdling {
                    TaskRepository.instance.update(task.copy(name = it))
                }
            })
    }
}

@Preview
@Composable
fun PreviewTaskDetailCard(@PreviewParameter(TaskPreviewParameterProvider::class) task: Task) {
    TaskDetailCard(task = task, closeCard = {})
}

@Composable
fun NameTextField(newTaskNameState: MutableState<String>, defaultFocus: Boolean = false) {
    var newTaskName by newTaskNameState
    val nameContentDescription = stringResource(R.string.lbl_name)
    var modifier = Modifier.semantics { contentDescription = nameContentDescription }
    if (defaultFocus) {
        val focusRequester = remember { FocusRequester() }
        modifier = modifier.focusRequester(focusRequester)
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    OutlinedTextField(
        value = newTaskName,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = modifier,
        onValueChange = { newTaskName = it.trimStart() },
        label = { Text(stringResource(R.string.lbl_name)) })
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
                    style = MaterialTheme.typography.titleLarge
                )
                NameTextField(newTaskNameState = newTaskNameState)
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


class TaskPreviewParameterProvider : PreviewParameterProvider<Task> {
    override val values: Sequence<Task>
        get() {
            val dataset = Dataset()
            return sequenceOf(
                dataset.newTask("short task"),
                dataset.newTask("a much, much, much, much longer task").let {
                    it.copy(done = Day.of(LocalDate.now().minusDays(12)))
                        .also { dataset.update(it) }
                },
                dataset.newTask("task done today").let {
                    it.copy(done = Day.today())
                        .also { dataset.update(it) }
                },
            )
        }
}

class TaskListPreviewParameterProvider : PreviewParameterProvider<List<Task>?> {
    override val values: Sequence<List<Task>?>
        get() {
            return sequenceOf(null, TaskPreviewParameterProvider().values.toList())
        }
}
