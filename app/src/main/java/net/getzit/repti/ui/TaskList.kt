// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import net.getzit.repti.Day
import net.getzit.repti.R
import net.getzit.repti.Task
import net.getzit.repti.TaskId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskList(modifier: Modifier = Modifier, tasks: List<Task>) {
    var selectedTaskId: TaskId? by rememberSaveable { mutableStateOf(null) }
    val listState = rememberLazyListState()

    if (selectedTaskId != null && tasks.none { it.id == selectedTaskId }) {
        selectedTaskId = null
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues.Absolute(left = 8.dp, right = 8.dp),
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
                    .background(colorScheme.surface.copy(alpha = 0.75f))
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
                    .background(colorScheme.surface.copy(alpha = 0.75f))
                    .alpha(0.75f),
            )
        }
    }

    if (selectedTaskId != null) {
        TaskDetailSheet(
            task = tasks.first { it.id == selectedTaskId },
            onDismiss = { selectedTaskId = null },
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListItem(
    task: Task,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val swipeState = rememberSwipeToDismissBoxState()
    if (swipeState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
        scope.launchTaskRepository {
            update(task.copy(done = Day.today()))
            swipeState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromEndToStart = false,
        backgroundContent = { Box(Modifier.fillMaxSize().background(colorScheme.secondary)) }
    ) {
        Row(
            Modifier
                .background(if (selected) colorScheme.secondaryContainer else colorScheme.background)
                .defaultMinSize(minHeight = 24.dp)
                .padding(4.dp)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.name,
                style = typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (task.done != null) {
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDoneDaysAgo(task.done),
                        style = typography.labelLarge
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Done,
                        stringResource(R.string.lbl_done),
                        tint = colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewTaskListItem(
    @PreviewParameter(TaskPreviewParameterProvider::class) task: Task,
) {
    TaskListItem(task = task, selected = false, onClick = {})
}


fun formatDoneDaysAgo(day: Day): String =
    Day.today().daysAfter(day).let { if (it == 0) "" else it.toString() }
