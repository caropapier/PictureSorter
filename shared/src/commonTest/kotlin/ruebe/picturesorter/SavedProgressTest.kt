package ruebe.picturesorter

import ruebe.picturesorter.model.Photo
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.SavedEntry
import ruebe.picturesorter.model.SavedProgress
import ruebe.picturesorter.model.SortSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedProgressTest {
    private fun photos(vararg names: String) = names.map { Photo("/pics/$it", it) }

    @Test
    fun encodeDecodeRoundTrip() {
        val progress = SavedProgress(
            folder = "C:\\Users\\me\\Pictures\\Holiday 2026",
            round = 2,
            entries = listOf(
                SavedEntry("a.jpg", Rating.YES, Rating.MAYBE),
                SavedEntry("tab\there\\and\nnewline.jpg", Rating.NO, Rating.YES),
                SavedEntry("Ünïcødé 🙂.png", null, Rating.YES),
            ),
        )
        assertEquals(progress, SavedProgress.decode(progress.encode()))
        // Windows line endings (e.g. file edited by hand) are accepted as well
        assertEquals(progress, SavedProgress.decode(progress.encode().replace("\n", "\r\n")))
    }

    @Test
    fun decodeRejectsInvalidText() {
        assertNull(SavedProgress.decode(""))
        assertNull(SavedProgress.decode("something else\nfolder\tx\nround\t1\n"))
        assertNull(SavedProgress.decode("PictureSorter progress v1\nround\t1\n"))
    }

    @Test
    fun sessionSurvivesSaveAndRestore() {
        val photos = photos("1.jpg", "2.jpg", "3.jpg", "4.jpg")
        val first = SortSession(photos).rate(Rating.YES).rate(Rating.NO).rate(Rating.MAYBE).rate(Rating.YES)
        val second = first.startNextRound().rate(Rating.NO)

        val saved = SavedProgress.decode(SavedProgress.of("/pics", second).encode())!!
        val restored = SortSession.restore(saved, photos)

        // Previous ratings are only kept for pictures that are still in the round (the only ones shown)
        val expected = second.copy(previousRatings = second.previousRatings.filterKeys { id -> second.queue.any { it.id == id } })
        assertEquals(expected, restored.session)
        assertEquals(0, restored.missingCount)
        assertEquals(3, saved.total)
        assertEquals(1, saved.ratedCount)
    }

    @Test
    fun restoreSkipsMissingAndAppendsNewPicturesInRoundOne() {
        val before = photos("a.jpg", "b.jpg", "c.jpg", "d.jpg")
        val session = SortSession(before).rate(Rating.YES).rate(Rating.NO).rate(Rating.MAYBE)
        val saved = SavedProgress.of("/pics", session)

        // b.jpg (rated) and d.jpg (unrated) were deleted, e.jpg was added
        val now = photos("a.jpg", "c.jpg", "e.jpg")
        val restored = SortSession.restore(saved, now)

        assertEquals(2, restored.missingCount)
        assertEquals(now, restored.session.queue)
        assertEquals(mapOf("/pics/a.jpg" to Rating.YES, "/pics/c.jpg" to Rating.MAYBE), restored.session.ratings)
        assertEquals(now[2], restored.session.current)
    }

    @Test
    fun restoreDoesNotAddNewPicturesInLaterRounds() {
        val before = photos("a.jpg", "b.jpg")
        val round2 = SortSession(before).rate(Rating.YES).rate(Rating.NO).startNextRound()
        val saved = SavedProgress.of("/pics", round2)

        val restored = SortSession.restore(saved, photos("a.jpg", "b.jpg", "new.jpg"))

        assertEquals(photos("a.jpg"), restored.session.queue)
        assertTrue(restored.session.ratings.isEmpty())
        assertEquals(Rating.YES, restored.session.previousRatingOf(before[0]))
    }
}
