package ruebe.picturesorter.ui

import androidx.compose.ui.graphics.Color
import ruebe.picturesorter.model.Rating

// Muted pastel tones instead of loud primary colors
val NoColor = Color(0xFFE6A5A0)
val MaybeColor = Color(0xFFEFD08E)
val YesColor = Color(0xFFA8CFA4)

/** Text/symbol color on the pastel surfaces (dark for good contrast). */
val OnRatingColor = Color(0xFF2F2F2F)

val Rating.color: Color
    get() = when (this) {
        Rating.YES -> YesColor
        Rating.MAYBE -> MaybeColor
        Rating.NO -> NoColor
    }

val Rating.label: String
    get() = when (this) {
        Rating.YES -> "Yes"
        Rating.MAYBE -> "Maybe"
        Rating.NO -> "No"
    }

val Rating.symbol: String
    get() = when (this) {
        Rating.YES -> "✓"
        Rating.MAYBE -> "?"
        Rating.NO -> "✕"
    }
