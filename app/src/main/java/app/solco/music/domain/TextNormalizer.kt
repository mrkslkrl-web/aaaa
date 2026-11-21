package app.solco.music.domain

import java.text.Normalizer

object TextNormalizer {

    private val DIACRITICS_REGEX = Regex("[\\u0300-\\u036f]")
    private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}]+")
    private val FEAT_BRACKET_REGEX =
        Regex("\\s*[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring|with|con)\\s[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
    private val FEAT_DASH_REGEX =
        Regex("\\s+[-–—]\\s+(?:feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
    private val FEAT_INLINE_REGEX =
        Regex("\\s+(?:feat\\.|ft\\.|featuring)\\s.*$", RegexOption.IGNORE_CASE)
    private val FEAT_WORD_REGEX =
        Regex("\\s+feat\\s.*$", RegexOption.IGNORE_CASE)

    private val EDITION_BRACKET_REGEX = Regex(
        "\\s*[(\\[]\\s*(?:deluxe|super deluxe|expanded|complete|collector'?s?|special|anniversary|remaster(?:ed)?|reissue|bonus track(?:s)?|explicit|clean|digital|standard|legacy|platinum|gold|edition|version|intl|international|mono|stereo|live|tour|\\d{4}\\s*remaster)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE
    )
    private val EDITION_DASH_REGEX = Regex(
        "\\s+[-–—]\\s+(?:deluxe|expanded|remaster(?:ed)?|single|ep|explicit|clean|radio edit|album version|bonus track|live|\\d{4}\\s*remaster).*$",
        RegexOption.IGNORE_CASE
    )
    private val SINGLE_EP_SUFFIX_REGEX = Regex("\\s*-\\s*(?:Single|EP)$", RegexOption.IGNORE_CASE)
    private val SPOTIFY_ID_REGEX = Regex("^[A-Za-z0-9]{22}$")

    /**
     * Exact match with Solco's `ot()` normalizer.
     */
    fun norm(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val nfkd = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFKD)
        val noDiacritics = DIACRITICS_REGEX.replace(nfkd, "")
        val amp = noDiacritics.replace("&", " and ")
        return NON_ALNUM_REGEX.replace(amp, " ").trim()
    }

    /**
     * Strip featuring annotations, matching Solco's `aE()`.
     */
    fun stripFeaturing(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw
        s = FEAT_BRACKET_REGEX.replace(s, "")
        s = FEAT_DASH_REGEX.replace(s, "")
        s = FEAT_INLINE_REGEX.replace(s, "")
        s = FEAT_WORD_REGEX.replace(s, "")
        return s.trim()
    }

    /**
     * Clean track title for matching (`Ue()` + edition/remaster stripping).
     */
    fun cleanTrackTitle(raw: String?): String {
        val base = stripFeaturing(raw)
        val noEd = EDITION_DASH_REGEX.replace(EDITION_BRACKET_REGEX.replace(base, ""), "").trim()
        return norm(noEd).ifEmpty { norm(base) }.ifEmpty { norm(raw) }
    }

    /**
     * Clean album title for canonical edition deduplication.
     */
    fun cleanAlbumTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = SINGLE_EP_SUFFIX_REGEX.replace(raw, "")
        s = EDITION_BRACKET_REGEX.replace(s, "")
        s = EDITION_DASH_REGEX.replace(s, "")
        return norm(s).ifEmpty { norm(raw) }
    }

    fun normSongKey(raw: String?): String = norm(stripFeaturing(raw)).ifEmpty { norm(raw) }

    fun nameKey(artist: String, track: String, album: String): String =
        "${norm(artist)} ${norm(track)} ${norm(album)}"

    /**
     * Bit-for-bit compatible implementation of Solco's `xR()` (cyrb53) hash for `names` doc IDs.
     */
    fun nameIdHash(input: String): String {
        var h1 = 0xdeadbeef.toInt()
        var h2 = 0x41c6ce57
        for (i in input.indices) {
            val ch = input[i].code
            h1 = (h1 xor ch) * 2654435761.toInt()
            h2 = (h2 xor ch) * 1597334677
        }
        h1 = ((h1 xor (h1 ushr 16)) * 2246822507.toInt()) xor ((h2 xor (h2 ushr 13)) * 3266489909.toInt())
        h2 = ((h2 xor (h2 ushr 16)) * 2246822507.toInt()) xor ((h1 xor (h1 ushr 13)) * 3266489909.toInt())
        val combined = 4294967296L * (2097151L and h2.toLong()) + (h1.toLong() and 0xffffffffL)
        return "n" + combined.toString(36)
    }

    fun isSpotifyId(id: String?): Boolean = !id.isNullOrBlank() && SPOTIFY_ID_REGEX.matches(id)

    fun extractSpotifyId(input: String?, kind: String): String? {
        val s = input?.trim().orEmpty()
        if (s.isEmpty()) return null
        val regex = Regex("(?:spotify:$kind:|open\\.spotify\\.com/(?:intl-[a-z-]+/)?$kind/)([A-Za-z0-9]{22})")
        regex.find(s)?.groupValues?.getOrNull(1)?.let { return it }
        return if (SPOTIFY_ID_REGEX.matches(s)) s else null
    }

    fun syntheticArtistId(name: String): String = "n:" + norm(name)
}
