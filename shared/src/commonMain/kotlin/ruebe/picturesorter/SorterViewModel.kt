package ruebe.picturesorter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ruebe.picturesorter.model.Photo
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.SavedProgress
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

    /** Unfinished sorting sessions, most recently changed first. Kept in sync with the files on disk. */
    var savedSessions by mutableStateOf<List<SavedProgress>>(emptyList())
        private set
    /** Set when the chosen folder has saved progress: ask whether to continue or start over. */
    var resumeOffer by mutableStateOf<SavedProgress?>(null)
        private set
    /** Set while the "discard progress?" confirmation is shown. */
    var discardRequest by mutableStateOf<SavedProgress?>(null)
        private set

    /** Loaded pictures around the current position (previous, current, next two). */
    val images = mutableStateMapOf<String, ImageState>()
    private val imageJobs = mutableMapOf<String, Job>()

    /** Progress writes are done one after another in the background, so they never block rating. */
    private val diskOps = Channel<DiskOp>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { processDiskOps() }
        viewModelScope.launch {
            val loaded = runCatching { platform.readProgressFiles() }.getOrDefault(emptyList())
                .mapNotNull { SavedProgress.decode(it) }
            // Sessions changed while loading are newer than the files
            savedSessions = savedSessions + loaded.filter { file -> savedSessions.none { it.folder == file.folder } }
        }
    }

    fun chooseFolder() = launchBusy {
        val folder = platform.pickFolder("Choose picture folder", sourceFolder) ?: return@launchBusy
        val saved = savedSessions.firstOrNull { platform.isSameFolder(it.folder, folder) }
        if (saved != null) {
            message = null
            resumeOffer = saved
            return@launchBusy
        }
        startNewSession(folder)
    }

    /** Continues a saved session (from the start screen or the resume question). */
    fun resume(saved: SavedProgress) = launchBusy {
        resumeOffer = null
        val photos = runCatching { platform.listImages(saved.folder) }.getOrNull()
        if (photos == null) {
            message = "Folder not found: ${saved.folder}"
            return@launchBusy
        }
        val restored = SortSession.restore(saved, photos)
        if (restored.session.queue.isEmpty()) {
            message = "None of the saved pictures are in the folder anymore."
            return@launchBusy
        }
        message = if (restored.missingCount > 0) {
            "${restored.missingCount} pictures are no longer in the folder and were skipped."
        } else {
            null
        }
        sourceFolder = saved.folder
        clearImages()
        updateSession(restored.session)
        screen = if (restored.session.isRoundFinished) Screen.SUMMARY else Screen.RATING
    }

    /** Answer "start over" to the resume question: the saved progress is discarded. */
    fun startOver() = launchBusy {
        val saved = resumeOffer ?: return@launchBusy
        resumeOffer = null
        forgetProgress(saved.folder)
        startNewSession(saved.folder)
    }

    fun cancelResume() {
        resumeOffer = null
    }

    fun requestDiscard(saved: SavedProgress) {
        discardRequest = saved
    }

    fun confirmDiscard() {
        val saved = discardRequest ?: return
        discardRequest = null
        forgetProgress(saved.folder)
    }

    fun cancelDiscard() {
        discardRequest = null
    }

    fun rate(rating: Rating) {
        val current = session ?: return
        if (screen != Screen.RATING || current.isRoundFinished) return
        message = null
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
        val source = sourceFolder ?: return
        pendingMove = null
        screen = Screen.MOVING
        movedMode = move.mode
        viewModelScope.launch {
            moveResult = platform.moveFiles(move.photos, move.targetFolder)
            // Sorting of this folder is finished
            forgetProgress(source)
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

    /** Home icon clicked: return to the start screen. Ratings are saved, so nothing is lost. */
    fun requestHome() {
        // Moving cannot be interrupted safely; while busy a folder dialog is still open
        if (screen == Screen.START || screen == Screen.MOVING || busy) return
        restart()
    }

    fun restart() {
        clearImages()
        pendingMove = null
        session = null
        moveResult = null
        movedMode = null
        message = null
        screen = Screen.START
    }

    fun dismissMessage() {
        message = null
    }

    private suspend fun startNewSession(folder: String) {
        val photos = platform.listImages(folder)
        if (photos.isEmpty()) {
            message = "No pictures found in this folder."
            return
        }
        message = null
        sourceFolder = folder
        clearImages()
        updateSession(SortSession(photos))
        screen = Screen.RATING
    }

    private fun updateSession(newSession: SortSession) {
        session = newSession
        saveProgress()
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

    /** Saves the current session, or removes the saved one if there is nothing worth keeping. */
    private fun saveProgress() {
        val folder = sourceFolder ?: return
        val current = session ?: return
        if (!current.canUndo && current.round == 1) {
            forgetProgress(folder)
            return
        }
        val progress = SavedProgress.of(folder, current)
        savedSessions = listOf(progress) + savedSessions.filterNot { it.folder == folder }
        diskOps.trySend(DiskOp.Save(progress))
    }

    private fun forgetProgress(folder: String) {
        savedSessions = savedSessions.filterNot { it.folder == folder }
        diskOps.trySend(DiskOp.Delete(folder))
    }

    private sealed interface DiskOp {
        data class Save(val progress: SavedProgress) : DiskOp
        data class Delete(val folder: String) : DiskOp
    }

    private suspend fun processDiskOps() {
        var next: DiskOp? = null
        while (true) {
            var op = next ?: diskOps.receive()
            next = null
            // Several queued saves of the same folder: only the newest one needs to be written
            while (op is DiskOp.Save) {
                val newer = diskOps.tryReceive().getOrNull() ?: break
                if (newer is DiskOp.Save && newer.progress.folder == op.progress.folder) {
                    op = newer
                } else {
                    next = newer
                    break
                }
            }
            try {
                when (op) {
                    is DiskOp.Save -> platform.writeProgressFile(op.progress.folder, op.progress.encode())
                    is DiskOp.Delete -> platform.deleteProgressFile(op.folder)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Could not save progress: ${e.message ?: e::class.simpleName}"
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
            } catch (e: CancellationException) {
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
