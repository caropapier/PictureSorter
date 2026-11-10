package ruebe.picturesorter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ruebe.picturesorter.model.Photo
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.SelectionMode
import ruebe.picturesorter.model.SortSession

enum class Screen { START, RATING, SUMMARY, MOVING, DONE }

sealed interface ImageState {
    data object Loading : ImageState
    data class Loaded(val bitmap: ImageBitmap) : ImageState
    data object Failed : ImageState
}

data class PendingMove(val photos: List<Photo>, val targetFolder: String, val mode: SelectionMode)

class SorterViewModel(private val platform: PhotoPlatform) : ViewModel() {

    var screen by mutableStateOf(Screen.START)
        private set
    var sourceFolder by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf<SortSession?>(null)
        private set
    var pendingMove by mutableStateOf<PendingMove?>(null)
        private set
    var moveResult by mutableStateOf<MoveResult?>(null)
        private set
    /** Option chosen when moving (shown on the done screen). */
    var movedMode by mutableStateOf<SelectionMode?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Loaded pictures around the current position (previous, current, next two). */
    val images = mutableStateMapOf<String, ImageState>()
    private val imageJobs = mutableMapOf<String, Job>()

    fun chooseFolder() = launchBusy {
        val folder = platform.pickFolder("Choose picture folder", sourceFolder) ?: return@launchBusy
        val photos = platform.listImages(folder)
        if (photos.isEmpty()) {
            message = "No pictures found in this folder."
            return@launchBusy
        }
        message = null
        sourceFolder = folder
        clearImages()
        updateSession(SortSession(photos))
        screen = Screen.RATING
    }

    fun rate(rating: Rating) {
        val current = session ?: return
        if (screen != Screen.RATING || current.isRoundFinished) return
        updateSession(current.rate(rating))
        if (session?.isRoundFinished == true) screen = Screen.SUMMARY
    }

    fun undo() {
        val current = session ?: return
        if (!current.canUndo) return
        updateSession(current.undo())
        screen = Screen.RATING
    }

    fun startNextRound() {
        val current = session ?: return
        if (current.selection(SelectionMode.YES_AND_MAYBE).isEmpty()) return
        updateSession(current.startNextRound())
        screen = Screen.RATING
    }

    fun prepareMove(mode: SelectionMode) = launchBusy {
        val current = session ?: return@launchBusy
        val source = sourceFolder ?: return@launchBusy
        val photos = current.selection(mode)
        if (photos.isEmpty()) return@launchBusy
        val target = platform.pickFolder("Choose target folder", platform.suggestTargetFolder(source))
            ?: return@launchBusy
        if (platform.isSameFolder(target, source)) {
            message = "The target folder must not be the source folder."
            return@launchBusy
        }
        message = null
        pendingMove = PendingMove(photos, target, mode)
    }

    fun cancelMove() {
        pendingMove = null
    }

    fun confirmMove() {
        val move = pendingMove ?: return
        pendingMove = null
        screen = Screen.MOVING
        movedMode = move.mode
        viewModelScope.launch {
            moveResult = platform.moveFiles(move.photos, move.targetFolder)
            clearImages()
            screen = Screen.DONE
        }
    }

    fun openTargetFolder() {
        val folder = moveResult?.targetFolder ?: return
        viewModelScope.launch {
            runCatching { platform.openFolder(folder) }
                .onFailure { message = "Could not open folder: ${it.message}" }
        }
    }

    fun restart() {
        clearImages()
        session = null
        moveResult = null
        movedMode = null
        message = null
        screen = Screen.START
    }

    fun dismissMessage() {
        message = null
    }

    private fun updateSession(newSession: SortSession) {
        session = newSession
        val index = newSession.currentIndex
        val wanted = (index - 1..index + 2).mapNotNull { newSession.queue.getOrNull(it) }
        val wantedIds = wanted.map { it.id }.toSet()
        (images.keys - wantedIds).forEach { id ->
            imageJobs.remove(id)?.cancel()
            images.remove(id)
        }
        wanted.filter { it.id !in images }.forEach { photo ->
            images[photo.id] = ImageState.Loading
            imageJobs[photo.id] = viewModelScope.launch {
                val bitmap = runCatching { platform.loadImage(photo, MAX_IMAGE_SIZE) }.getOrNull()
                images[photo.id] = if (bitmap != null) ImageState.Loaded(bitmap) else ImageState.Failed
                imageJobs.remove(photo.id)
            }
        }
    }

    private fun clearImages() {
        imageJobs.values.forEach { it.cancel() }
        imageJobs.clear()
        images.clear()
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Error: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_SIZE = 2400
    }
}
