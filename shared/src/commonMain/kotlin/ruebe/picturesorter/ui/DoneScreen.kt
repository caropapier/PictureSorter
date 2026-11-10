package ruebe.picturesorter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ruebe.picturesorter.MoveResult
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.SelectionMode
import ruebe.picturesorter.model.SortSession

@Composable
fun MovingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Moving pictures …")
    }
}

@Composable
fun DoneScreen(
    result: MoveResult,
    session: SortSession,
    mode: SelectionMode,
    message: String?,
    onOpenFolder: () -> Unit,
    onRestart: () -> Unit,
) {
    val summary = session.summary()
    val modeText = when (mode) {
        SelectionMode.YES_AND_MAYBE -> "Yes + Maybe"
        SelectionMode.YES_ONLY -> "Yes only"
    }
    val roundsText = if (session.round == 1) "1 round" else "${session.round} rounds"

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Done", style = MaterialTheme.typography.headlineMedium)
        Text(
            "After $roundsText · result of the last round:",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CountTile(Rating.YES, summary.yes)
            CountTile(Rating.MAYBE, summary.maybe)
            CountTile(Rating.NO, summary.no)
        }
        Text("Chosen option: $modeText", style = MaterialTheme.typography.titleMedium)
        Text(
            "${result.movedCount} pictures were moved to\n${result.targetFolder}",
            textAlign = TextAlign.Center,
        )
        if (result.failures.isNotEmpty()) {
            Text(
                "${result.failures.size} pictures could not be moved:",
                color = MaterialTheme.colorScheme.error,
            )
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(result.failures) { failure ->
                    Text("${failure.photo.name}: ${failure.reason}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onOpenFolder) { Text("Open target folder") }
            Button(onClick = onRestart) { Text("Sort another folder") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
