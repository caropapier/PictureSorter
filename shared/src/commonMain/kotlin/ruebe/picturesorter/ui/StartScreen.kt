package ruebe.picturesorter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ruebe.picturesorter.model.SavedProgress
import ruebe.picturesorter.resources.Res
import ruebe.picturesorter.resources.logo

@Composable
fun StartScreen(
    busy: Boolean,
    message: String?,
    savedSessions: List<SavedProgress>,
    onChooseFolder: () -> Unit,
    onResume: (SavedProgress) -> Unit,
    onDiscard: (SavedProgress) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "PictureSorter",
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Text(
            "Choose a folder. You will then see one picture after another and decide: " +
                "Yes, No or Maybe. Your progress is saved automatically.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        if (busy) {
            CircularProgressIndicator(color = ProgressBlue)
        } else {
            Button(onClick = onChooseFolder) { Text("Choose folder") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (savedSessions.isNotEmpty()) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Unfinished sessions", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(savedSessions, key = { it.folder }) { saved ->
                        SavedSessionCard(saved, enabled = !busy, onResume = onResume, onDiscard = onDiscard)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSessionCard(
    saved: SavedProgress,
    enabled: Boolean,
    onResume: (SavedProgress) -> Unit,
    onDiscard: (SavedProgress) -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    saved.folder,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    "Round ${saved.round} · ${saved.ratedCount} / ${saved.total} pictures rated",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onDiscard(saved) }, enabled = enabled) { Text("Discard") }
            Button(onClick = { onResume(saved) }, enabled = enabled) { Text("Continue") }
        }
    }
}
