package app.solco.music.domain

import app.solco.music.data.model.AlbumRecord
import app.solco.music.data.model.AlbumStat
import app.solco.music.data.model.ArtistRecord
import app.solco.music.data.model.ArtistStat
import app.solco.music.data.model.ChunkRecord
import app.solco.music.data.model.GoalProgress
import app.solco.music.data.model.GoalsConfig
import app.solco.music.data.model.KvRecord
import app.solco.music.data.model.MergeConfig
import app.solco.music.data.model.NameRecord
import app.solco.music.data.model.PeriodStats
import app.solco.music.data.model.PlayEvent
import app.solco.music.data.model.ResolvedAlbum
import app.solco.music.data.model.ResolvedArtist
import app.solco.music.data.model.ResolvedSong
import app.solco.music.data.model.ResolvedTrack
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.model.SolcoModel
import app.solco.music.data.model.SongStat
import app.solco.music.data.model.TrackHint
import app.solco.music.data.model.TrackRecord
import app.solco.music.data.model.UnresolvedEntry
import app.solco.music.data.model.UwuTournament
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

data class RawStoreSnapshot(
    val kv: Map<String, KvRecord> = emptyMap(),
    val chunks: Map<String, ChunkRecord> = emptyMap(),
    val names: Map<String, NameRecord> = emptyMap(),
    val tracks: Map<String, TrackRecord> = emptyMap(),
    val albums: Map<String, AlbumRecord> = emptyMap(),
    val artists: Map<String, ArtistRecord> = emptyMap(),
    val reviews: Map<String, ReviewRecord> = emptyMap()
)

data class MonthlyBucket(
    val year: Int,
    val month: Int, // 1..12
    val label: String,
    val plays: Int
)

data class UwuBracketState(
    val done: Boolean,
    val champion: String? = null,
    val a: String? = null,
    val b: String? = null,
    val roundIndex: Int = 0,
    val roundSize: Int = 0,
    val matchNo: Int = 0,
    val matchesInRound: Int = 0,
    val picksUsed: Int = 0,
    val eliminatedAtSize: Map<String, Int> = emptyMap()
)

/**
 * Core computational engine for Solco:
 * - Unified Canonical Artist Deduplication (merges Spotify, Deezer `dz:`, and synthetic `n:` IDs automatically!)
 * - Enhanced Canonical Album Edition Deduplication (Standard, Deluxe, Expanded, Remastered, cross-source)
 * - Fast play-count & listening-time stats per period
 * - Goals progress & UwuFufu bracket engine
 */
object ModelEngine {

    const val VARIOUS_ARTISTS_ID = "0LyfQWJT6nXafLPZqxe9Of"
    const val ALBUM_MIN_TRACKS = 4
    const val MIN_PLAY_MS = 30_000L
    const val CUTOFF_GRACE_SEC = 10L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val monthShorts = arrayOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

    val TOP_TIERS = listOf(1, 3, 5, 10, 20, 50)
    val TOP_CAPS = mapOf(1 to 1, 3 to 2, 5 to 2, 10 to 5, 20 to 10, 50 to 30)

    private fun artistIdPriority(id: String): Int = when {
        TextNormalizer.isSpotifyId(id) -> 3
        id.startsWith("dz:") -> 2
        id.startsWith("am:") -> 1
        id.startsWith("n:") -> 0
        else -> 2
    }

