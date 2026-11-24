package ruebe.picturesorter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import ruebe.picturesorter.ui.HomeButton
import androidx.compose.material3.AlertDialog
import ruebe.picturesorter.ui.PictureSorterTheme
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
    PictureSorterTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (vm.screen) {
                    Screen.START -> StartScreen(
                        busy = vm.busy,
                        message = vm.message,
                        savedSessions = vm.savedSessions,
                        onChooseFolder = vm::chooseFolder,
                        onResume = vm::resume,
                        onDiscard = vm::requestDiscard,
                    )
                    Screen.RATING -> vm.session?.let { session ->
                        RatingScreen(
                            session = session,
                            images = vm.images,
                            message = vm.message,
                            onRate = vm::rate,
                            onUndo = vm::undo,
                            onHome = vm::requestHome,
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

                // The rating screen places the icon in its own header row
                if (vm.screen == Screen.SUMMARY || vm.screen == Screen.DONE) {
                    HomeButton(
                        onClick = vm::requestHome,
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    )
                }
            }

            vm.resumeOffer?.let { saved ->
                AlertDialog(
                    onDismissRequest = vm::cancelResume,
                    title = { Text("Continue where you left off?") },
                    text = {
                        Text(
                            "This folder has an unfinished session: round ${saved.round}, " +
                                "${saved.ratedCount} of ${saved.total} pictures rated.\n" +
                                "Starting over discards these ratings.",
                        )
                    },
                    confirmButton = { TextButton(onClick = { vm.resume(saved) }) { Text("Continue") } },
                    dismissButton = { TextButton(onClick = vm::startOver) { Text("Start over") } },
                )
            }

            vm.discardRequest?.let { saved ->
                AlertDialog(
                    onDismissRequest = vm::cancelDiscard,
                    title = { Text("Discard progress?") },
                    text = {
                        Text(
                            "The ratings for\n${saved.folder}\nwill be lost. No pictures are moved or deleted.",
                        )
                    },
                    confirmButton = { TextButton(onClick = vm::confirmDiscard) { Text("Discard") } },
                    dismissButton = { TextButton(onClick = vm::cancelDiscard) { Text("Keep") } },
                )
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
