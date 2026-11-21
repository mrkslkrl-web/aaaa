package app.solco.music.data.model

import kotlinx.serialization.Serializable

/**
 * Raw Firestore-compatible records mirroring Solco's existing schema under users/{uid}/{collection}.
 */
@Serializable
data class KvRecord(
    val k: String,
    val vJson: String = "null",
    val u: Long = 0L
)

@Serializable
data class ChunkRecord(
    val id: String,
    val src: String, // "sp" or "lfm"
    val dataJson: String = "[]",
    val n: Int = 0,
    val u: Long = 0L
)

@Serializable
data class NameRecord(
    val id: String,
    val k: String = "",
    val artist: String = "",
    val track: String = "",
    val album: String = "",
    val tid: String? = null,
    val st: String = "new", // "new", "ok", "ign", "err"
    val tries: Int = 0,
    val nt: Long = 0L,
    val u: Long = 0L
)

@Serializable
data class ArtistRef(
    val id: String? = null,
    val name: String = ""
)

@Serializable
data class TrackHint(
    val an: String? = null,
    val al: String? = null
)

@Serializable
data class TrackRecord(
    val id: String,
    val name: String = "?",
    val album: String? = null,
    val dur: Long = 0L,
    val isrc: String? = null,
    val artistsJson: String = "[]",
    val hintJson: String? = null,
    val st: String = "stub", // "ok", "part", "stub", "err"
    val disc: Int = 1,
    val num: Int = 0,
    val tries: Int = 0,
    val u: Long = 0L
)

@Serializable
data class AlbumRecord(
    val id: String,
    val name: String = "…",
    val type: String = "",
    val total: Int? = null,
    val date: String = "",
    val img: String? = null,
    val artistsJson: String = "[]",
    val tracksJson: String = "[]",
    val st: String = "stub",
    val tries: Int = 0,
    val u: Long = 0L
)

@Serializable
data class ArtistRecord(
    val id: String,
    val name: String = "…",
    val img: String? = null,
    val st: String = "stub",
    val tries: Int = 0,
    val u: Long = 0L
)

@Serializable
data class NoteRecord(
    val id: String,
    val et: String, // "artist", "album", "track"
    val eid: String,
    val text: String = "",
    val del: Int = 0,
    val u: Long = 0L
)

@Serializable
data class TrackReviewEntry(
    val mood: Int? = null,
    val text: String? = null
)

@Serializable
data class ReviewRecord(
    val id: String,
    val et: String, // "artist", "album", "track"
    val eid: String,
    val rating: Double? = null,
    val mood: Int? = null,
    val text: String = "",
    val day: String = "",
    val tracksJson: String? = null,
    val created: Long = 0L,
    val del: Int = 0,
    val u: Long = 0L
)

// --- Config & KV typed payloads ---

@Serializable
data class LastFmConfig(
    val user: String = "",
    val key: String = ""
)

@Serializable
data class SpotifyConfig(
    val id: String = "",
    val secret: String = ""
)

@Serializable
data class LastFmProfile(
    val name: String = "",
    val playcount: Long = 0L,
    val registered: Long = 0L
)

@Serializable
data class LastFmState(
    val last: Long = 0L
)

@Serializable
data class NowPlaying(
    val artist: String = "",
    val track: String = "",
    val album: String = "",
    val img: String? = null,
    val at: Long = 0L
)

@Serializable
data class LastFmSyncStatus(
    val ok: Boolean = true,
    val at: Long = 0L,
    val added: Int = 0,
    val done: Boolean = true,
    val error: String? = null
)

@Serializable
data class GoalRule(
    val start: String = "",
    val per: Int = 1
)

@Serializable
data class GoalPause(
    val from: Long = 0L,
    val to: Long? = null
)

@Serializable
data class GoalsConfig(
    val album: GoalRule = GoalRule(),
    val track: GoalRule = GoalRule(),
    val pauses: List<GoalPause> = emptyList()
)

@Serializable
data class MergeConfig(
    val join: List<List<String>> = emptyList(),
    val split: List<String> = emptyList()
)

@Serializable
data class ReleaseItem(
    val id: String,
    val sid: String = "",
    val src: String = "sp", // "sp", "dz", "am"
    val title: String,
    val artists: List<String> = emptyList(),
    val by: List<String> = emptyList(),
    val img: String? = null,
    val date: String = "",
    val seen: String = "",
    val tracks: Int = 0,
    val type: String = "single", // "album" or "single"
    val link: String? = null,
    val savedAt: Long? = null,
    val isFeature: Boolean = false
)

