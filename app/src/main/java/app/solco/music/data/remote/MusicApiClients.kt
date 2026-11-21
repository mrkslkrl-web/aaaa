package app.solco.music.data.remote

import android.util.Base64
import app.solco.music.data.model.LastFmConfig
import app.solco.music.data.model.LastFmProfile
import app.solco.music.data.model.NowPlaying
import app.solco.music.data.model.ReleaseItem
import app.solco.music.data.model.SpotifyConfig
import app.solco.music.domain.ModelEngine
import app.solco.music.domain.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.LocalDate

data class ScrobbleItem(
    val uts: Long,
    val artist: String,
    val track: String,
    val album: String
)

data class RecentTracksPage(
    val totalPages: Int,
    val nowPlaying: NowPlaying?,
    val tracks: List<ScrobbleItem>
)

data class MatchedTrackResult(
    val trackId: String,
    val title: String,
    val albumId: String?,
    val albumName: String?,
    val albumType: String,
    val albumTotal: Int?,
    val albumDate: String,
    val albumImg: String?,
    val albumArtists: List<Pair<String, String>>,
    val trackArtists: List<Pair<String, String>>,
    val durMs: Long,
    val isrc: String?,
    val disc: Int,
    val trackNum: Int,
    val source: String // "sp", "dz", "am"
)

data class FullAlbumResult(
    val id: String,
    val name: String,
    val type: String,
    val total: Int,
    val date: String,
    val img: String?,
    val artists: List<Pair<String, String>>,
    val tracks: List<MatchedTrackResult>
)

/**
 * Unified, rate-limit-resilient music metadata client combining:
 * - Last.fm (scrobbles + now playing)
 * - Spotify Web API (primary search, catalog, & releases including appears_on)
 * - Deezer API (zero-auth reliable fallback for track matching, albums & artist releases)
 * - Apple Music / iTunes API (secondary fallback with non-truncating batch sizes)
 */
