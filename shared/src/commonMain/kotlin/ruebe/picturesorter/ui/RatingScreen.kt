package ruebe.picturesorter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruebe.picturesorter.ImageState
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.SortSession

@Composable
fun RatingScreen(
    session: SortSession,
    images: Map<String, ImageState>,
    message: String?,
    onRate: (Rating) -> Unit,
    onUndo: () -> Unit,
    onHome: () -> Unit,
) {
    val photo = session.current ?: return
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> onRate(Rating.NO)
                    Key.DirectionRight -> onRate(Rating.YES)
                    Key.DirectionUp, Key.DirectionDown -> onRate(Rating.MAYBE)
                    Key.Backspace -> onUndo()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: round, progress, file name, rating from the previous round
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Round ${session.round} · Picture ${session.currentIndex + 1} / ${session.queue.size}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                photo.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            session.previousRatingOf(photo)?.let { previous ->
                Text(
                    "Before: ${previous.symbol} ${previous.label}",
                    color = OnRatingColor,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(previous.color, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            HomeButton(onClick = onHome)
        }
        LinearProgressIndicator(
            progress = { session.currentIndex.toFloat() / session.queue.size },
            modifier = Modifier.fillMaxWidth(),
            color = ProgressBlue,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        // Picture styled like a printed photo: white border and soft shadow, sized to its aspect ratio
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = images[photo.id]) {
                is ImageState.Loaded -> Image(
                    bitmap = state.bitmap,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .aspectRatio(state.bitmap.width.toFloat() / state.bitmap.height)
                        .shadow(10.dp, RoundedCornerShape(2.dp))
                        .background(Color.White)
                        .padding(8.dp),
                )
                ImageState.Failed -> Text("Picture cannot be displayed")
                ImageState.Loading, null -> CircularProgressIndicator(color = ProgressBlue)
            }
        }

        // Back button in the left corner, the three rating buttons centered below the picture
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onUndo,
                enabled = session.canUndo,
                modifier = Modifier.align(Alignment.CenterStart),
            ) { Text("↶ Back") }
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                RatingButton(Rating.NO, onRate)
                RatingButton(Rating.MAYBE, onRate)
                RatingButton(Rating.YES, onRate)
            }
        }
    }
}

@Composable
private fun RatingButton(rating: Rating, onRate: (Rating) -> Unit) {
    Button(
        onClick = { onRate(rating) },
        colors = ButtonDefaults.buttonColors(containerColor = rating.color, contentColor = OnRatingColor),
        modifier = Modifier.width(120.dp).height(64.dp),
    ) {
        Text(rating.symbol, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}