@Serializable
data class UwuTournament(
    val id: String,
    val name: String,
    val kind: String, // "artist", "album", "track"
    val seed: List<String?> = emptyList(),
    val picks: List<Int> = emptyList(),
    val created: Long = 0L,
    val updated: Long = 0L
)

// --- Domain computed models ---

enum class Mood(val value: Int, val label: String, val colorHex: Long, val baseScore: Double) {
    AWFUL(1, "Che schifo", 0xFFFF4D6D, 1.5),
    DISLIKE(2, "Non mi piace", 0xFFFF8C42, 3.75),
    OK(3, "Normale", 0xFFFFD166, 6.0),
    GOOD(4, "Bello", 0xFF2EC4B6, 8.25),
    AMAZING(5, "Spettacolare", 0xFF5B8CFF, 10.0);

    companion object {
        fun fromValue(v: Int?): Mood? = entries.find { it.value == v }
    }
}

enum class TimePeriod(val key: String, val label: String) {
    ALL("all", "Sempre"),
    D7("d7", "7 giorni"),
    D30("d30", "30 giorni"),
    D90("d90", "3 mesi"),
    Y1("y1", "1 anno");

    companion object {
        fun fromKey(k: String): TimePeriod? = entries.find { it.key == k }
    }
}

data class PlayEvent(
    val ts: Long,
    val trackId: String,
    val ms: Long,
    val nameId: String?,
    val isLfm: Boolean
)

data class ResolvedArtist(
    val id: String,
    val name: String,
    val img: String?,
    val real: Boolean,
    val aliases: List<String> = emptyList()
)

data class ResolvedAlbum(
    val id: String,
    val name: String,
    val type: String,
    val total: Int,
    val date: String,
    val img: String?,
    val artists: List<Pair<String, String>>,
    val tracks: List<String>,
    val st: String,
    val editions: List<String> = listOf(id),
    val aliasOf: String? = null
)

data class ResolvedTrack(
    val id: String,
    val name: String,
    val albumId: String?,
    val albumHint: String?,
    val dur: Long,
    val isrc: String?,
    val artists: List<Pair<String, String>>,
    val st: String,
    val disc: Int,
    val num: Int
)

data class ResolvedSong(
    val id: String,
    val title: String,
    val versions: List<String>,
    val dur: Long,
    val img: String?,
    val date: String,
    val artists: List<Pair<String, String>>,
    val mainArtists: Set<String>,
    val albums: List<String>,
    val isSingle: Boolean,
    val inAlbum: Boolean,
    val resolved: Boolean
)

data class UnresolvedEntry(
    val nameId: String,
    val artist: String,
    val track: String,
    val album: String,
    val count: Int,
    val firstTs: Long,
    val lastTs: Long,
    val tries: Int
)

data class SongStat(
    val plays: Int,
    val ms: Long,
    val firstTs: Long,
    val lastTs: Long
)

data class AlbumStat(
    val fullPlays: Int,
    val totalPlays: Int,
    val directPlays: Int,
    val ms: Long,
    val lastTs: Long,
    val songCount: Int,
    val skippedCount: Int,
    val skippedSongIds: Set<String>,
    val minSongPlays: Int
)

data class ArtistStat(
    val plays: Int,
    val ms: Long,
    val songs: Int,
    val firstTs: Long,
    val lastTs: Long
)

data class PeriodStats(
    val periodKey: String,
    val song: Map<String, SongStat>,
    val version: Map<String, Int>,
    val album: Map<String, AlbumStat>,
    val artist: Map<String, ArtistStat>,
    val totalPlays: Int,
    val totalMs: Long
)

data class SolcoModel(
    val artists: Map<String, ResolvedArtist> = emptyMap(),
    val artistCanon: Map<String, String> = emptyMap(),
    val albums: Map<String, ResolvedAlbum> = emptyMap(),
    val albumCanon: Map<String, String> = emptyMap(),
    val tracks: Map<String, ResolvedTrack> = emptyMap(),
    val songs: Map<String, ResolvedSong> = emptyMap(),
    val songOf: Map<String, String> = emptyMap(),
    val discOff: Map<String, List<Int>> = emptyMap(),
    val plays: List<PlayEvent> = emptyList(),
    val unresolved: Map<String, UnresolvedEntry> = emptyMap(),
    val cutoff: Long = 0L,
    val spCount: Int = 0,
    val lfmCount: Int = 0,
    val years: List<Int> = emptyList(),
    val firstPlay: Long = 0L
)

data class GoalProgress(
    val balance: Int,
    val count: Int,
    val todayCount: Int,
    val elapsed: Int,
    val paused: Boolean
)

data class ReleaseScanState(
    val scanning: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val phase: String = "",
    val source: String = "",
    val error: String? = null,
    val lastRunMs: Long = 0L
)
