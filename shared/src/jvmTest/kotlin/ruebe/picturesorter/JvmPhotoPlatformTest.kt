package ruebe.picturesorter

import kotlinx.coroutines.runBlocking
import ruebe.picturesorter.model.Photo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmPhotoPlatformTest {
    private val root: Path = Files.createTempDirectory("picturesorter-test")
    private val source = root.resolve("source").createDirectories()
    private val target = root.resolve("target")
    private val progressDir = root.resolve("progress")
    private val platform = JvmPhotoPlatform(progressDir = progressDir)

    @AfterTest
    fun cleanup() {
        root.toFile().deleteRecursively()
    }

    private fun photo(name: String, content: String = name): Photo {
        val file = source.resolve(name)
        file.writeText(content)
        return Photo(file.toAbsolutePath().toString(), name)
    }

    @Test
    fun listImagesFiltersAndSorts() = runBlocking {
        photo("b.JPG"); photo("a.png"); photo("notes.txt"); photo("c.webp")
        source.resolve("subfolder.jpg").createDirectories()
        val names = platform.listImages(source.toString()).map { it.name }
        assertEquals(listOf("a.png", "b.JPG", "c.webp"), names)
    }

    @Test
    fun moveFilesMovesInsteadOfCopying() = runBlocking {
        val a = photo("a.jpg")
        val b = photo("b.jpg")
        val keep = photo("stays.jpg")

        val result = platform.moveFiles(listOf(a, b), target.toString())

        assertEquals(2, result.movedCount)
        assertTrue(result.failures.isEmpty())
        assertTrue(target.resolve("a.jpg").exists())
        assertTrue(target.resolve("b.jpg").exists())
        assertFalse(source.resolve("a.jpg").exists(), "Source must be gone after moving")
        assertFalse(source.resolve("b.jpg").exists())
        assertTrue(source.resolve(keep.name).exists())
    }

    @Test
    fun moveFilesNeverOverwrites() = runBlocking {
        target.createDirectories()
        target.resolve("a.jpg").writeText("already there")
        target.resolve("a_1.jpg").writeText("also already there")
        val a = photo("a.jpg", "new")

        val result = platform.moveFiles(listOf(a), target.toString())

        assertEquals(1, result.movedCount)
        assertEquals("already there", target.resolve("a.jpg").readText())
        assertEquals("also already there", target.resolve("a_1.jpg").readText())
        assertEquals("new", target.resolve("a_2.jpg").readText())
        assertFalse(source.resolve("a.jpg").exists())
    }

    @Test
    fun missingSourceIsReportedAsFailure() = runBlocking {
        val ghost = Photo(source.resolve("gone.jpg").toString(), "gone.jpg")
        val ok = photo("ok.jpg")
        val result = platform.moveFiles(listOf(ghost, ok), target.toString())
        assertEquals(1, result.movedCount)
        assertEquals(listOf("gone.jpg"), result.failures.map { it.photo.name })
    }

    @Test
    fun loadImageScalesDown() = runBlocking {
        val file = source.resolve("large.png")
        javax.imageio.ImageIO.write(
            java.awt.image.BufferedImage(4000, 1000, java.awt.image.BufferedImage.TYPE_INT_RGB),
            "png",
            file.toFile(),
        )
        val bitmap = platform.loadImage(Photo(file.toString(), "large.png"), maxSize = 2000)
        assertEquals(2000, bitmap?.width)
        assertEquals(500, bitmap?.height)
    }

    @Test
    fun loadImageAppliesExifRotation() = runBlocking {
        // JPEG 400x200 with EXIF orientation 6 (90° clockwise) -> displayed as 200x400
        val jpeg = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(
                java.awt.image.BufferedImage(400, 200, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", it,
            )
        }.toByteArray()
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big-endian header, IFD at offset 8
            0x00, 0x01, // 1 entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00, // Orientation = 6
            0x00, 0x00, 0x00, 0x00, // no next IFD
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((payload.size + 2) shr 8).toByte(), (payload.size + 2).toByte()) + payload
        val withExif = jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
        val file = source.resolve("portrait.jpg")
        Files.write(file, withExif)

        val bitmap = platform.loadImage(Photo(file.toString(), "portrait.jpg"), maxSize = 2000)
        assertEquals(200, bitmap?.width)
        assertEquals(400, bitmap?.height)
    }

    @Test
    fun sameFolderDetection() {
        assertTrue(platform.isSameFolder(source.toString(), source.resolve(".").toString()))
        assertFalse(platform.isSameFolder(source.toString(), target.toString()))
    }

    @Test
    fun progressFilesAreWrittenReplacedAndDeleted() = runBlocking {
        assertEquals(emptyList(), platform.readProgressFiles())

        platform.writeProgressFile(source.toString(), "first")
        platform.writeProgressFile(target.toString(), "other")
        // Same folder, written differently: replaces the first file instead of adding one
        platform.writeProgressFile(source.resolve(".").toString(), "second")

        assertEquals(setOf("second", "other"), platform.readProgressFiles().toSet())
        assertTrue(Files.list(progressDir).use { s -> s.noneMatch { it.toString().endsWith(".tmp") } })

        platform.deleteProgressFile(source.toString())
        assertEquals(listOf("other"), platform.readProgressFiles())
        platform.deleteProgressFile(source.toString()) // no error if already gone
    }

    @Test
    fun readsLocalizedPicturesFolderOnLinux() {
        val config = root.resolve("user-dirs.dirs")
        config.writeText("# comment\nXDG_DESKTOP_DIR=\"\$HOME/Schreibtisch\"\nXDG_PICTURES_DIR=\"\$HOME/Bilder\"\n")
        assertEquals("/home/me/Bilder", JvmPhotoPlatform.xdgUserDir("XDG_PICTURES_DIR", "/home/me", config))
        assertEquals(null, JvmPhotoPlatform.xdgUserDir("XDG_MUSIC_DIR", "/home/me", config))
    }

    @Test
    fun findsPicturesFolderOnThisMachine() {
        // Every desktop user account has one; at least it must be an existing folder if found
        JvmPhotoPlatform.picturesFolder?.let { assertTrue(java.io.File(it).isDirectory) }
        println("Pictures folder: ${JvmPhotoPlatform.picturesFolder}")
    }
}
