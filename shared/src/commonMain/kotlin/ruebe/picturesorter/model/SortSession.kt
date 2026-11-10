package ruebe.picturesorter.model

enum class Rating { YES, NO, MAYBE }

/** A picture. [id] is platform specific (desktop: absolute file path). */
data class Photo(val id: String, val name: String)

enum class SelectionMode { YES_AND_MAYBE, YES_ONLY }

data class RoundSummary(val yes: Int, val maybe: Int, val no: Int)

/**
 * Immutable state of a sorting session.
 *
 * A round shows all pictures in [queue] one after another. The picture being rated is always [current].
 * Follow-up rounds contain all yes and maybe pictures of the previous round; their previous rating
 * stays visible via [previousRatings].
 */
data class SortSession(
    val round: Int,
    val queue: List<Photo>,
    val ratings: Map<String, Rating> = emptyMap(),
    val previousRatings: Map<String, Rating> = emptyMap(),
) {
    constructor(photos: List<Photo>) : this(round = 1, queue = photos)

    /** Pictures are rated strictly in order, so the index equals the number of ratings. */
    val currentIndex: Int get() = ratings.size

    val current: Photo? get() = queue.getOrNull(currentIndex)

    val isRoundFinished: Boolean get() = currentIndex >= queue.size

    val canUndo: Boolean get() = currentIndex > 0

    fun previousRatingOf(photo: Photo): Rating? = previousRatings[photo.id]

    fun rate(rating: Rating): SortSession {
        val photo = current ?: return this
        return copy(ratings = ratings + (photo.id to rating))
    }

    fun undo(): SortSession {
        if (!canUndo) return this
        return copy(ratings = ratings - queue[currentIndex - 1].id)
    }

    fun summary(): RoundSummary {
        val values = ratings.values
        return RoundSummary(
            yes = values.count { it == Rating.YES },
            maybe = values.count { it == Rating.MAYBE },
            no = values.count { it == Rating.NO },
        )
    }

    fun selection(mode: SelectionMode): List<Photo> = queue.filter { photo ->
        when (ratings[photo.id]) {
            Rating.YES -> true
            Rating.MAYBE -> mode == SelectionMode.YES_AND_MAYBE
            Rating.NO, null -> false
        }
    }

    fun startNextRound(): SortSession = SortSession(
        round = round + 1,
        queue = selection(SelectionMode.YES_AND_MAYBE),
        ratings = emptyMap(),
        previousRatings = ratings,
    )
}
