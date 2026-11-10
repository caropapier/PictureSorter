package ruebe.picturesorter

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PictureSorter",
        state = rememberWindowState(width = 1200.dp, height = 900.dp),
    ) {
        val platform = remember { JvmPhotoPlatform(window) }
        App(platform)
    }
}
