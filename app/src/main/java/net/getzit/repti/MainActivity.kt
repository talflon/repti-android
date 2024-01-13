package net.getzit.repti

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReptiApp() }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Full Preview", showSystemUi = true)
@Composable
fun ReptiApp() {
    TaskList(Dataset().allTasks.toList())
}

@Composable
fun TaskList(tasks: List<Task>) {
    LazyColumn() {
        for (task in tasks) {
            item {
                TaskListItem(task)
            }
        }
    }
}

fun formatDone(day: Day?): String =
    if (day == null) {
        "—"
    } else {
        DAYS.between(day.date, LocalDate.now()).toString()
    }

@Preview(widthDp = 300)
@Composable
fun TaskListItem(@PreviewParameter(TaskPreviewParameterProvider::class) task: Task) {
    Row(
        Modifier
            .padding(8.dp),
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
            Button(onClick = { /*TODO*/ }) {
                Icon(Icons.Rounded.Done, "mark as done")
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
                dataset.newTask("a much, much longer task").let {
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