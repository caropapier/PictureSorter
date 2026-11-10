package ruebe.picturesorter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import ruebe.picturesorter.ui.DoneScreen
import ruebe.picturesorter.ui.MovingScreen
import ruebe.picturesorter.ui.RatingScreen
import ruebe.picturesorter.ui.StartScreen
import ruebe.picturesorter.ui.SummaryScreen

@Composable
fun App(platform: PhotoPlatform) {
    val vm = viewModel { SorterViewModel(platform) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (vm.screen) {
                    Screen.START -> StartScreen(
                        busy = vm.busy,
                        message = vm.message,
                        onChooseFolder = vm::chooseFolder,
                    )
                    Screen.RATING -> vm.session?.let { session ->
                        RatingScreen(
                            session = session,
                            images = vm.images,
                            onRate = vm::rate,
                            onUndo = vm::undo,
                        )
                    }
                    Screen.SUMMARY -> vm.session?.let { session ->
                        SummaryScreen(
                            session = session,
                            busy = vm.busy,
                            message = vm.message,
                            onMove = vm::prepareMove,
                            onNextRound = vm::startNextRound,
                            onUndo = vm::undo,
                        )
                    }
                    Screen.MOVING -> MovingScreen()
                    Screen.DONE -> {
                        val result = vm.moveResult
                        val session = vm.session
                        val mode = vm.movedMode
                        if (result != null && session != null && mode != null) {
                            DoneScreen(
                                result = result,
                                session = session,
                                mode = mode,
                                message = vm.message,
                                onOpenFolder = vm::openTargetFolder,
                                onRestart = vm::restart,
                            )
                        }
                    }
                }
            }

            vm.pendingMove?.let { move ->
                AlertDialog(
                    onDismissRequest = vm::cancelMove,
                    title = { Text("Move pictures?") },
                    text = {
                        Text(buildAnnotatedString {
                            append("${move.photos.size} pictures will be ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("moved") }
                            append(" to\n${move.targetFolder}\nThey will no longer be in the source folder.")
                        })
                    },
                    confirmButton = { TextButton(onClick = vm::confirmMove) { Text("Move") } },
                    dismissButton = { TextButton(onClick = vm::cancelMove) { Text("Cancel") } },
                )
            }
        }
    }
}
