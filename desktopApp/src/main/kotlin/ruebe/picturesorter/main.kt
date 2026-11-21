package ruebe.picturesorter

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import ruebe.picturesorter.resources.Res
import ruebe.picturesorter.resources.app_icon

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PictureSorter",
        icon = painterResource(Res.drawable.app_icon),
        state = rememberWindowState(width = 1200.dp, height = 900.dp),
    ) {
        val platform = remember { JvmPhotoPlatform(window) }
        App(platform)
    }
}