class MusicApiClients(
    private val http: OkHttpClient = FirebaseRestClient.defaultHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val formMedia = "application/x-www-form-urlencoded".toMediaType()

    private val spTokenMutex = Mutex()
    private var spToken: String? = null
    private var spTokenExpMs: Long = 0L
    private var spTokenKey: String = ""
    private var lastSpCallMs: Long = 0L
    private var lastDzCallMs: Long = 0L
    private var lastAmCallMs: Long = 0L

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun pace(lastRef: () -> Long, setRef: (Long) -> Unit, minGapMs: Long) {
        val wait = minGapMs - (System.currentTimeMillis() - lastRef())
        if (wait > 0) delay(wait)
        setRef(System.currentTimeMillis())
    }

    // ==================== LAST.FM ====================

    suspend fun getLastFmProfile(cfg: LastFmConfig): LastFmProfile = withContext(Dispatchers.IO) {
        val url = "https://ws.audioscrobbler.com/2.0/?method=user.getinfo&user=${enc(cfg.user)}&api_key=${enc(cfg.key)}&format=json"
        val body = getText(url) ?: throw IllegalStateException("Last.fm non risponde")
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonPrimitive?.intOrNull?.let { code ->
            val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: "errore $code"
            throw IllegalStateException("Last.fm: $msg")
        }
        val u = root["user"]?.jsonObject ?: throw IllegalStateException("Utente Last.fm non trovato")
        LastFmProfile(
            name = u["name"]?.jsonPrimitive?.contentOrNull ?: cfg.user,
            playcount = u["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            registered = u["registered"]?.jsonObject?.get("unixtime")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        )
    }

    suspend fun getLastFmRecentPage(
        cfg: LastFmConfig,
        fromSec: Long,
        toSec: Long,
        page: Int
    ): RecentTracksPage = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks")
            append("&user=").append(enc(cfg.user))
            append("&api_key=").append(enc(cfg.key))
            append("&limit=200&format=json&page=").append(page)
            append("&to=").append(toSec)
            if (fromSec > 0L) append("&from=").append(fromSec)
        }
        val body = getText(url) ?: throw IllegalStateException("Last.fm non risponde")
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonPrimitive?.intOrNull?.let { code ->
            val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: "errore $code"
            throw IllegalStateException("Last.fm: $msg")
        }
        val rt = root["recenttracks"]?.jsonObject ?: return@withContext RecentTracksPage(1, null, emptyList())
        val totalPages = rt["@attr"]?.jsonObject?.get("totalPages")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        val rawTracksEl = rt["track"]
        val trackObjs: List<JsonObject> = when (rawTracksEl) {
            is JsonArray -> rawTracksEl.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(rawTracksEl)
            else -> emptyList()
        }

        var nowPlaying: NowPlaying? = null
        val scrobbles = mutableListOf<ScrobbleItem>()

        for (t in trackObjs) {
            val isNow = t["@attr"]?.jsonObject?.get("nowplaying")?.jsonPrimitive?.contentOrNull == "true"
            val artistName = t["artist"]?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull
                ?: t["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                ?: ""
            val trackName = t["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val albumName = t["album"]?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            val imgs = t["image"] as? JsonArray
            val imgUrl = imgs?.mapNotNull { it as? JsonObject }
                ?.let { list ->
                    list.find { it["size"]?.jsonPrimitive?.contentOrNull == "extralarge" } ?: list.lastOrNull()
                }?.get("#text")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            if (isNow) {
                nowPlaying = NowPlaying(artistName, trackName, albumName, imgUrl, System.currentTimeMillis())
            } else {
                val uts = t["date"]?.jsonObject?.get("uts")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                if (artistName.isNotBlank() && trackName.isNotBlank()) {
                    scrobbles.add(ScrobbleItem(uts, artistName, trackName, albumName))
                }
            }
        }
        RecentTracksPage(totalPages, nowPlaying, scrobbles)
    }

    // ==================== SPOTIFY ====================

    private suspend fun getSpotifyToken(cfg: SpotifyConfig): String = spTokenMutex.withLock {
        val key = "${cfg.id}:${cfg.secret}"
        if (spToken != null && spTokenKey == key && spTokenExpMs > System.currentTimeMillis() + 60_000L) {
            return spToken!!
        }
        val basic = Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val req = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $basic")
            .post("grant_type=client_credentials".toRequestBody(formMedia))
            .build()
        val res = withContext(Dispatchers.IO) { http.newCall(req).execute() }
        val text = res.body?.string().orEmpty()
        if (!res.isSuccessful) {
            throw IllegalStateException("Spotify credenziali non valide (HTTP ${res.code})")
        }
        val obj = json.parseToJsonElement(text).jsonObject
        val token = obj["access_token"]!!.jsonPrimitive.content
        val exp = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        spToken = token
        spTokenKey = key
        spTokenExpMs = System.currentTimeMillis() + exp * 1000L
        token
    }

    private suspend fun spotifyGet(cfg: SpotifyConfig, path: String, attempt: Int = 0): JsonObject? {
        pace({ lastSpCallMs }, { lastSpCallMs = it }, 180L)
        val token = getSpotifyToken(cfg)
        val req = Request.Builder()
            .url("https://api.spotify.com/v1$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val res = withContext(Dispatchers.IO) { http.newCall(req).execute() }
        val code = res.code
        val retryAfterSec = res.header("Retry-After")?.toLongOrNull()
        val text = res.body?.string().orEmpty()
        if (code == 401 && attempt < 1) {
            spToken = null
            return spotifyGet(cfg, path, attempt + 1)
        }
        if ((code == 429 || code == 502 || code == 503) && attempt < 4) {
            val waitMs = ((retryAfterSec ?: (3L * (attempt + 1))).coerceAtMost(30L)) * 1000L
            delay(waitMs)
            return spotifyGet(cfg, path, attempt + 1)
        }
        if (code == 404) return null
        if (code !in 200..299) return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    suspend fun testSpotify(cfg: SpotifyConfig): String? = runCatching {
        spToken = null
        val obj = spotifyGet(cfg, "/artists/0TnOYISbd1XYRBk9myaseg/albums?include_groups=single&market=IT&limit=1")
        if (obj == null) "Spotify non ha restituito risultati (verifica Client ID/Secret)" else null
    }.getOrElse { it.message ?: "Errore Spotify" }

    suspend fun findSpotifyArtistId(cfg: SpotifyConfig, artistName: String): String? {
        val target = TextNormalizer.norm(artistName)
        if (target.isEmpty()) return null
        val root = spotifyGet(cfg, "/search?type=artist&market=IT&limit=10&q=${enc(artistName)}") ?: return null
        val items = root["artists"]?.jsonObject?.get("items")?.jsonArray ?: return null
        val parsed = items.mapNotNull { it as? JsonObject }
        // 1. Exact normalized match
        parsed.firstOrNull { TextNormalizer.norm(it["name"]?.jsonPrimitive?.contentOrNull) == target }
            ?.get("id")?.jsonPrimitive?.contentOrNull?.let { return it }
        // 2. High-confidence match (handles minor punctuation/prefix differences)
        parsed.firstOrNull {
            val n = TextNormalizer.norm(it["name"]?.jsonPrimitive?.contentOrNull)
            n.isNotEmpty() && (n.replace(" ", "") == target.replace(" ", ""))
        }?.get("id")?.jsonPrimitive?.contentOrNull?.let { return it }
        return null
    }

    /**
     * Fetches ALL recent releases for a Spotify artist including albums, singles AND appears_on (featurings)!
     */
    suspend fun getSpotifyArtistReleases(
        cfg: SpotifyConfig,
        artistId: String,
        artistDisplayName: String,
        sinceDate: String,
        todayStr: String
    ): List<ReleaseItem> {
        val collected = LinkedHashMap<String, ReleaseItem>()
        // Query album+single AND appears_on separately so albums/singles are never crowded out by compilations
        val groups = listOf("album,single" to false, "appears_on" to true)
        for ((grp, isFeat) in groups) {
            val root = spotifyGet(cfg, "/artists/$artistId/albums?include_groups=$grp&market=IT&limit=20") ?: continue
            val items = root["items"]?.jsonArray ?: continue
            for (el in items) {
                val obj = el as? JsonObject ?: continue
                val prec = obj["release_date_precision"]?.jsonPrimitive?.contentOrNull ?: continue
                val date = obj["release_date"]?.jsonPrimitive?.contentOrNull ?: continue
                if (prec != "day" || date < sinceDate) continue
                val sid = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val totalTracks = obj["total_tracks"]?.jsonPrimitive?.intOrNull ?: 0
                val albumType = obj["album_type"]?.jsonPrimitive?.contentOrNull ?: ""
                if (albumType.equals("compilation", ignoreCase = true) && isFeat) continue
                val artists = (obj["artists"]?.jsonArray ?: JsonArray(emptyList()))
                    .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
                val img = pickBestSpotifyImage(obj["images"] as? JsonArray)
                val id = "sp:$sid"
                if (!collected.containsKey(id)) {
                    collected[id] = ReleaseItem(
                        id = id,
                        sid = sid,
                        src = "sp",
                        title = title,
                        artists = artists.ifEmpty { listOf(artistDisplayName) },
                        by = listOf(artistDisplayName),
                        img = img,
                        date = date,
                        seen = todayStr,
                        tracks = totalTracks,
                        type = if (totalTracks >= ModelEngine.ALBUM_MIN_TRACKS) "album" else "single",
                        link = "https://open.spotify.com/album/$sid",
                        isFeature = isFeat
                    )
                }
            }
        }
        return collected.values.toList()
    }

    suspend fun searchSpotifyTracks(
        cfg: SpotifyConfig,
        artist: String,
        track: String,
        album: String = ""
    ): List<MatchedTrackResult> {
        val cleanTrack = TextNormalizer.stripFeaturing(track)
        val queries = buildList {
            if (artist.isNotBlank() && track.isNotBlank()) {
                add("track:\"$cleanTrack\" artist:\"$artist\"")
                add("$artist $cleanTrack")
            } else {
                add("$artist $track".trim())
            }
        }.distinct()

        val targetArtist = TextNormalizer.norm(artist)
        val targetTrack = TextNormalizer.cleanTrackTitle(track)
        val results = LinkedHashMap<String, MatchedTrackResult>()

        for (q in queries) {
            val root = spotifyGet(cfg, "/search?type=track&market=IT&limit=10&q=${enc(q)}") ?: continue
            val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray ?: continue
            for (el in items) {
                val obj = el as? JsonObject ?: continue
                val parsed = parseSpotifyTrackObj(obj) ?: continue
                results.putIfAbsent(parsed.trackId, parsed)
            }
            // Stop early if we found a strong match
            if (results.values.any { isStrongMatch(it, targetArtist, targetTrack) }) break
        }

        return results.values.sortedByDescending { matchScore(it, targetArtist, targetTrack, TextNormalizer.cleanAlbumTitle(album)) }
    }

    suspend fun getSpotifyAlbum(cfg: SpotifyConfig, albumId: String): FullAlbumResult? {
        val obj = spotifyGet(cfg, "/albums/$albumId?market=IT") ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val type = obj["album_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val total = obj["total_tracks"]?.jsonPrimitive?.intOrNull ?: 0
        val date = obj["release_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val img = pickBestSpotifyImage(obj["images"] as? JsonArray)
        val artists = parseSpotifyArtists(obj["artists"] as? JsonArray)
        val trackItems = obj["tracks"]?.jsonObject?.get("items")?.jsonArray ?: JsonArray(emptyList())
        val tracks = trackItems.mapNotNull { el ->
            val t = el as? JsonObject ?: return@mapNotNull null
            val tid = t["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val tName = t["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            MatchedTrackResult(
                trackId = tid,
                title = tName,
                albumId = id,
                albumName = name,
                albumType = type,
                albumTotal = total,
                albumDate = date,
                albumImg = img,
                albumArtists = artists,
                trackArtists = parseSpotifyArtists(t["artists"] as? JsonArray),
                durMs = t["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
                isrc = null,
                disc = t["disc_number"]?.jsonPrimitive?.intOrNull ?: 1,
                trackNum = t["track_number"]?.jsonPrimitive?.intOrNull ?: 0,
                source = "sp"
            )
        }
        return FullAlbumResult(id, name, type, total, date, img, artists, tracks)
    }

    suspend fun getSpotifyArtistImage(cfg: SpotifyConfig, artistId: String): Pair<String, String?>? {
        val obj = spotifyGet(cfg, "/artists/$artistId") ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val img = pickBestSpotifyImage(obj["images"] as? JsonArray)
        return name to img
    }

    /**
     * Fetches up to 50 Spotify tracks in a single API call to resolve "?" / stub tracks quickly.
     */
    suspend fun getSpotifyTracksBatch(cfg: SpotifyConfig, trackIds: List<String>): List<MatchedTrackResult> {
        if (trackIds.isEmpty()) return emptyList()
        val out = mutableListOf<MatchedTrackResult>()
        for (chunk in trackIds.distinct().chunked(50)) {
            val root = spotifyGet(cfg, "/tracks?market=IT&ids=${chunk.joinToString(",")}") ?: continue
            val arr = root["tracks"]?.jsonArray ?: continue
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                parseSpotifyTrackObj(obj)?.let { out.add(it) }
            }
        }
        return out
    }

    /**
     * Fetches up to 50 Spotify artists in a single API call to refresh profile images across the library.
     */
    suspend fun getSpotifyArtistsBatch(cfg: SpotifyConfig, artistIds: List<String>): Map<String, Pair<String, String?>> {
        if (artistIds.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, Pair<String, String?>>()
        for (chunk in artistIds.distinct().chunked(50)) {
            val root = spotifyGet(cfg, "/artists?ids=${chunk.joinToString(",")}") ?: continue
            val arr = root["artists"]?.jsonArray ?: continue
            for (el in arr) {
                val obj = el as? JsonObject ?: continue
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val img = pickBestSpotifyImage(obj["images"] as? JsonArray)
                out[id] = name to img
            }
        }
        return out
    }

    /**
     * Fetches the live official Spotify artist profile photo via Spotify's public oEmbed endpoint.
     * Works with zero API key and is unaffected by Spotify Web API Development Mode restrictions.
     */
    suspend fun getSpotifyArtistOEmbedImage(artistId: String): Pair<String, String?>? {
        if (!TextNormalizer.isSpotifyId(artistId)) return null
        val url = "https://open.spotify.com/oembed?url=${enc("https://open.spotify.com/artist/$artistId")}"
        val text = getText(url) ?: return null
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val thumb = obj["thumbnail_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return if (thumb != null) title to thumb else null
    }

    private fun parseSpotifyTrackObj(obj: JsonObject): MatchedTrackResult? {
        val tid = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val al = obj["album"]?.jsonObject
        val alId = al?.get("id")?.jsonPrimitive?.contentOrNull
        val alName = al?.get("name")?.jsonPrimitive?.contentOrNull
        val alType = al?.get("album_type")?.jsonPrimitive?.contentOrNull.orEmpty()
        val alTotal = al?.get("total_tracks")?.jsonPrimitive?.intOrNull
        val alDate = al?.get("release_date")?.jsonPrimitive?.contentOrNull.orEmpty()
        val alImg = pickBestSpotifyImage(al?.get("images") as? JsonArray)
        val alArtists = parseSpotifyArtists(al?.get("artists") as? JsonArray)
        val trArtists = parseSpotifyArtists(obj["artists"] as? JsonArray)
        val isrc = obj["external_ids"]?.jsonObject?.get("isrc")?.jsonPrimitive?.contentOrNull
        return MatchedTrackResult(
            trackId = tid,
            title = title,
            albumId = alId,
            albumName = alName,
            albumType = alType,
            albumTotal = alTotal,
            albumDate = alDate,
            albumImg = alImg,
            albumArtists = alArtists,
            trackArtists = trArtists,
            durMs = obj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
            isrc = isrc,
            disc = obj["disc_number"]?.jsonPrimitive?.intOrNull ?: 1,
            trackNum = obj["track_number"]?.jsonPrimitive?.intOrNull ?: 0,
            source = "sp"
        )
    }

    private fun parseSpotifyArtists(arr: JsonArray?): List<Pair<String, String>> =
        arr?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id to name
        }.orEmpty()

    private fun pickBestSpotifyImage(arr: JsonArray?): String? =
        arr?.mapNotNull { it as? JsonObject }
            ?.maxByOrNull { it["width"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?.get("url")?.jsonPrimitive?.contentOrNull

    // ==================== DEEZER (Zero-auth fast fallback) ====================

    private suspend fun deezerGet(path: String, attempt: Int = 0): JsonObject? {
        pace({ lastDzCallMs }, { lastDzCallMs = it }, 110L)
        val text = getText("https://api.deezer.com$path") ?: return null
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val errCode = obj["error"]?.jsonObject?.get("code")?.jsonPrimitive?.intOrNull
        if (errCode == 4 && attempt < 3) {
            delay(3500L)
            return deezerGet(path, attempt + 1)
        }
        if (errCode != null) return null
        return obj
    }

    suspend fun searchDeezerTracks(
        artist: String,
        track: String,
        album: String = ""
    ): List<MatchedTrackResult> {
        val cleanTrack = TextNormalizer.stripFeaturing(track)
        val queries = listOfNotNull(
            if (artist.isNotBlank() && cleanTrack.isNotBlank()) "artist:\"$artist\" track:\"$cleanTrack\"" else null,
            "$artist $cleanTrack".trim().takeIf { it.isNotBlank() }
        ).distinct()

        val targetArtist = TextNormalizer.norm(artist)
        val targetTrack = TextNormalizer.cleanTrackTitle(track)
        val results = LinkedHashMap<String, MatchedTrackResult>()

        for (q in queries) {
            val root = deezerGet("/search/track?limit=10&q=${enc(q)}") ?: continue
            val data = root["data"]?.jsonArray ?: continue
            for (el in data) {
                val o = el as? JsonObject ?: continue
                val idNum = o["id"]?.jsonPrimitive?.longOrNull ?: continue
                val title = o["title"]?.jsonPrimitive?.contentOrNull ?: continue
                val artObj = o["artist"]?.jsonObject
                val artId = artObj?.get("id")?.jsonPrimitive?.longOrNull?.let { "dz:$it" } ?: continue
                val artName = artObj["name"]?.jsonPrimitive?.contentOrNull ?: artist
                val alObj = o["album"]?.jsonObject
                val alId = alObj?.get("id")?.jsonPrimitive?.longOrNull?.let { "dz:$it" }
                val alTitle = alObj?.get("title")?.jsonPrimitive?.contentOrNull
                val alImg = alObj?.get("cover_big")?.jsonPrimitive?.contentOrNull
                    ?: alObj?.get("cover_medium")?.jsonPrimitive?.contentOrNull
                val durMs = (o["duration"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000L
                val tid = "dz:$idNum"
                results.putIfAbsent(
                    tid,
                    MatchedTrackResult(
                        trackId = tid,
                        title = title,
                        albumId = alId,
                        albumName = alTitle,
                        albumType = "album",
                        albumTotal = null,
                        albumDate = "",
                        albumImg = alImg,
                        albumArtists = listOf(artId to artName),
                        trackArtists = listOf(artId to artName),
                        durMs = durMs,
                        isrc = null,
                        disc = 1,
                        trackNum = 0,
                        source = "dz"
                    )
                )
            }
            if (results.values.any { isStrongMatch(it, targetArtist, targetTrack) }) break
        }
        return results.values.sortedByDescending { matchScore(it, targetArtist, targetTrack, TextNormalizer.cleanAlbumTitle(album)) }
    }

    suspend fun getDeezerAlbum(dzAlbumId: String): FullAlbumResult? {
        val rawId = dzAlbumId.removePrefix("dz:")
        val obj = deezerGet("/album/$rawId") ?: return null
        val name = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val date = obj["release_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val total = obj["nb_tracks"]?.jsonPrimitive?.intOrNull ?: 0
        val recordType = obj["record_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val img = obj["cover_xl"]?.jsonPrimitive?.contentOrNull
            ?: obj["cover_big"]?.jsonPrimitive?.contentOrNull
            ?: obj["cover_medium"]?.jsonPrimitive?.contentOrNull
        val artObj = obj["artist"]?.jsonObject
        val mainArt = artObj?.let {
            val aid = it["id"]?.jsonPrimitive?.longOrNull?.let { id -> "dz:$id" }
            val an = it["name"]?.jsonPrimitive?.contentOrNull
            if (aid != null && an != null) listOf(aid to an) else emptyList()
        }.orEmpty()
        val trackArr = obj["tracks"]?.jsonObject?.get("data")?.jsonArray ?: JsonArray(emptyList())
        val tracks = trackArr.mapIndexedNotNull { idx, el ->
            val t = el as? JsonObject ?: return@mapIndexedNotNull null
            val tidNum = t["id"]?.jsonPrimitive?.longOrNull ?: return@mapIndexedNotNull null
            val tName = t["title"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val tArtObj = t["artist"]?.jsonObject
            val tArt = tArtObj?.let {
                val aid = it["id"]?.jsonPrimitive?.longOrNull?.let { id -> "dz:$id" }
                val an = it["name"]?.jsonPrimitive?.contentOrNull
                if (aid != null && an != null) listOf(aid to an) else mainArt
            } ?: mainArt
            MatchedTrackResult(
                trackId = "dz:$tidNum",
                title = tName,
                albumId = "dz:$rawId",
                albumName = name,
                albumType = recordType,
                albumTotal = total,
                albumDate = date,
                albumImg = img,
                albumArtists = mainArt,
                trackArtists = tArt,
                durMs = (t["duration"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000L,
                isrc = t["isrc"]?.jsonPrimitive?.contentOrNull,
                disc = t["disk_number"]?.jsonPrimitive?.intOrNull ?: 1,
                trackNum = t["track_position"]?.jsonPrimitive?.intOrNull ?: (idx + 1),
                source = "dz"
            )
        }
        return FullAlbumResult("dz:$rawId", name, recordType, total, date, img, mainArt, tracks)
    }

    suspend fun findDeezerArtistId(artistName: String): String? =
        findDeezerArtistProfile(artistName)?.first

    /**
     * Returns Pair(dzNumericId, highResPictureUrl) for the most popular matching Deezer artist,
     * rejecting empty placeholder URLs (`/artist//`).
     */
    suspend fun findDeezerArtistProfile(artistName: String): Pair<String, String?>? {
        val target = TextNormalizer.norm(artistName)
        if (target.isEmpty()) return null
        val root = deezerGet("/search/artist?limit=10&q=${enc(artistName)}") ?: return null
        val data = root["data"]?.jsonArray ?: return null
        val candidates = data.mapNotNull { it as? JsonObject }
        val exact = candidates.filter {
            TextNormalizer.norm(it["name"]?.jsonPrimitive?.contentOrNull) == target
        }
        val pool = exact.ifEmpty {
            candidates.filter {
                val n = TextNormalizer.norm(it["name"]?.jsonPrimitive?.contentOrNull)
                n.isNotEmpty() && n.replace(" ", "") == target.replace(" ", "")
            }
        }
        val hit = pool.maxByOrNull { it["nb_fan"]?.jsonPrimitive?.longOrNull ?: 0L } ?: return null
        val id = hit["id"]?.jsonPrimitive?.longOrNull ?: return null
        val rawPic = hit["picture_xl"]?.jsonPrimitive?.contentOrNull
            ?: hit["picture_big"]?.jsonPrimitive?.contentOrNull
            ?: hit["picture_medium"]?.jsonPrimitive?.contentOrNull
        val validPic = rawPic?.takeIf { it.isNotBlank() && !it.contains("/artist//") }
        return id.toString() to validPic
    }

    suspend fun getDeezerTrack(dzTrackId: String): MatchedTrackResult? {
        val rawId = dzTrackId.removePrefix("dz:")
        val o = deezerGet("/track/$rawId") ?: return null
        val idNum = o["id"]?.jsonPrimitive?.longOrNull ?: return null
        val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val artObj = o["artist"]?.jsonObject
        val artId = artObj?.get("id")?.jsonPrimitive?.longOrNull?.let { "dz:$it" } ?: return null
        val artName = artObj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val alObj = o["album"]?.jsonObject
        val alId = alObj?.get("id")?.jsonPrimitive?.longOrNull?.let { "dz:$it" }
        val alTitle = alObj?.get("title")?.jsonPrimitive?.contentOrNull
        val alImg = alObj?.get("cover_xl")?.jsonPrimitive?.contentOrNull
            ?: alObj?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: alObj?.get("cover_medium")?.jsonPrimitive?.contentOrNull
        val durMs = (o["duration"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000L
        return MatchedTrackResult(
            trackId = "dz:$idNum",
            title = title,
            albumId = alId,
            albumName = alTitle,
            albumType = "album",
            albumTotal = null,
            albumDate = o["release_date"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            albumImg = alImg,
            albumArtists = listOf(artId to artName),
            trackArtists = listOf(artId to artName),
            durMs = durMs,
            isrc = o["isrc"]?.jsonPrimitive?.contentOrNull,
            disc = o["disk_number"]?.jsonPrimitive?.intOrNull ?: 1,
            trackNum = o["track_position"]?.jsonPrimitive?.intOrNull ?: 0,
            source = "dz"
        )
    }

    suspend fun getDeezerArtistReleases(
        dzNumericArtistId: String,
        artistDisplayName: String,
        sinceDate: String,
        todayStr: String
    ): List<ReleaseItem> {
        val root = deezerGet("/artist/$dzNumericArtistId/albums?limit=30") ?: return emptyList()
        val data = root["data"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<ReleaseItem>()
        for (el in data) {
            val o = el as? JsonObject ?: continue
            val date = o["release_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (date.length < 10 || date < sinceDate) continue
            val alId = o["id"]?.jsonPrimitive?.longOrNull ?: continue
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: continue
            val img = o["cover_big"]?.jsonPrimitive?.contentOrNull
                ?: o["cover_medium"]?.jsonPrimitive?.contentOrNull
            val rType = o["record_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            out.add(
                ReleaseItem(
                    id = "dz:$alId",
                    sid = "dz:$alId",
                    src = "dz",
                    title = title,
                    artists = listOf(artistDisplayName),
                    by = listOf(artistDisplayName),
                    img = img,
                    date = date.take(10),
                    seen = todayStr,
                    tracks = if (rType.equals("album", ignoreCase = true)) 10 else 1,
                    type = if (rType.equals("album", ignoreCase = true)) "album" else "single",
                    link = "https://www.deezer.com/album/$alId"
                )
            )
        }
        return out
    }

    // ==================== APPLE MUSIC / iTUNES (Non-truncating fallback) ====================

    private suspend fun itunesGet(path: String): JsonObject? {
        pace({ lastAmCallMs }, { lastAmCallMs = it }, 260L)
        val text = getText("https://itunes.apple.com$path") ?: return null
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    suspend fun findAppleArtistId(artistName: String): Long? {
        val target = TextNormalizer.norm(artistName)
        if (target.isEmpty()) return null
        val root = itunesGet("/search?entity=musicArtist&limit=8&country=IT&term=${enc(artistName)}") ?: return null
        val arr = root["results"]?.jsonArray ?: return null
        return arr.mapNotNull { it as? JsonObject }
            .firstOrNull { TextNormalizer.norm(it["artistName"]?.jsonPrimitive?.contentOrNull) == target }
            ?.get("artistId")?.jsonPrimitive?.longOrNull
    }

    /**
     * Looks up recent releases in small batches of 15 artists (instead of 80!) so iTunes never truncates results.
     */
    suspend fun getAppleReleasesBatch(
        artistIdToName: Map<Long, String>,
        sinceDate: String,
        todayStr: String
    ): List<ReleaseItem> {
        if (artistIdToName.isEmpty()) return emptyList()
        val ids = artistIdToName.keys.joinToString(",")
        val root = itunesGet("/lookup?entity=album&sort=recent&limit=10&country=IT&id=$ids") ?: return emptyList()
        val arr = root["results"]?.jsonArray ?: return emptyList()
        val out = mutableListOf<ReleaseItem>()
        var currentArtistName: String? = null
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val wrapper = o["wrapperType"]?.jsonPrimitive?.contentOrNull
            if (wrapper == "artist") {
                val aid = o["artistId"]?.jsonPrimitive?.longOrNull
                currentArtistName = artistIdToName[aid] ?: o["artistName"]?.jsonPrimitive?.contentOrNull
                continue
            }
            if (wrapper != "collection") continue
            val relDate = o["releaseDate"]?.jsonPrimitive?.contentOrNull?.take(10).orEmpty()
            if (relDate.length < 10 || relDate < sinceDate) continue
            val cid = o["collectionId"]?.jsonPrimitive?.longOrNull ?: continue
            val rawName = o["collectionName"]?.jsonPrimitive?.contentOrNull ?: continue
            val cleanTitle = rawName.replace(Regex(" - (Single|EP)$", RegexOption.IGNORE_CASE), "")
            val artistName = o["artistName"]?.jsonPrimitive?.contentOrNull ?: currentArtistName.orEmpty()
            val trackCount = o["trackCount"]?.jsonPrimitive?.intOrNull ?: 0
            val img = o["artworkUrl100"]?.jsonPrimitive?.contentOrNull?.replace("100x100bb", "600x600bb")
            val link = o["collectionViewUrl"]?.jsonPrimitive?.contentOrNull
            val isAlbum = trackCount >= ModelEngine.ALBUM_MIN_TRACKS && !rawName.endsWith(" - Single", ignoreCase = true)
            out.add(
                ReleaseItem(
                    id = "am:$cid",
                    sid = "am:$cid",
                    src = "am",
                    title = cleanTitle,
                    artists = listOf(artistName),
                    by = listOfNotNull(currentArtistName ?: artistName.takeIf { it.isNotBlank() }),
                    img = img,
                    date = relDate,
                    seen = todayStr,
                    tracks = trackCount,
                    type = if (isAlbum) "album" else "single",
                    link = link
                )
            )
        }
        return out
    }

    // ==================== MATCH SCORING HELPERS ====================

    fun isStrongMatch(item: MatchedTrackResult, targetArtistNorm: String, targetTrackClean: String): Boolean {
        val candTrack = TextNormalizer.cleanTrackTitle(item.title)
        val candArtists = item.trackArtists.map { TextNormalizer.norm(it.second) }
        val trackMatch = candTrack == targetTrackClean ||
            (candTrack.length >= 4 && targetTrackClean.length >= 4 &&
                (candTrack.startsWith(targetTrackClean) || targetTrackClean.startsWith(candTrack)))
        val artistMatch = targetArtistNorm.isEmpty() || candArtists.any {
            it == targetArtistNorm || it.contains(targetArtistNorm) || targetArtistNorm.contains(it)
        }
        return trackMatch && artistMatch
    }

    fun matchScore(
        item: MatchedTrackResult,
        targetArtistNorm: String,
        targetTrackClean: String,
        targetAlbumClean: String
    ): Int {
        var score = 0
        val candTrack = TextNormalizer.cleanTrackTitle(item.title)
        val candArtists = item.trackArtists.map { TextNormalizer.norm(it.second) }
        val candAlbum = TextNormalizer.cleanAlbumTitle(item.albumName)

        if (candTrack == targetTrackClean) score += 60
        else if (candTrack.isNotEmpty() && targetTrackClean.isNotEmpty() &&
            (candTrack.contains(targetTrackClean) || targetTrackClean.contains(candTrack))
        ) score += 35

        if (candArtists.any { it == targetArtistNorm }) score += 45
        else if (candArtists.any { it.contains(targetArtistNorm) || targetArtistNorm.contains(it) }) score += 25

        if (targetAlbumClean.isNotEmpty() && candAlbum == targetAlbumClean) score += 20
        if (item.source == "sp") score += 10
        if (item.albumImg != null) score += 3
        return score
    }

    private suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SolcoAndroid/2.0")
                .get()
                .build()
            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        }.getOrNull()
    }

    companion object {
        fun daysAgoDate(days: Long): String = LocalDate.now().minusDays(days).toString()
        fun todayDate(): String = LocalDate.now().toString()
    }
}
