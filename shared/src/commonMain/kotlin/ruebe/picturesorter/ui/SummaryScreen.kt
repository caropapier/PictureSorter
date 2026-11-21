package ruebe.picturesorter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.SelectionMode
import ruebe.picturesorter.model.SortSession

@Composable
fun SummaryScreen(
    session: SortSession,
    busy: Boolean,
    message: String?,
    onMove: (SelectionMode) -> Unit,
    onNextRound: () -> Unit,
    onUndo: () -> Unit,
) {
    val summary = session.summary()
    val yesAndMaybe = summary.yes + summary.maybe

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Round ${session.round} finished", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CountTile(Rating.YES, summary.yes)
            CountTile(Rating.MAYBE, summary.maybe)
            CountTile(Rating.NO, summary.no)
        }

        if (busy) {
            CircularProgressIndicator(color = ProgressBlue)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onMove(SelectionMode.YES_AND_MAYBE) },
                    enabled = yesAndMaybe > 0,
                    modifier = Modifier.width(360.dp),
                ) { Text("Move Yes + Maybe ($yesAndMaybe)") }
                Button(
                    onClick = { onMove(SelectionMode.YES_ONLY) },
                    enabled = summary.yes > 0,
                    modifier = Modifier.width(360.dp),
                ) { Text("Move Yes only (${summary.yes})") }
                OutlinedButton(
                    onClick = onNextRound,
                    enabled = yesAndMaybe > 0,
                    modifier = Modifier.width(360.dp),
                ) { Text("New round over Yes + Maybe ($yesAndMaybe)") }
                TextButton(onClick = onUndo) { Text("Change last rating") }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun CountTile(rating: Rating, count: Int) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .background(rating.color, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = MaterialTheme.typography.displaySmall, color = OnRatingColor)
        Text("${rating.symbol} ${rating.label}", style = MaterialTheme.typography.titleMedium, color = OnRatingColor)
    }
}
