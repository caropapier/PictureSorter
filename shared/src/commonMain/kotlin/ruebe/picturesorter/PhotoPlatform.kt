package ruebe.picturesorter

import androidx.compose.ui.graphics.ImageBitmap
import ruebe.picturesorter.model.Photo

/**
 * Everything that depends on the operating system. Desktop: [JvmPhotoPlatform].
 * A future Android version can implement this interface, e.g. via the Storage Access Framework.
 */
interface PhotoPlatform {
    /** Shows a folder chooser. Returns `null` if cancelled. */
    suspend fun pickFolder(title: String, suggestion: String? = null): String?

    /** All supported pictures directly inside the folder (not recursive), sorted by name. */
    suspend fun listImages(folder: String): List<Photo>

    /** Loads a picture, scaled down to at most [maxSize] pixels per side. `null` if it cannot be read. */
    suspend fun loadImage(photo: Photo, maxSize: Int): ImageBitmap?

    /** MOVES (does not copy) the pictures into [targetFolder]. A failure on one file does not abort the rest. */
    suspend fun moveFiles(photos: List<Photo>, targetFolder: String): MoveResult

    /** Opens the folder in the system file manager (Windows: Explorer). */
    suspend fun openFolder(folder: String)

    /** Path of the suggested target folder inside [folder]. */
    fun suggestTargetFolder(folder: String): String

    /** `true` if both paths denote the same folder. */
    fun isSameFolder(a: String, b: String): Boolean
}

data class MoveFailure(val photo: Photo, val reason: String)

data class MoveResult(val targetFolder: String, val movedCount: Int, val failures: List<MoveFailure>)
