package ruebe.picturesorter

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import ruebe.picturesorter.model.Photo
import java.awt.Component
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class JvmPhotoPlatform(
    private val parent: Component? = null,
    private val progressDir: Path = defaultProgressDir(),
) : PhotoPlatform {

    override suspend fun pickFolder(title: String, suggestion: String?): String? = withContext(Dispatchers.Swing) {
        ensureSystemLookAndFeel()
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            // Without a suggestion, start in the user's pictures folder instead of "Documents"
            (suggestion ?: picturesFolder)?.let { File(it) }?.let { file ->
                if (file.isDirectory) {
                    currentDirectory = file
                } else {
                    // Suggestion does not exist yet (e.g. "<source>/Selected"): open the parent folder and prefill the name.
                    file.parentFile?.let { currentDirectory = it }
                    selectedFile = file
                }
            }
        }
        if (chooser.showDialog(parent, "Select") == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }

    override suspend fun listImages(folder: String): List<Photo> = withContext(Dispatchers.IO) {
        Files.list(Paths.get(folder)).use { stream ->
            stream.iterator().asSequence()
                .filter { it.isRegularFile() && it.extension.lowercase() in IMAGE_EXTENSIONS }
                .map { Photo(id = it.toAbsolutePath().toString(), name = it.name) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .toList()
        }
    }

    override suspend fun loadImage(photo: Photo, maxSize: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeScaled(Paths.get(photo.id).readBytes(), maxSize) }.getOrNull()
    }

    override suspend fun moveFiles(photos: List<Photo>, targetFolder: String): MoveResult =
        withContext(Dispatchers.IO) { movePhotos(photos, Paths.get(targetFolder)) }

    override suspend fun openFolder(folder: String) = withContext(Dispatchers.IO) {
        val os = System.getProperty("os.name").lowercase()
        when {
            "win" in os -> ProcessBuilder("explorer.exe", File(folder).absolutePath).start()
            java.awt.Desktop.isDesktopSupported() &&
                java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN) ->
                java.awt.Desktop.getDesktop().open(File(folder))
            else -> ProcessBuilder("xdg-open", folder).start()
        }
        Unit
    }

    override fun suggestTargetFolder(folder: String): String = File(folder, "Selected").absolutePath

    override fun isSameFolder(a: String, b: String): Boolean {
        val pa = Paths.get(a).toAbsolutePath().normalize()
        val pb = Paths.get(b).toAbsolutePath().normalize()
        if (pa == pb) return true
        return runCatching { pa.exists() && pb.exists() && Files.isSameFile(pa, pb) }.getOrDefault(false)
    }

    override suspend fun readProgressFiles(): List<String> = withContext(Dispatchers.IO) {
        if (!progressDir.isDirectory()) return@withContext emptyList()
        Files.list(progressDir).use { stream -> stream.toList() }
            .filter { it.isRegularFile() && it.extension == PROGRESS_EXTENSION }
            .sortedByDescending { it.getLastModifiedTime() }
            .mapNotNull { runCatching { it.readText() }.getOrNull() }
    }

    override suspend fun writeProgressFile(folder: String, content: String) = withContext(Dispatchers.IO) {
        Files.createDirectories(progressDir)
        val file = progressFile(folder)
        // Write to a temporary file first, so a crash never leaves a half-written progress file
        val temp = Files.createTempFile(progressDir, file.nameWithoutExtension, ".tmp")
        try {
            temp.writeText(content)
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        Unit
    }

    override suspend fun deleteProgressFile(folder: String) = withContext(Dispatchers.IO) {
        Files.deleteIfExists(progressFile(folder))
        Unit
    }

    /** One file per source folder; the name is derived from the normalized folder path. */
    private fun progressFile(folder: String): Path {
        var key = Paths.get(folder).toAbsolutePath().normalize().toString()
        if (isWindows) key = key.lowercase()
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return progressDir.resolve("${hash.take(32)}.$PROGRESS_EXTENSION")
    }

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        private const val PROGRESS_EXTENSION = "txt"

        private val isWindows = "win" in System.getProperty("os.name").lowercase()

        /** The user's pictures folder, or `null` if it cannot be found. Determined once. */
        val picturesFolder: String? by lazy { findPicturesFolder() }

        private fun findPicturesFolder(): String? {
            val home = System.getProperty("user.home")
            val candidates = if (isWindows) {
                // The registry knows the real location, also when it was moved (e.g. to OneDrive)
                listOfNotNull(windowsShellFolder("My Pictures"), Paths.get(home, "Pictures").toString())
            } else {
                // Localized on Linux, e.g. ~/Bilder on a German Ubuntu
                listOfNotNull(xdgUserDir("XDG_PICTURES_DIR", home), Paths.get(home, "Pictures").toString())
            }
            return candidates.firstOrNull { File(it).isDirectory }
        }

        private fun windowsShellFolder(name: String): String? = runCatching {
            val process = ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders",
                "/v", name,
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            // Line format: "    My Pictures    REG_SZ    C:\Users\me\Pictures"
            output.lines().firstOrNull { "REG_SZ" in it }?.substringAfter("REG_SZ")?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        /** Reads a folder from `~/.config/user-dirs.dirs`, e.g. `XDG_PICTURES_DIR="$HOME/Bilder"`. */
        internal fun xdgUserDir(key: String, home: String, config: Path = Paths.get(home, ".config", "user-dirs.dirs")): String? =
            runCatching {
                config.toFile().readLines()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("$key=") }
                    ?.substringAfter('=')?.trim()?.removeSurrounding("\"")
                    ?.replace("\$HOME", home)
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()

        /** Windows: `%APPDATA%\PictureSorter\progress`, otherwise `$XDG_DATA_HOME/PictureSorter/progress`. */
        fun defaultProgressDir(): Path {
            val home = System.getProperty("user.home")
            val base = if (isWindows) {
                System.getenv("APPDATA") ?: Paths.get(home, "AppData", "Roaming").toString()
            } else {
                System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?: Paths.get(home, ".local", "share").toString()
            }
            return Paths.get(base, "PictureSorter", "progress")
        }

        private var lookAndFeelSet = false

        private fun ensureSystemLookAndFeel() {
            if (lookAndFeelSet) return
            lookAndFeelSet = true
            runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        }

        /**
         * Moves the files. Never overwrites: if the name already exists in the target,
         * the file gets a suffix `_1`, `_2`, …
         */
        internal fun movePhotos(photos: List<Photo>, target: Path): MoveResult {
            Files.createDirectories(target)
            var moved = 0
            val failures = mutableListOf<MoveFailure>()
            for (photo in photos) {
                val source = Paths.get(photo.id)
                try {
                    moveWithoutOverwrite(source, target)
                    moved++
                } catch (e: Exception) {
                    failures += MoveFailure(photo, e.message ?: e::class.simpleName.orEmpty())
                }
            }
            return MoveResult(target.toAbsolutePath().toString(), moved, failures)
        }

        private fun moveWithoutOverwrite(source: Path, targetDir: Path): Path {
            var counter = 0
            while (true) {
                val name = if (counter == 0) {
                    source.name
                } else {
                    val ext = source.extension
                    "${source.nameWithoutExtension}_$counter" + if (ext.isEmpty()) "" else ".$ext"
                }
                val destination = targetDir.resolve(name)
                if (!destination.exists()) {
                    try {
                        // Without REPLACE_EXISTING: fails instead of overwriting. Across drives,
                        // Files.move copies internally and then deletes the source.
                        return Files.move(source, destination)
                    } catch (_: FileAlreadyExistsException) {
                        // Appeared in the meantime – try the next name.
                    }
                }
                counter++
            }
        }

        /**
         * Decodes and scales down a picture. Skia already applies the EXIF orientation (portrait phone photos)
         * while decoding, so width/height are already the display dimensions.
         */
        private fun decodeScaled(bytes: ByteArray, maxSize: Int): ImageBitmap {
            Image.makeFromEncoded(bytes).use { image ->
                val w = image.width
                val h = image.height
                val scale = min(1f, maxSize.toFloat() / max(w, h))
                val outW = max(1, (w * scale).roundToInt())
                val outH = max(1, (h * scale).roundToInt())

                Surface.makeRasterN32Premul(outW, outH).use { surface ->
                    surface.canvas.drawImageRect(
                        image,
                        Rect.makeWH(w.toFloat(), h.toFloat()),
                        Rect.makeWH(outW.toFloat(), outH.toFloat()),
                        FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                        null,
                        true,
                    )
                    return surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
                }
            }
        }
    }
}
