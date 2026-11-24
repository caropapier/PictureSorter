package ruebe.picturesorter.model

/** One picture of a saved round, identified by its file name inside the source folder. */
data class SavedEntry(val name: String, val rating: Rating?, val previousRating: Rating?)

/**
 * Sorting progress of one source folder, stored so that sorting can be continued later.
 * [entries] are in queue order; the rated pictures always come first.
 */
data class SavedProgress(val folder: String, val round: Int, val entries: List<SavedEntry>) {

    val total: Int get() = entries.size

    val ratedCount: Int get() = entries.count { it.rating != null }

    /** Simple line based text format, independent of any serialization library. */
    fun encode(): String = buildString {
        append(HEADER).append('\n')
        append("folder\t").append(escape(folder)).append('\n')
        append("round\t").append(round).append('\n')
        for (entry in entries) {
            append("photo\t").append(escape(entry.name))
                .append('\t').append(entry.rating.code())
                .append('\t').append(entry.previousRating.code())
                .append('\n')
        }
    }

    companion object {
        private const val HEADER = "PictureSorter progress v1"

        fun of(folder: String, session: SortSession) = SavedProgress(
            folder = folder,
            round = session.round,
            entries = session.queue.map { SavedEntry(it.name, session.ratings[it.id], session.previousRatings[it.id]) },
        )

        /** Returns `null` if the text is not a valid progress file. */
        fun decode(text: String): SavedProgress? {
            val lines = text.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotEmpty() }
            if (lines.firstOrNull() != HEADER) return null
            var folder: String? = null
            var round: Int? = null
            val entries = mutableListOf<SavedEntry>()
            for (line in lines.drop(1)) {
                val parts = line.split('\t')
                when (parts[0]) {
                    "folder" -> folder = parts.getOrNull(1)?.let(::unescape)
                    "round" -> round = parts.getOrNull(1)?.toIntOrNull()
                    "photo" -> {
                        if (parts.size != 4) return null
                        entries += SavedEntry(unescape(parts[1]), parseRating(parts[2]), parseRating(parts[3]))
                    }
                }
            }
            if (folder == null || round == null || round < 1) return null
            return SavedProgress(folder, round, entries)
        }

        private fun Rating?.code(): String = when (this) {
            Rating.YES -> "Y"
            Rating.NO -> "N"
            Rating.MAYBE -> "M"
            null -> "-"
        }

        private fun parseRating(code: String): Rating? = when (code) {
            "Y" -> Rating.YES
            "N" -> Rating.NO
            "M" -> Rating.MAYBE
            else -> null
        }

        private fun escape(s: String): String = buildString {
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }

        private fun unescape(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(s[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}

/** A session rebuilt from [SavedProgress]. [missingCount] pictures were saved but are no longer in the folder. */
data class RestoredSession(val session: SortSession, val missingCount: Int)
