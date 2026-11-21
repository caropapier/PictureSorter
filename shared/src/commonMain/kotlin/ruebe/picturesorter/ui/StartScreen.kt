package ruebe.picturesorter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ruebe.picturesorter.resources.Res
import ruebe.picturesorter.resources.logo

@Composable
fun StartScreen(busy: Boolean, message: String?, onChooseFolder: () -> Unit) {
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
                "Yes, No or Maybe.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (busy) {
            CircularProgressIndicator(color = ProgressBlue)
        } else {
            Button(onClick = onChooseFolder) { Text("Choose folder") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
