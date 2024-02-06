package net.getzit.repti

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tasks by TaskRepository.instance.tasks.collectAsState()
            MainUI(tasks)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TaskRepository.instance.ensureLoaded()
            }
        }
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

@Composable
fun MainScreen(tasks: List<Task>) {
    val scope = rememberCoroutineScope()
    val openNewTaskDialog = remember { mutableStateOf(false) }
    var selectedTaskId: TaskId? by remember { mutableStateOf(null) }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedTaskId == null,
                exit = scaleOut(),
                enter = scaleIn(),
            ) {
                FloatingActionButton(onClick = { openNewTaskDialog.value = true }) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cmd_create_new_task)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            LazyColumn(modifier = Modifier.weight(1f), state = rememberLazyListState()) {
                items(items = tasks, key = { it.id.string }) { task ->
                    TaskListItem(
                        task = task,
                        selected = task.id == selectedTaskId,
                        setSelected = { selected ->
                            selectedTaskId = if (selected) task.id else null
                        })
                }
            }

            if (selectedTaskId != null) {
                Spacer(
                    Modifier
                        .height(16.dp)
                        .fillMaxWidth()
                        .clickable { selectedTaskId = null })
                TaskDetailCard(
                    modifier = Modifier.padding(8.dp),
                    task = tasks.first { it.id == selectedTaskId })
            }
        }
    }

    if (openNewTaskDialog.value) {
        NewTaskDialog(
            onDismissRequest = { openNewTaskDialog.value = false },
            onConfirmRequest = { name ->
                scope.launch {
                    TaskRepository.instance.newTask(name)
                }
                openNewTaskDialog.value = false
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
    @PreviewParameter(TaskPreviewParameterProvider::class) task: Task,
    selected: Boolean,
    setSelected: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .padding(8.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .selectable(
                selected = selected,
                onClick = { setSelected(!selected) }
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
                scope.launch {
                    TaskRepository.instance.update(task.copy(done = Day.today()))
                }
            }) {
                Icon(Icons.Rounded.Done, stringResource(R.string.cmd_mark_task_done))
            }
        }
    }
}

@Preview
@Composable
fun TaskDetailCard(
    modifier: Modifier = Modifier,
    @PreviewParameter(TaskPreviewParameterProvider::class) task: Task
) {
    val scope = rememberCoroutineScope()
    val cardContentDescription = stringResource(R.string.lbl_more_information_for_task)

    ElevatedCard(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = cardContentDescription
            }) {
        Column(Modifier.padding(8.dp)) {
            Text(text = task.name, style = MaterialTheme.typography.titleLarge)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    scope.launch {
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
                    scope.launch {
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
                    scope.launch {
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
}

@Composable
fun NewTaskDialog(onDismissRequest: () -> Unit, onConfirmRequest: (String) -> Unit) {
    var newTaskName by remember { mutableStateOf("") }
    val textFocusRequester = remember { FocusRequester() }

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
                OutlinedTextField(
                    value = newTaskName,
                    modifier = Modifier.focusRequester(textFocusRequester),
                    onValueChange = { newTaskName = it },
                    label = { Text(stringResource(R.string.lbl_name)) })
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                    ) {
                        Text(stringResource(R.string.cmd_cancel))
                    }
                    TextButton(
                        onClick = { onConfirmRequest(newTaskName) },
                    ) {
                        Text(stringResource(R.string.cmd_create))
                    }
                }
            }
        }

        LaunchedEffect(Unit) { textFocusRequester.requestFocus() }
    }
}

@Preview
@Composable
private fun PreviewNewTaskDialog() {
    NewTaskDialog(onDismissRequest = {}, onConfirmRequest = {})
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
