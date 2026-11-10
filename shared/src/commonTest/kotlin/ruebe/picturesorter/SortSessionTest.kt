package ruebe.picturesorter

import ruebe.picturesorter.model.Photo
import ruebe.picturesorter.model.Rating
import ruebe.picturesorter.model.RoundSummary
import ruebe.picturesorter.model.SelectionMode
import ruebe.picturesorter.model.SortSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SortSessionTest {
    private val photos = (1..5).map { Photo("p$it", "picture$it.jpg") }

    private fun rateAll(vararg ratings: Rating): SortSession =
        ratings.fold(SortSession(photos)) { s, r -> s.rate(r) }

    @Test
    fun ratesPhotosInOrder() {
        var s = SortSession(photos)
        assertEquals(photos[0], s.current)
        s = s.rate(Rating.YES)
        assertEquals(photos[1], s.current)
        assertEquals(1, s.currentIndex)
        assertFalse(s.isRoundFinished)
    }

    @Test
    fun finishesRoundAndCounts() {
        val s = rateAll(Rating.YES, Rating.NO, Rating.MAYBE, Rating.YES, Rating.NO)
        assertTrue(s.isRoundFinished)
        assertNull(s.current)
        assertEquals(RoundSummary(yes = 2, maybe = 1, no = 2), s.summary())
        // Further ratings after the round has ended change nothing
        assertEquals(s, s.rate(Rating.YES))
    }

    @Test
    fun undoRemovesLastRating() {
        val s = rateAll(Rating.YES, Rating.NO).undo()
        assertEquals(photos[1], s.current)
        assertEquals(mapOf("p1" to Rating.YES), s.ratings)
        assertFalse(SortSession(photos).canUndo)
        assertEquals(SortSession(photos), SortSession(photos).undo())
    }

    @Test
    fun selectionModes() {
        val s = rateAll(Rating.YES, Rating.NO, Rating.MAYBE, Rating.YES, Rating.NO)
        assertEquals(listOf(photos[0], photos[2], photos[3]), s.selection(SelectionMode.YES_AND_MAYBE))
        assertEquals(listOf(photos[0], photos[3]), s.selection(SelectionMode.YES_ONLY))
    }

    @Test
    fun nextRoundContainsYesAndMaybeAndRemembersPreviousRating() {
        val first = rateAll(Rating.YES, Rating.NO, Rating.MAYBE, Rating.YES, Rating.NO)
        val second = first.startNextRound()
        assertEquals(2, second.round)
        assertEquals(listOf(photos[0], photos[2], photos[3]), second.queue)
        assertEquals(photos[0], second.current)
        assertTrue(second.ratings.isEmpty())
        assertEquals(Rating.YES, second.previousRatingOf(photos[0]))
        assertEquals(Rating.MAYBE, second.previousRatingOf(photos[2]))

        // Round 2: maybe becomes yes, one yes becomes no
        val rated = second.rate(Rating.YES).rate(Rating.YES).rate(Rating.NO)
        assertEquals(RoundSummary(yes = 2, maybe = 0, no = 1), rated.summary())
        assertEquals(listOf(photos[0], photos[2]), rated.selection(SelectionMode.YES_ONLY))

        val third = rated.startNextRound()
        assertEquals(3, third.round)
        assertEquals(Rating.YES, third.previousRatingOf(photos[2]))
    }
}