    private fun parseArtistsList(artistsJson: String): List<Pair<String?, String>> {
        if (artistsJson.isBlank() || artistsJson == "[]") return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(artistsJson).jsonArray
            arr.mapNotNull { el ->
                val pair = el as? JsonArray ?: return@mapNotNull null
                val id = pair.getOrNull(0)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                val name = pair.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty()
                if (id == null && name.isBlank()) null else id to name
            }
        }.getOrDefault(emptyList())
    }

    private fun parseStringList(jsonStr: String): List<String> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return runCatching {
            json.parseToJsonElement(jsonStr).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrDefault(emptyList())
    }

    private fun parseTrackHint(hintJson: String?): TrackHint? {
        if (hintJson.isNullOrBlank() || hintJson == "null") return null
        return runCatching { json.decodeFromString<TrackHint>(hintJson) }.getOrNull()
    }

    fun buildModel(raw: RawStoreSnapshot): SolcoModel {
        val mergeCfg = raw.kv["merge"]?.vJson?.let {
            runCatching { json.decodeFromString<MergeConfig>(it) }.getOrNull()
        } ?: MergeConfig()
        val discOff: Map<String, List<Int>> = raw.kv["albums.discOff"]?.vJson?.let {
            runCatching { json.decodeFromString<Map<String, List<Int>>>(it) }.getOrNull()
        } ?: emptyMap()

        // 1. Collect all raw artist candidates (from artists table, tracks, and albums)
        data class RawArtistCand(val id: String, var name: String, var img: String?, val real: Boolean)
        val rawArtists = LinkedHashMap<String, RawArtistCand>()

        fun registerArtist(id: String?, name: String?, img: String?, real: Boolean) {
            val cleanName = name?.trim().orEmpty()
            val keyId = id?.takeIf { it.isNotBlank() } ?: if (cleanName.isNotEmpty()) {
                TextNormalizer.syntheticArtistId(cleanName)
            } else return
            val existing = rawArtists[keyId]
            if (existing == null) {
                rawArtists[keyId] = RawArtistCand(
                    id = keyId,
                    name = cleanName.ifEmpty { "…" },
                    img = img?.takeIf { it.isNotBlank() },
                    real = real && !keyId.startsWith("n:")
                )
            } else {
                if ((existing.name == "…" || existing.name == "?") && cleanName.isNotEmpty()) {
                    existing.name = cleanName
                }
                if (existing.img == null && !img.isNullOrBlank()) {
                    existing.img = img
                }
            }
        }

        for (a in raw.artists.values) {
            registerArtist(a.id, a.name, a.img, true)
        }
        for (t in raw.tracks.values) {
            for ((aid, aname) in parseArtistsList(t.artistsJson)) {
                registerArtist(aid, aname, null, aid != null)
            }
            parseTrackHint(t.hintJson)?.an?.let { registerArtist(null, it, null, false) }
        }
        for (al in raw.albums.values) {
            for ((aid, aname) in parseArtistsList(al.artistsJson)) {
                registerArtist(aid, aname, null, aid != null)
            }
        }

        // 2. Canonical Artist Deduplication: group by normalized artist name!
        // Fixes duplicate artists across Spotify, Deezer (dz:), Apple Music (am:), and synthetic (n:) IDs.
        val byNormName = LinkedHashMap<String, MutableList<RawArtistCand>>()
        for (cand in rawArtists.values) {
            val nk = TextNormalizer.norm(cand.name)
            if (nk.isNotEmpty() && cand.name != "…" && cand.name != "?") {
                byNormName.getOrPut(nk) { mutableListOf() }.add(cand)
            }
        }

        val artistCanon = HashMap<String, String>()
        val artistsMap = LinkedHashMap<String, ResolvedArtist>()

        for ((_, group) in byNormName) {
            // Sort: Spotify ID first, then has image, then Deezer, then synthetic, then lexicographical
            group.sortWith(
                compareByDescending<RawArtistCand> { artistIdPriority(it.id) }
                    .thenByDescending { it.img != null }
                    .thenBy { it.id }
            )
            val primary = group.first()
            val bestImg = group.firstNotNullOfOrNull { it.img }
            val bestName = group.firstOrNull { it.name.isNotBlank() && it.name != "…" }?.name ?: primary.name
            val aliasIds = group.drop(1).map { it.id }
            for (c in group) artistCanon[c.id] = primary.id
            artistsMap[primary.id] = ResolvedArtist(
                id = primary.id,
                name = bestName,
                img = bestImg,
                real = group.any { it.real },
                aliases = aliasIds
            )
        }
        // Keep any nameless artists
        for (cand in rawArtists.values) {
            if (!artistCanon.containsKey(cand.id)) {
                artistCanon[cand.id] = cand.id
                artistsMap[cand.id] = ResolvedArtist(cand.id, cand.name, cand.img, cand.real)
            }
        }

        fun resolveArtistPair(pair: Pair<String?, String>): Pair<String, String> {
            val rawId = pair.first?.takeIf { it.isNotBlank() }
            val name = pair.second.trim()
            val canonId = when {
                rawId != null && artistCanon.containsKey(rawId) -> artistCanon[rawId]!!
                name.isNotEmpty() -> {
                    val syn = TextNormalizer.syntheticArtistId(name)
                    artistCanon[syn] ?: run {
                        val existingByName = byNormName[TextNormalizer.norm(name)]?.firstOrNull()?.id
                        existingByName ?: syn.also {
                            artistCanon[it] = it
                            artistsMap[it] = ResolvedArtist(it, name, null, false)
                        }
                    }
                }
                else -> {
                    val q = "n:"
                    artistsMap.putIfAbsent(q, ResolvedArtist(q, "?", null, false))
                    q
                }
            }
            val displayName = artistsMap[canonId]?.name?.takeIf { it != "…" } ?: name.ifEmpty { "?" }
            return canonId to displayName
        }

        // 3. Build resolved albums & tracks using canonical artist IDs
        val albumsMap = LinkedHashMap<String, ResolvedAlbum>()
        for (al in raw.albums.values) {
            val trackIds = parseStringList(al.tracksJson)
            val resolvedArtists = parseArtistsList(al.artistsJson).map(::resolveArtistPair)
                .distinctBy { it.first }
            albumsMap[al.id] = ResolvedAlbum(
                id = al.id,
                name = al.name.ifBlank { "…" },
                type = al.type,
                total = al.total ?: trackIds.size,
                date = al.date,
                img = al.img?.takeIf { it.isNotBlank() },
                artists = resolvedArtists,
                tracks = trackIds,
                st = al.st
            )
        }

        val tracksMap = LinkedHashMap<String, ResolvedTrack>()
        fun addTrack(t: TrackRecord) {
            val rawPairs = parseArtistsList(t.artistsJson).toMutableList()
            val hint = parseTrackHint(t.hintJson)
            if (rawPairs.isEmpty() && !hint?.an.isNullOrBlank()) {
                rawPairs.add(null to hint!!.an!!)
            }
            if (rawPairs.isEmpty()) rawPairs.add(null to "?")
            val resolvedArtists = rawPairs.map(::resolveArtistPair).distinctBy { it.first }
            tracksMap[t.id] = ResolvedTrack(
                id = t.id,
                name = t.name.ifBlank { "?" },
                albumId = t.album?.takeIf { it.isNotBlank() },
                albumHint = hint?.al,
                dur = t.dur,
                isrc = t.isrc?.takeIf { it.isNotBlank() },
                artists = resolvedArtists,
                st = t.st,
                disc = t.disc.coerceAtLeast(1),
                num = t.num
            )
        }
        for (t in raw.tracks.values) addTrack(t)

        // 4. Enhanced Canonical Album Edition Deduplication
        // Collapses Standard / Deluxe / Expanded / Remastered / cross-source duplicates!
        val albumCanon = HashMap<String, String>()
        val albumGroups = LinkedHashMap<String, MutableList<ResolvedAlbum>>()
        for (al in albumsMap.values) {
            albumCanon[al.id] = al.id
            val primaryArtist = al.artists.firstOrNull()?.first.orEmpty()
            val cleanName = TextNormalizer.cleanAlbumTitle(al.name)
            if (cleanName.isNotEmpty() && primaryArtist.isNotEmpty()) {
                albumGroups.getOrPut("$cleanName|$primaryArtist") { mutableListOf() }.add(al)
            }
        }

        fun albumTrackKeys(al: ResolvedAlbum): Set<String> =
            al.tracks.map { tid -> TextNormalizer.cleanTrackTitle(tracksMap[tid]?.name ?: tid) }.toSet()

        for ((_, group) in albumGroups) {
            if (group.size < 2) continue
            // Prefer Spotify ID, status ok, more tracks, has image, older/exact release date
            group.sortWith(
                compareByDescending<ResolvedAlbum> { it.st == "ok" }
                    .thenByDescending { TextNormalizer.isSpotifyId(it.id) }
                    .thenByDescending { it.tracks.size }
                    .thenByDescending { it.img != null }
                    .thenBy { it.id }
            )
            data class CanonicalHolder(var album: ResolvedAlbum, val editions: MutableList<String>, val keys: MutableSet<String>)
            val holders = mutableListOf<CanonicalHolder>()

            for (cand in group) {
                val candKeys = albumTrackKeys(cand)
                val match = holders.find { h ->
                    when {
                        // Same exact cleaned album title & same canonical artist, and one is stub/empty -> merge!
                        candKeys.isEmpty() || h.keys.isEmpty() -> true
                        else -> {
                            val overlap = candKeys.count { it in h.keys }
                            val ratio = overlap.toDouble() / minOf(candKeys.size, h.keys.size).coerceAtLeast(1)
                            ratio >= 0.70
                        }
                    }
                }
                if (match != null) {
                    match.editions.add(cand.id)
                    match.keys.addAll(candKeys)
                    albumCanon[cand.id] = match.album.id
                    albumsMap[cand.id] = cand.copy(aliasOf = match.album.id)
                    val bestImg = match.album.img ?: cand.img
                    val bestDate = match.album.date.ifBlank { cand.date }
                    val mergedTracks = if (match.album.tracks.isEmpty() && cand.tracks.isNotEmpty()) cand.tracks else match.album.tracks
                    match.album = match.album.copy(
                        img = bestImg,
                        date = bestDate,
                        tracks = mergedTracks,
                        editions = match.editions.toList()
                    )
                    albumsMap[match.album.id] = match.album
                } else {
                    val h = CanonicalHolder(cand.copy(editions = listOf(cand.id)), mutableListOf(cand.id), candKeys.toMutableSet())
                    holders.add(h)
                    albumsMap[cand.id] = h.album
                }
            }
        }

        // 5. Parse play chunks (Spotify + Last.fm) & unresolved names
        val spChunks = mutableListOf<ChunkRecord>()
        val lfmChunks = mutableListOf<ChunkRecord>()
        for (c in raw.chunks.values) {
            if (c.src == "sp") spChunks.add(c) else lfmChunks.add(c)
        }

        var spCutoff = 0L
        val spRawRows = mutableListOf<Triple<Long, Long, String>>()
        for (chunk in spChunks) {
            runCatching {
                val arr = json.parseToJsonElement(chunk.dataJson).jsonArray
                for (el in arr) {
                    val row = el.jsonArray
                    val ts = row.getOrNull(0)?.jsonPrimitive?.longOrNull ?: continue
                    val ms = row.getOrNull(1)?.jsonPrimitive?.longOrNull ?: 0L
                    val ref = row.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: continue
                    if (ts > spCutoff) spCutoff = ts
                    spRawRows.add(Triple(ts, ms, ref))
                }
            }
        }

        val plays = mutableListOf<PlayEvent>()
        data class UnresAcc(var count: Int, var first: Long, var last: Long)
        val unresMap = HashMap<String, UnresAcc>()

        fun resolveName(nameId: String): Pair<Boolean, String?> {
            // Pair(isIgnored, trackIdOrNull)
            val nr = raw.names[nameId] ?: return false to null
            return when (nr.st) {
                "ign" -> true to null
                "ok" -> if (!nr.tid.isNullOrBlank()) false to nr.tid else false to null
                else -> false to null
            }
        }

        fun addUnresolved(nameId: String, ts: Long) {
            val acc = unresMap[nameId]
            if (acc == null) {
                unresMap[nameId] = UnresAcc(1, ts, ts)
            } else {
                acc.count++
                if (ts < acc.first) acc.first = ts
                if (ts > acc.last) acc.last = ts
            }
        }

        var spCount = 0
        var lfmCount = 0

        for ((ts, ms, ref) in spRawRows) {
            if (ms < MIN_PLAY_MS) continue
            val playTs = ts - (ms / 1000.0).roundToLong()
            if (ref.startsWith("#")) {
                val nid = ref.substring(1)
                val (ignored, tid) = resolveName(nid)
                if (ignored) continue
                if (tid == null) {
                    addUnresolved(nid, playTs)
                    continue
                }
                plays.add(PlayEvent(playTs, tid, ms, nid, false))
                spCount++
            } else {
                plays.add(PlayEvent(playTs, ref, ms, null, false))
                spCount++
            }
        }

        for (chunk in lfmChunks) {
            runCatching {
                val arr = json.parseToJsonElement(chunk.dataJson).jsonArray
                for (el in arr) {
                    val row = el.jsonArray
                    val ts = row.getOrNull(0)?.jsonPrimitive?.longOrNull ?: continue
                    val nid = row.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: continue
                    if (spCutoff > 0L && ts < spCutoff - CUTOFF_GRACE_SEC) continue
                    val (ignored, tid) = resolveName(nid)
                    if (ignored) continue
                    if (tid == null) {
                        addUnresolved(nid, ts)
                        continue
                    }
                    plays.add(PlayEvent(ts, tid, 0L, nid, true))
                    lfmCount++
                }
            }
        }

        plays.sortBy { it.ts }

        // Ensure stub track exists for every played trackId
        for (p in plays) {
            if (!tracksMap.containsKey(p.trackId)) {
                addTrack(TrackRecord(id = p.trackId, name = "…", st = "stub"))
            }
        }

        // 6. Union-Find to collapse song versions (same cleaned title + canonical artist OR same ISRC OR manual merge)
        val splitSet = mergeCfg.split.toSet()
        val parent = HashMap<String, String>()
        fun find(x: String): String {
            var cur = x
            while (parent[cur] != null && parent[cur] != cur) cur = parent[cur]!!
            var node = x
            while (node != cur) {
                val next = parent[node] ?: break
                parent[node] = cur
                node = next
            }
            return cur
        }

        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) {
                // Prefer Spotify ID as canonical root if one is Spotify and the other is Deezer
                val keepA = when {
                    TextNormalizer.isSpotifyId(ra) && !TextNormalizer.isSpotifyId(rb) -> true
                    !TextNormalizer.isSpotifyId(ra) && TextNormalizer.isSpotifyId(rb) -> false
                    else -> ra < rb
                }
                if (keepA) parent[rb] = ra else parent[ra] = rb
            }
        }

        fun isSingleTrack(t: ResolvedTrack): Boolean {
            val al = t.albumId?.let { albumsMap[it] } ?: return false
            return al.st == "ok" && al.total < ALBUM_MIN_TRACKS
        }

        val bySongKey = HashMap<String, MutableList<ResolvedTrack>>()
        for (t in tracksMap.values) {
            if (t.st == "stub" || t.st == "err") continue
            val firstArtist = t.artists.firstOrNull()?.first ?: continue
            val key = TextNormalizer.cleanTrackTitle(t.name) + "|" + firstArtist
            bySongKey.getOrPut(key) { mutableListOf() }.add(t)
        }
        for (group in bySongKey.values) {
            if (group.size < 2) continue
            for (i in 0 until group.size) {
                for (j in i + 1 until group.size) {
                    val a = group[i]
                    val b = group[j]
                    if (a.id in splitSet || b.id in splitSet) continue
                    val sameCanonAlbum = a.albumId != null && b.albumId != null &&
                        (albumCanon[a.albumId] ?: a.albumId) == (albumCanon[b.albumId] ?: b.albumId)
                    if (sameCanonAlbum && a.albumId == b.albumId) continue
                    val closeDur = a.dur > 0 && b.dur > 0 && abs(a.dur - b.dur) <= 6000L
                    val sameIsrc = !a.isrc.isNullOrBlank() && a.isrc == b.isrc
                    if (sameIsrc || closeDur || isSingleTrack(a) || isSingleTrack(b) || sameCanonAlbum) {
                        union(a.id, b.id)
                    }
                }
            }
        }

        // ISRC dedup across all tracks
        val byIsrc = HashMap<String, String>()
        for (t in tracksMap.values) {
            val isrc = t.isrc ?: continue
            if (t.id in splitSet) continue
            val prev = byIsrc[isrc]
            if (prev != null) union(prev, t.id) else byIsrc[isrc] = t.id
        }

        // Manual user joins
        for (pair in mergeCfg.join) {
            if (pair.size >= 2 && tracksMap.containsKey(pair[0]) && tracksMap.containsKey(pair[1])) {
                union(pair[0], pair[1])
            }
        }

        // 7. Build ResolvedSongs
        val songOf = HashMap<String, String>()
        val versionBuckets = LinkedHashMap<String, MutableList<String>>()
        for (t in tracksMap.values) {
            val root = find(t.id)
            songOf[t.id] = root
            versionBuckets.getOrPut(root) { mutableListOf() }.add(t.id)
        }

        val songsMap = LinkedHashMap<String, ResolvedSong>()
        for ((rootId, verIds) in versionBuckets) {
            val vers = verIds.mapNotNull { tracksMap[it] }.sortedWith(
                compareBy<ResolvedTrack> { if (isSingleTrack(it)) 0 else 1 }
                    .thenBy { if (TextNormalizer.isSpotifyId(it.id)) 0 else 1 }
                    .thenBy { it.albumId?.let { aid -> albumsMap[aid]?.date }?.ifBlank { "9999" } ?: "9999" }
            )
            if (vers.isEmpty()) continue
            val primary = vers.first()
            val primaryAl = primary.albumId?.let { albumsMap[it] }
            val img = primaryAl?.img ?: vers.firstNotNullOfOrNull { it.albumId?.let { aid -> albumsMap[aid]?.img } }
            val date = vers.mapNotNull { it.albumId?.let { aid -> albumsMap[aid]?.date }?.takeIf { d -> d.isNotBlank() } }
                .minOrNull().orEmpty()

            val mainArtistIds = LinkedHashSet<String>()
            val allArtistMap = LinkedHashMap<String, String>()
            for (v in vers) {
                val al = v.albumId?.let { albumsMap[it] }
                val alArtistIds = al?.artists?.map { it.first }?.filter { it != VARIOUS_ARTISTS_ID }?.toSet().orEmpty()
                val mainForTrack = v.artists.filter { it.first in alArtistIds }.ifEmpty { listOfNotNull(v.artists.firstOrNull()) }
                mainForTrack.forEach { mainArtistIds.add(it.first) }
                for ((aid, aname) in v.artists) {
                    allArtistMap.putIfAbsent(aid, artistsMap[aid]?.name ?: aname)
                }
            }
            val orderedArtists = allArtistMap.entries
                .map { it.key to it.value }
                .sortedByDescending { it.first in mainArtistIds }

            val canonAlbums = vers.mapNotNull { v ->
                v.albumId?.let { albumCanon[it] ?: it }
            }.distinct()

            val inAlbum = vers.any { v ->
                val al = v.albumId?.let { albumsMap[it] }
                al != null && al.st == "ok" && al.total >= ALBUM_MIN_TRACKS &&
                    al.artists.any { it.first in mainArtistIds }
            }

            songsMap[rootId] = ResolvedSong(
                id = rootId,
                title = primary.name,
                versions = vers.map { it.id },
                dur = primary.dur,
                img = img,
                date = date,
                artists = orderedArtists,
                mainArtists = mainArtistIds,
                albums = canonAlbums,
                isSingle = vers.any { isSingleTrack(it) },
                inAlbum = inAlbum,
                resolved = vers.any { it.st == "ok" || it.st == "part" }
            )
        }

        // 8. Unresolved map & years
        val unresolved = LinkedHashMap<String, UnresolvedEntry>()
        for ((nid, acc) in unresMap) {
            val nr = raw.names[nid]
            unresolved[nid] = UnresolvedEntry(
                nameId = nid,
                artist = nr?.artist ?: "?",
                track = nr?.track ?: "?",
                album = nr?.album.orEmpty(),
                count = acc.count,
                firstTs = acc.first,
                lastTs = acc.last,
                tries = nr?.tries ?: 0
            )
        }

        val zone = ZoneId.systemDefault()
        val years = plays.map { Instant.ofEpochSecond(it.ts).atZone(zone).year }.distinct().sortedDescending()

        return SolcoModel(
            artists = artistsMap,
            artistCanon = artistCanon,
            albums = albumsMap,
            albumCanon = albumCanon,
            tracks = tracksMap,
            songs = songsMap,
            songOf = songOf,
            discOff = discOff,
            plays = plays,
            unresolved = unresolved,
            cutoff = spCutoff,
            spCount = spCount,
            lfmCount = lfmCount,
            years = years,
            firstPlay = plays.firstOrNull()?.ts ?: 0L
        )
    }

    fun periodBoundsSec(periodKey: String, nowSec: Long = System.currentTimeMillis() / 1000L): Pair<Long, Long>? {
        return when {
            periodKey.isBlank() || periodKey == "all" -> null
            periodKey == "d7" -> (nowSec - 7 * 86400L) to Long.MAX_VALUE
            periodKey == "d30" -> (nowSec - 30 * 86400L) to Long.MAX_VALUE
            periodKey == "d90" -> (nowSec - 90 * 86400L) to Long.MAX_VALUE
            periodKey == "y1" -> (nowSec - 365 * 86400L) to Long.MAX_VALUE
            periodKey.startsWith("year:") -> {
                val yr = periodKey.removePrefix("year:").toIntOrNull() ?: return null
                val zone = ZoneId.systemDefault()
                val start = LocalDate.of(yr, 1, 1).atStartOfDay(zone).toEpochSecond()
                val end = LocalDate.of(yr + 1, 1, 1).atStartOfDay(zone).toEpochSecond()
                start to end
            }
            else -> null
        }
    }

    private fun albumSkipAllowance(trackCount: Int): Int = when {
        trackCount >= 21 -> 4
        trackCount >= 15 -> 3
        trackCount >= 10 -> 2
        trackCount >= 6 -> 1
        else -> 0
    }

    private fun fnvHash(s: String): UInt {
        var h = 2166136261u
        for (ch in s) {
            h = (h xor ch.code.toUInt()) * 16777619u
        }
        return h
    }

    fun computeStats(model: SolcoModel, periodKey: String = "all"): PeriodStats {
        val bounds = periodBoundsSec(periodKey)
        data class MutableSongStat(var n: Int = 0, var ms: Long = 0L, var first: Long = Long.MAX_VALUE, var last: Long = 0L)
        val songStats = HashMap<String, MutableSongStat>()
        val verPlays = HashMap<String, Int>()
        var total = 0
        var totalMs = 0L

        for (p in model.plays) {
            if (bounds != null && (p.ts < bounds.first || p.ts >= bounds.second)) continue
            val sid = model.songOf[p.trackId] ?: p.trackId
            val durMs = if (p.ms > 0L) p.ms else (model.tracks[p.trackId]?.dur ?: 0L)
            val st = songStats.getOrPut(sid) { MutableSongStat(first = p.ts, last = p.ts) }
            st.n++
            st.ms += durMs
            if (p.ts < st.first) st.first = p.ts
            if (p.ts > st.last) st.last = p.ts
            verPlays[p.trackId] = (verPlays[p.trackId] ?: 0) + 1
            total++
            totalMs += durMs
        }

        val finalSongs = songStats.mapValues { (_, v) -> SongStat(v.n, v.ms, v.first, v.last) }

        val finalAlbums = HashMap<String, AlbumStat>()
        for (al in model.albums.values) {
            if (al.aliasOf != null) continue
            var sumPlays = 0
            var sumMs = 0L
            var directPlays = 0
            var lastTs = 0L

            for (edId in al.editions) {
                val ed = model.albums[edId] ?: continue
                for (tid in ed.tracks) directPlays += verPlays[tid] ?: 0
            }

            val seenSongs = LinkedHashSet<String>()
            val offDiscs = model.discOff[al.id].orEmpty().toSet()
            val trackPlayPairs = mutableListOf<Pair<Int, String>>()

            for (tid in al.tracks) {
                val disc = model.tracks[tid]?.disc ?: 1
                if (disc in offDiscs) continue
                val sid = model.songOf[tid] ?: tid
                if (!seenSongs.add(sid)) continue
                val st = finalSongs[sid]
                val n = st?.plays ?: 0
                trackPlayPairs.add(n to sid)
                sumPlays += n
                sumMs += st?.ms ?: 0L
                if (st != null && st.lastTs > lastTs) lastTs = st.lastTs
            }

            trackPlayPairs.sortWith(compareBy<Pair<Int, String>> { it.first }.thenBy { fnvHash(it.second) })
            val skip = albumSkipAllowance(trackPlayPairs.size)
            val fullPlays = if (trackPlayPairs.isNotEmpty()) trackPlayPairs[skip.coerceAtMost(trackPlayPairs.lastIndex)].first else 0
            val skippedSet = trackPlayPairs.take(skip).map { it.second }.toSet()

            if (directPlays > 0 || sumPlays > 0) {
                finalAlbums[al.id] = AlbumStat(
                    fullPlays = fullPlays,
                    totalPlays = sumPlays,
                    directPlays = directPlays,
                    ms = sumMs,
                    lastTs = lastTs,
                    songCount = seenSongs.size,
                    skippedCount = skip,
                    skippedSongIds = skippedSet,
                    minSongPlays = trackPlayPairs.firstOrNull()?.first ?: 0
                )
            }
        }

        // Artist aggregation — ALWAYS using canonical artist IDs so play counts stay 100% unified & updated!
        data class MutableArtistStat(var n: Int = 0, var ms: Long = 0L, var songs: Int = 0, var first: Long = Long.MAX_VALUE, var last: Long = 0L)
        val artistAcc = HashMap<String, MutableArtistStat>()
        for ((sid, st) in finalSongs) {
            val song = model.songs[sid] ?: continue
            val countedForSong = HashSet<String>()
            for ((aid, _) in song.artists) {
                val canonAid = model.artistCanon[aid] ?: aid
                if (!countedForSong.add(canonAid)) continue
                val acc = artistAcc.getOrPut(canonAid) { MutableArtistStat(first = st.firstTs, last = st.lastTs) }
                acc.n += st.plays
                acc.ms += st.ms
                acc.songs++
                if (st.firstTs < acc.first) acc.first = st.firstTs
                if (st.lastTs > acc.last) acc.last = st.lastTs
            }
        }
        val finalArtists = artistAcc.mapValues { (_, v) -> ArtistStat(v.n, v.ms, v.songs, v.first, v.last) }

        return PeriodStats(
            periodKey = periodKey,
            song = finalSongs,
            version = verPlays,
            album = finalAlbums,
            artist = finalArtists,
            totalPlays = total,
            totalMs = totalMs
        )
    }

    fun monthlyActivity(
        model: SolcoModel,
        trackIds: Set<String>,
        months: Int = 12
    ): List<MonthlyBucket> {
        val now = LocalDate.now()
        val keys = (months - 1 downTo 0).map { offset ->
            val d = now.minusMonths(offset.toLong())
            d.year to d.monthValue
        }
        val counts = keys.associateWith { 0 }.toMutableMap()
        val startEpoch = now.minusMonths((months - 1).toLong()).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val zone = ZoneId.systemDefault()

        for (i in model.plays.lastIndex downTo 0) {
            val p = model.plays[i]
            if (p.ts < startEpoch) break
            if (p.trackId !in trackIds) continue
            val d = Instant.ofEpochSecond(p.ts).atZone(zone)
            val k = d.year to d.monthValue
            if (k in counts) counts[k] = (counts[k] ?: 0) + 1
        }
        return keys.map { (y, m) ->
            MonthlyBucket(y, m, monthShorts[(m - 1).coerceIn(0, 11)], counts[y to m] ?: 0)
        }
    }

    fun computeGoalProgress(
        reviews: Collection<ReviewRecord>,
        entityType: String,
        goals: GoalsConfig,
        today: LocalDate = LocalDate.now()
    ): GoalProgress {
        val rule = if (entityType == "album") goals.album else goals.track
        val todayStr = today.toString()
        val startStr = rule.start.ifBlank { todayStr }
        val per = rule.per.coerceAtLeast(1)
        val zone = ZoneId.systemDefault()

        data class PauseWindow(val fromMs: Long, val toMs: Long, val d1: String, val d2: String)
        val pauses = goals.pauses.map { p ->
            val d1 = Instant.ofEpochMilli(p.from).atZone(zone).toLocalDate().toString()
            val d2 = if (p.to != null) Instant.ofEpochMilli(p.to).atZone(zone).toLocalDate().toString() else "9999-12-31"
            PauseWindow(p.from, p.to ?: Long.MAX_VALUE, d1, d2)
        }

        fun isDayPaused(day: String) = pauses.any { day >= it.d1 && day < it.d2 }
        fun isCreatedPaused(createdMs: Long) = pauses.any { createdMs >= it.fromMs && createdMs < it.toMs }

        var count = 0
        var todayCount = 0
        for (r in reviews) {
            if (r.del != 0 || r.et != entityType) continue
            if (isCreatedPaused(r.created)) continue
            if (r.day >= startStr && r.day <= todayStr) count++
            if (r.day == todayStr) todayCount++
        }

        var elapsed = 0
        val startDate = runCatching { LocalDate.parse(startStr) }.getOrDefault(today)
        var cursor = startDate
        while (cursor.isBefore(today)) {
            if (!isDayPaused(cursor.toString())) elapsed++
            cursor = cursor.plusDays(1)
        }
        val pausedNow = pauses.any { it.toMs == Long.MAX_VALUE }
        return GoalProgress(
            balance = count - elapsed * per,
            count = count,
            todayCount = todayCount,
            elapsed = elapsed,
            paused = pausedNow
        )
    }

    // --- UwuFufu Tournament Bracket Engine ---

    fun buildBracketSeed(ids: List<String>, shuffle: Boolean = true, rng: Random = Random.Default): List<String?> {
        if (ids.size < 2) return ids
        val ordered = if (shuffle) ids.shuffled(rng) else ids
        var size = 2
        while (size < ordered.size) size *= 2
        val byes = size - ordered.size
        val pairs = mutableListOf<Pair<String?, String?>>()
        var idx = 0
        for (i in 0 until size / 2) {
            if (i < byes) {
                pairs.add(ordered[idx++] to null)
            } else {
                pairs.add(ordered[idx++] to ordered[idx++])
            }
        }
        if (shuffle) pairs.shuffle(rng)
        return pairs.flatMap { listOf(it.first, it.second) }
    }

    fun evaluateBracket(tournament: UwuTournament): UwuBracketState {
        var round = tournament.seed
        if (round.isEmpty()) return UwuBracketState(done = true, champion = null)
        val elim = LinkedHashMap<String, Int>()
        var pickIdx = 0
        var roundNo = 0
        while (round.size > 1) {
            val next = mutableListOf<String?>()
            for (i in round.indices step 2) {
                val a = round[i]
                val b = round.getOrNull(i + 1)
                if (a == null || b == null) {
                    next.add(a ?: b)
                    continue
                }
                if (pickIdx >= tournament.picks.size) {
                    val activeMatches = mutableListOf<Int>()
                    for (j in round.indices step 2) {
                        if (round[j] != null && round.getOrNull(j + 1) != null) activeMatches.add(j)
                    }
                    return UwuBracketState(
                        done = false,
                        a = a,
                        b = b,
                        roundIndex = roundNo,
                        roundSize = round.size,
                        matchNo = activeMatches.indexOf(i) + 1,
                        matchesInRound = activeMatches.size,
                        picksUsed = pickIdx,
                        eliminatedAtSize = elim
                    )
                }
                val winner = if (tournament.picks[pickIdx++] == 0) a else b
                val loser = if (winner == a) b else a
                elim[loser] = round.size
                next.add(winner)
            }
            round = next
            roundNo++
        }
        return UwuBracketState(
            done = true,
            champion = round.firstOrNull(),
            picksUsed = pickIdx,
            eliminatedAtSize = elim
        )
    }

    fun roundLabel(size: Int): String = when (size) {
        2 -> "Finale"
        4 -> "Semifinali"
        8 -> "Quarti di finale"
        16 -> "Ottavi di finale"
        32 -> "Sedicesimi"
        64 -> "Trentaduesimi"
        else -> "Turno da $size"
    }
}
