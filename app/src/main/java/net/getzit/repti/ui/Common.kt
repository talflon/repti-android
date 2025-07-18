// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.test.espresso.idling.CountingIdlingResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.getzit.repti.Dataset
import net.getzit.repti.Day
import net.getzit.repti.R
import net.getzit.repti.Task
import net.getzit.repti.TaskRepository
import java.time.LocalDate

@Composable
@ReadOnlyComposable
fun formatDoneWhen(day: Day?): String =
    if (day == null) {
        stringResource(R.string.lbl_not_done)
    } else {
        when (val daysAgo = Day.today().daysAfter(day)) {
            0 -> stringResource(R.string.lbl_today)
            1 -> stringResource(R.string.lbl_yesterday)
            else -> stringResource(R.string.lbl_days_ago, daysAgo)
        }
    }

val idlingResource = CountingIdlingResource("MainActivity", true)

inline fun CoroutineScope.launchIdling(crossinline block: suspend CoroutineScope.() -> Unit): Job {
    idlingResource.increment()
    var job: Job? = null
    try {
        job = launch {
            try {
                block()
            } finally {
                idlingResource.decrement()
            }
        }
        return job
    } finally {
        if (job == null) idlingResource.decrement()
    }
}

inline fun CoroutineScope.launchTaskRepository(crossinline block: suspend TaskRepository.() -> Unit): Job =
    launchIdling { TaskRepository.instance.run { block() } }

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
        )
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
