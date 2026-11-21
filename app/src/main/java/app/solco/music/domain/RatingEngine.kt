package app.solco.music.domain

import app.solco.music.data.model.Mood
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.model.TrackReviewEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.round

data class CalculatedTrackScore(
    val score: Double,
    val ratedCount: Int,
    val rawAverage: Double
)

/**
 * Bayesian length-aware rating calculator & independent sorting engine.
 *
 * Fixes two issues reported by the user:
 * 1. Short albums with all "Bello" tracks now score slightly lower than longer albums
 *    with all "Bello" tracks via a Bayesian shrinkage anchor toward "Normale" (6.0).
 * 2. Hiding the calculated score in the UI no longer breaks high-to-low sorting:
 *    [sortScore] ALWAYS evaluates the track-derived score first, regardless of display visibility.
 */
object RatingEngine {

    private const val PRIOR_NEUTRAL = 6.0 // Normale
    private const val PRIOR_WEIGHT = 2.0  // Gentle length-confidence prior
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseTrackReviews(tracksJson: String?): Map<String, TrackReviewEntry> {
        if (tracksJson.isNullOrBlank() || tracksJson == "null") return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, TrackReviewEntry>>(tracksJson)
        }.getOrDefault(emptyMap())
    }

    fun encodeTrackReviews(map: Map<String, TrackReviewEntry>): String? {
        if (map.isEmpty()) return null
        return json.encodeToString(map)
    }

    /**
     * Computes a 1.0–10.0 Bayesian length-weighted score from track moods.
     *
     * Examples (all "Bello" = 8.25):
     * - 4 tracks:  (4*8.25 + 2*6.0) / 6  = 7.5
     * - 8 tracks:  (8*8.25 + 2*6.0) / 10 = 7.8
     * - 14 tracks: (14*8.25 + 2*6.0)/ 16 = 8.0
     * - 20 tracks: (20*8.25 + 2*6.0)/ 22 = 8.0 (8.05 -> 8.1)
     *
     * Examples (all "Spettacolare" = 10.0):
     * - 4 tracks:  8.7
     * - 10 tracks: 9.3
     * - 16 tracks: 9.6
     */
    fun computeFromTracks(tracks: Map<String, TrackReviewEntry>): CalculatedTrackScore? {
        val moods = tracks.values.mapNotNull { Mood.fromValue(it.mood) }
        if (moods.isEmpty()) return null
        val n = moods.size
        val sum = moods.sumOf { it.baseScore }
        val rawAvg = sum / n
        val bayesian = (sum + PRIOR_WEIGHT * PRIOR_NEUTRAL) / (n + PRIOR_WEIGHT)
        val rounded = (round(bayesian * 10.0) / 10.0).coerceIn(1.0, 10.0)
        return CalculatedTrackScore(
            score = rounded,
            ratedCount = n,
            rawAverage = round(rawAvg * 10.0) / 10.0
        )
    }

    fun computeFromTracksJson(tracksJson: String?): CalculatedTrackScore? =
        computeFromTracks(parseTrackReviews(tracksJson))

    /**
     * Exact score used for sorting reviews from highest to lowest.
     * IMPORTANT: Always independent of whether the user hides the calculated score visually!
     */
    fun sortScore(review: ReviewRecord): Double {
        val fromTracks = computeFromTracksJson(review.tracksJson)
        if (fromTracks != null) return fromTracks.score
        Mood.fromValue(review.mood)?.let { return it.baseScore }
        return review.rating ?: -1.0
    }

    /**
     * Score or mood to display on a review row/card depending on [showCalculatedScore].
     */
    fun displayScore(review: ReviewRecord, showCalculatedScore: Boolean): Pair<Double?, Mood?> {
        if (showCalculatedScore) {
            val calc = computeFromTracksJson(review.tracksJson)
            if (calc != null) return calc.score to null
        }
        return review.rating to Mood.fromValue(review.mood)
    }

    fun formatScore(score: Double): String {
        val rounded = round(score * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(rounded)
    }
}
