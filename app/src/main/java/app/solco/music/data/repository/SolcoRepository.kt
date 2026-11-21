package app.solco.music.data.repository

import android.content.Context
import android.net.Uri
import app.solco.music.data.local.AlbumEntity
import app.solco.music.data.local.ArtistEntity
import app.solco.music.data.local.ChunkEntity
import app.solco.music.data.local.KvEntity
import app.solco.music.data.local.NameEntity
import app.solco.music.data.local.NoteEntity
import app.solco.music.data.local.OutboxEntity
import app.solco.music.data.local.ReleaseCacheEntity
import app.solco.music.data.local.ReviewEntity
import app.solco.music.data.local.SolcoDatabase
import app.solco.music.data.local.SyncMetaEntity
import app.solco.music.data.local.TrackEntity
import app.solco.music.data.model.AlbumRecord
import app.solco.music.data.model.ArtistRecord
import app.solco.music.data.model.ChunkRecord
import app.solco.music.data.model.GoalPause
import app.solco.music.data.model.GoalsConfig
import app.solco.music.data.model.KvRecord
import app.solco.music.data.model.LastFmConfig
import app.solco.music.data.model.LastFmProfile
import app.solco.music.data.model.LastFmState
import app.solco.music.data.model.LastFmSyncStatus
import app.solco.music.data.model.MergeConfig
import app.solco.music.data.model.NameRecord
import app.solco.music.data.model.NoteRecord
import app.solco.music.data.model.NowPlaying
import app.solco.music.data.model.PeriodStats
import app.solco.music.data.model.ReleaseItem
import app.solco.music.data.model.ReleaseScanState
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.model.SolcoModel
import app.solco.music.data.model.SpotifyConfig
import app.solco.music.data.model.TrackRecord
import app.solco.music.data.model.TrackReviewEntry
import app.solco.music.data.model.UwuTournament
import app.solco.music.data.remote.AuthSession
import app.solco.music.data.remote.FirebaseRestClient
import app.solco.music.data.remote.FullAlbumResult
import app.solco.music.data.remote.MatchedTrackResult
import app.solco.music.data.remote.MusicApiClients
import app.solco.music.domain.ModelEngine
import app.solco.music.domain.RatingEngine
import app.solco.music.domain.RawStoreSnapshot
import app.solco.music.domain.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipInputStream

data class SolcoUiSnapshot(
    val phase: String = "boot", // "boot", "login", "ready"
    val userEmail: String? = null,
    val syncing: Boolean = false,
    val matching: Boolean = false,
    val matchProgress: String = "",
    val lastSyncMs: Long = 0L,
    val error: String? = null,
    val model: SolcoModel = SolcoModel(),
    val statsAll: PeriodStats = ModelEngine.computeStats(SolcoModel(), "all"),
    val reviews: Map<String, ReviewRecord> = emptyMap(),
    val notes: Map<String, NoteRecord> = emptyMap(),
    val kv: Map<String, KvRecord> = emptyMap(),
    val releases: List<ReleaseItem> = emptyList(),
    val releaseScan: ReleaseScanState = ReleaseScanState()
) {
    private val json get() = SolcoRepository.json

    val lastFmConfig: LastFmConfig
        get() = kv["cfg.lastfm"]?.vJson?.let { runCatching { json.decodeFromString<LastFmConfig>(it) }.getOrNull() }
            ?: LastFmConfig()

    val spotifyConfig: SpotifyConfig
        get() = kv["cfg.spotify"]?.vJson?.let { runCatching { json.decodeFromString<SpotifyConfig>(it) }.getOrNull() }
            ?: SpotifyConfig()

    val nowPlaying: NowPlaying?
        get() = kv["lfm.now"]?.vJson?.let { runCatching { json.decodeFromString<NowPlaying>(it) }.getOrNull() }

    val lastFmProfile: LastFmProfile?
        get() = kv["lfm.profile"]?.vJson?.let { runCatching { json.decodeFromString<LastFmProfile>(it) }.getOrNull() }

    val lastFmStatus: LastFmSyncStatus?
        get() = kv["lfm.status"]?.vJson?.let { runCatching { json.decodeFromString<LastFmSyncStatus>(it) }.getOrNull() }

    val goalsConfig: GoalsConfig
        get() = kv["goals"]?.vJson?.let { runCatching { json.decodeFromString<GoalsConfig>(it) }.getOrNull() }
            ?: GoalsConfig(
                album = app.solco.music.data.model.GoalRule(LocalDate.now().toString(), 1),
                track = app.solco.music.data.model.GoalRule(LocalDate.now().toString(), 1)
            )

    val showCalculatedAlbumScore: Boolean
        get() = kv["ui.albumScore"]?.vJson?.trim() != "false"

    val savedReleases: List<ReleaseItem>
        get() = kv["releases.saved"]?.vJson?.let { runCatching { json.decodeFromString<List<ReleaseItem>>(it) }.getOrNull() }
            ?: emptyList()

    val tournaments: List<UwuTournament>
        get() = kv.values
            .filter { it.k.startsWith("uwu:") && it.vJson != "null" && it.vJson.isNotBlank() }
            .mapNotNull { runCatching { json.decodeFromString<UwuTournament>(it.vJson) }.getOrNull() }
            .sortedByDescending { it.updated.coerceAtLeast(it.created) }

    val topMap: Map<String, Map<String, Int>>
        get() = kv["top"]?.vJson?.let { runCatching { json.decodeFromString<Map<String, Map<String, Int>>>(it) }.getOrNull() }
            ?: emptyMap()
}

data class ImportSummary(
    val filesParsed: Int,
    val totalEntries: Int,
    val keptPlays: Int,
    val skippedShort: Int,
    val skippedPodcasts: Int
)

class SolcoRepository(
    private val context: Context,
    private val db: SolcoDatabase = SolcoDatabase.getInstance(context),
    val firebase: FirebaseRestClient = FirebaseRestClient(context),
    val musicApi: MusicApiClients = MusicApiClients()
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        private val ALL_COLLECTIONS = listOf("kv", "chunks", "names", "tracks", "albums", "artists", "notes", "reviews")
    }

    private val dao = db.dao()
    private val syncMutex = Mutex()
    private val releaseMutex = Mutex()
    private val periodCache = HashMap<String, PeriodStats>()

    private val _state = MutableStateFlow(SolcoUiSnapshot())
    val state: StateFlow<SolcoUiSnapshot> = _state.asStateFlow()

    // In-memory mirrors for ultra-fast rebuilds
    private var kvMap = LinkedHashMap<String, KvRecord>()
    private var chunksMap = LinkedHashMap<String, ChunkRecord>()
    private var namesMap = LinkedHashMap<String, NameRecord>()
    private var tracksMap = LinkedHashMap<String, TrackRecord>()
    private var albumsMap = LinkedHashMap<String, AlbumRecord>()
    private var artistsMap = LinkedHashMap<String, ArtistRecord>()
    private var notesMap = LinkedHashMap<String, NoteRecord>()
    private var reviewsMap = LinkedHashMap<String, ReviewRecord>()

    suspend fun boot() = withContext(Dispatchers.IO) {
        val session = firebase.currentSession()
        if (session == null) {
            _state.update { it.copy(phase = "login", userEmail = null) }
            return@withContext
        }
        loadLocalIntoMemory(session)
        _state.update { it.copy(phase = "ready", userEmail = session.email) }
        syncAll(runMatcher = true)
    }

    suspend fun signIn(email: String, pass: String) {
        val session = firebase.signIn(email, pass)
        onAuthenticated(session)
    }

    suspend fun signUp(email: String, pass: String) {
        val session = firebase.signUp(email, pass)
        onAuthenticated(session)
    }

    suspend fun sendPasswordReset(email: String) {
        firebase.sendPasswordReset(email)
    }

    private suspend fun onAuthenticated(session: AuthSession) = withContext(Dispatchers.IO) {
        val prevUid = dao.getMeta("uid")
        if (prevUid != null && prevUid != session.uid) {
            db.clearAllUserTables()
        }
        dao.putMeta(SyncMetaEntity("uid", session.uid))
        loadLocalIntoMemory(session)
        _state.update { it.copy(phase = "ready", userEmail = session.email, error = null) }
        syncAll(runMatcher = true)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        firebase.signOut()
        db.clearAllUserTables()
        kvMap.clear(); chunksMap.clear(); namesMap.clear(); tracksMap.clear()
        albumsMap.clear(); artistsMap.clear(); notesMap.clear(); reviewsMap.clear()
        periodCache.clear()
        _state.value = SolcoUiSnapshot(phase = "login")
    }

    private suspend fun loadLocalIntoMemory(session: AuthSession) {
        kvMap = LinkedHashMap(dao.getAllKv().associate { it.k to it.toRecord() })
        chunksMap = LinkedHashMap(dao.getAllChunks().associate { it.id to it.toRecord() })
        namesMap = LinkedHashMap(dao.getAllNames().associate { it.id to it.toRecord() })
        tracksMap = LinkedHashMap(dao.getAllTracks().associate { it.id to it.toRecord() })
        albumsMap = LinkedHashMap(dao.getAllAlbums().associate { it.id to it.toRecord() })
        artistsMap = LinkedHashMap(dao.getAllArtists().associate { it.id to it.toRecord() })
        notesMap = LinkedHashMap(dao.getAllNotes().associate { it.id to it.toRecord() })
        reviewsMap = LinkedHashMap(dao.getAllReviews().associate { it.id to it.toRecord() })

        val cachedReleases = dao.getReleases().mapNotNull {
            runCatching { json.decodeFromString<ReleaseItem>(it.payloadJson) }.getOrNull()
        }
        rebuildModel(extraReleases = cachedReleases, email = session.email)
    }

    private fun rebuildModel(
        extraReleases: List<ReleaseItem>? = null,
        email: String? = _state.value.userEmail
    ) {
        val raw = RawStoreSnapshot(
            kv = kvMap.toMap(),
            chunks = chunksMap.toMap(),
            names = namesMap.toMap(),
            tracks = tracksMap.toMap(),
            albums = albumsMap.toMap(),
            artists = artistsMap.toMap(),
            reviews = reviewsMap.toMap()
        )
        val model = ModelEngine.buildModel(raw)
        periodCache.clear()
        val statsAll = ModelEngine.computeStats(model, "all")
        periodCache["all"] = statsAll
        _state.update { cur ->
            cur.copy(
                userEmail = email,
                model = model,
                statsAll = statsAll,
                reviews = reviewsMap.toMap(),
                notes = notesMap.toMap(),
                kv = kvMap.toMap(),
                releases = extraReleases ?: cur.releases
            )
        }
    }

    fun statsForPeriod(periodKey: String): PeriodStats {
        return periodCache.getOrPut(periodKey) {
            ModelEngine.computeStats(_state.value.model, periodKey)
        }
    }

    // ==================== FIRESTORE SYNC & OUTBOX ====================

    suspend fun syncAll(runMatcher: Boolean = true) = withContext(Dispatchers.IO) {
        if (firebase.currentSession() == null) return@withContext
        syncMutex.withLock {
            _state.update { it.copy(syncing = true, error = null) }
            try {
                flushOutbox()
                pullAllFromFirestore()
                rebuildModel()
                // Sync Last.fm if configured
                val lfmCfg = _state.value.lastFmConfig
                if (lfmCfg.user.isNotBlank() && lfmCfg.key.isNotBlank()) {
                    syncLastFm(lfmCfg)
                }
                _state.update { it.copy(lastSyncMs = System.currentTimeMillis(), error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Errore sincronizzazione") }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
        if (runMatcher) {
            runSmartMatcher(maxItems = 80)
        }
    }

    private suspend fun flushOutbox() {
        val pending = dao.getOutbox()
        for (op in pending) {
            val map = runCatching { jsonStringToAnyMap(op.payloadJson) }.getOrNull() ?: continue
            firebase.setDocument(op.collection, op.docId, map)
            dao.deleteOutbox(op.opKey)
        }
    }

    private suspend fun pullAllFromFirestore() {
        for (col in ALL_COLLECTIONS) {
            val cursorKey = "cursor:$col"
            val sinceMs = dao.getMeta(cursorKey)?.toLongOrNull() ?: 0L
            val docs = firebase.fetchCollectionSince(col, sinceMs)
            if (docs.isEmpty()) continue
            var maxU = sinceMs
            when (col) {
                "kv" -> {
                    val list = docs.mapNotNull { d ->
                        val k = (d["k"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        val vStr = anyToJsonString(d["v"])
                        KvEntity(k, vStr, u).also { kvMap[k] = it.toRecord() }
                    }
                    dao.upsertKv(list)
                }
                "chunks" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        val src = (d["src"] as? String) ?: if (id.startsWith("sp")) "sp" else "lfm"
                        val dataStr = (d["data"] as? String) ?: anyToJsonString(d["data"])
                        val n = (d["n"] as? Number)?.toInt() ?: 0
                        ChunkEntity(id, src, dataStr, n, u).also { chunksMap[id] = it.toRecord() }
                    }
                    dao.upsertChunks(list)
                }
                "names" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        NameEntity(
                            id = id,
                            k = (d["k"] as? String).orEmpty(),
                            artist = (d["artist"] as? String).orEmpty(),
                            track = (d["track"] as? String).orEmpty(),
                            album = (d["album"] as? String).orEmpty(),
                            tid = d["tid"] as? String,
                            st = (d["st"] as? String) ?: "new",
                            tries = (d["tries"] as? Number)?.toInt() ?: 0,
                            nt = (d["nt"] as? Number)?.toLong() ?: 0L,
                            u = u
                        ).also { namesMap[id] = it.toRecord() }
                    }
                    dao.upsertNames(list)
                }
                "tracks" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        val artistsStr = (d["artists"] as? String) ?: anyToJsonString(d["artists"])
                        val hintStr = (d["hint"] as? String) ?: d["hint"]?.let(::anyToJsonString)
                        TrackEntity(
                            id = id,
                            name = (d["name"] as? String) ?: "?",
                            album = d["album"] as? String,
                            dur = (d["dur"] as? Number)?.toLong() ?: 0L,
                            isrc = d["isrc"] as? String,
                            artistsJson = artistsStr,
                            hintJson = hintStr,
                            st = (d["st"] as? String) ?: "stub",
                            disc = (d["disc"] as? Number)?.toInt() ?: 1,
                            num = (d["num"] as? Number)?.toInt() ?: 0,
                            tries = (d["tries"] as? Number)?.toInt() ?: 0,
                            u = u
                        ).also { tracksMap[id] = it.toRecord() }
                    }
                    dao.upsertTracks(list)
                }
                "albums" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        val artistsStr = (d["artists"] as? String) ?: anyToJsonString(d["artists"])
                        val tracksStr = (d["tracks"] as? String) ?: anyToJsonString(d["tracks"])
                        AlbumEntity(
                            id = id,
                            name = (d["name"] as? String) ?: "…",
                            type = (d["type"] as? String).orEmpty(),
                            total = (d["total"] as? Number)?.toInt(),
                            date = (d["date"] as? String).orEmpty(),
                            img = d["img"] as? String,
                            artistsJson = artistsStr,
                            tracksJson = tracksStr,
                            st = (d["st"] as? String) ?: "stub",
                            tries = (d["tries"] as? Number)?.toInt() ?: 0,
                            u = u
                        ).also { albumsMap[id] = it.toRecord() }
                    }
                    dao.upsertAlbums(list)
                }
                "artists" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        ArtistEntity(
                            id = id,
                            name = (d["name"] as? String) ?: "…",
                            img = d["img"] as? String,
                            st = (d["st"] as? String) ?: "stub",
                            tries = (d["tries"] as? Number)?.toInt() ?: 0,
                            u = u
                        ).also { artistsMap[id] = it.toRecord() }
                    }
                    dao.upsertArtists(list)
                }
                "notes" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        NoteEntity(
                            id = id,
                            et = (d["et"] as? String).orEmpty(),
                            eid = (d["eid"] as? String).orEmpty(),
                            text = (d["text"] as? String).orEmpty(),
                            del = (d["del"] as? Number)?.toInt() ?: 0,
                            u = u
                        ).also { notesMap[id] = it.toRecord() }
                    }
                    dao.upsertNotes(list)
                }
                "reviews" -> {
                    val list = docs.mapNotNull { d ->
                        val id = (d["id"] as? String) ?: (d["_docId"] as? String) ?: return@mapNotNull null
                        val u = (d["u"] as? Long) ?: 0L
                        if (u > maxU) maxU = u
                        val tracksStr = (d["tracks"] as? String) ?: d["tracks"]?.let(::anyToJsonString)
                        ReviewEntity(
                            id = id,
                            et = (d["et"] as? String).orEmpty(),
                            eid = (d["eid"] as? String).orEmpty(),
                            rating = (d["rating"] as? Number)?.toDouble(),
                            mood = (d["mood"] as? Number)?.toInt(),
                            text = (d["text"] as? String).orEmpty(),
                            day = (d["day"] as? String).orEmpty(),
                            tracksJson = tracksStr,
                            created = (d["created"] as? Number)?.toLong() ?: 0L,
                            del = (d["del"] as? Number)?.toInt() ?: 0,
                            u = u
                        ).also { reviewsMap[id] = it.toRecord() }
                    }
                    dao.upsertReviews(list)
                }
            }
            if (maxU > sinceMs) {
                dao.putMeta(SyncMetaEntity(cursorKey, maxU.toString()))
            }
        }
    }

    private suspend fun writeAndSync(collection: String, docId: String, firestoreFields: Map<String, Any?>) {
        val opKey = "$collection:$docId"
        val payloadJson = anyMapToJsonString(firestoreFields)
        dao.enqueueOutbox(OutboxEntity(opKey, collection, docId, payloadJson, System.currentTimeMillis()))
        runCatching {
            firebase.setDocument(collection, docId, firestoreFields)
            dao.deleteOutbox(opKey)
        }
    }

    // ==================== USER WRITE OPERATIONS ====================

    suspend fun putKv(key: String, valueJson: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val ent = KvEntity(key, valueJson, now)
        kvMap[key] = ent.toRecord()
        dao.upsertKv(listOf(ent))
        if (key == "merge" || key == "albums.discOff") {
            rebuildModel()
        } else {
            _state.update { it.copy(kv = kvMap.toMap()) }
        }
        writeAndSync("kv", key, mapOf("k" to key, "v" to valueJson))
    }

    suspend fun setShowCalculatedAlbumScore(show: Boolean) {
        putKv("ui.albumScore", if (show) "true" else "false")
    }

    suspend fun saveLastFmConfig(user: String, key: String): LastFmProfile = withContext(Dispatchers.IO) {
        val cfg = LastFmConfig(user.trim(), key.trim())
        val profile = musicApi.getLastFmProfile(cfg)
        putKv("cfg.lastfm", json.encodeToString(cfg))
        putKv("lfm.profile", json.encodeToString(profile))
        profile
    }

    suspend fun saveSpotifyConfig(id: String, secret: String): String? = withContext(Dispatchers.IO) {
        val cfg = SpotifyConfig(id.trim(), secret.trim())
        if (cfg.id.isNotBlank() && cfg.secret.isNotBlank()) {
            val err = musicApi.testSpotify(cfg)
            if (err != null) return@withContext err
        }
        putKv("cfg.spotify", json.encodeToString(cfg))
        null
    }

    suspend fun saveGoalsConfig(goals: GoalsConfig) {
        putKv("goals", json.encodeToString(goals))
    }

    suspend fun toggleGoalsPause() {
        val cur = _state.value.goalsConfig
        val now = System.currentTimeMillis()
        val openIdx = cur.pauses.indexOfLast { it.to == null }
        val updated = if (openIdx >= 0) {
            cur.pauses.toMutableList().apply { this[openIdx] = this[openIdx].copy(to = now) }
        } else {
            cur.pauses + GoalPause(from = now, to = null)
        }
        saveGoalsConfig(cur.copy(pauses = updated))
    }

    suspend fun saveReview(
        entityType: String,
        entityId: String,
        rating: Double?,
        mood: Int?,
        text: String,
        day: String,
        tracks: Map<String, TrackReviewEntry>
    ) = withContext(Dispatchers.IO) {
        val id = "$entityType:$entityId"
        val existing = reviewsMap[id]
        val created = if (existing != null && existing.del == 0 && existing.created > 0L) existing.created else System.currentTimeMillis()
        val tracksJson = RatingEngine.encodeTrackReviews(tracks)
        val now = System.currentTimeMillis()
        val ent = ReviewEntity(id, entityType, entityId, rating, mood, text, day, tracksJson, created, 0, now)
        reviewsMap[id] = ent.toRecord()
        dao.upsertReviews(listOf(ent))
        _state.update { it.copy(reviews = reviewsMap.toMap()) }
        writeAndSync(
            "reviews",
            id,
            mapOf(
                "id" to id,
                "et" to entityType,
                "eid" to entityId,
                "rating" to rating,
                "mood" to mood,
                "text" to text,
                "day" to day,
                "tracks" to tracksJson,
                "created" to created,
                "del" to 0
            )
        )
    }

    suspend fun deleteReview(id: String) = withContext(Dispatchers.IO) {
        val existing = reviewsMap[id] ?: return@withContext
        val now = System.currentTimeMillis()
        val ent = ReviewEntity(
            existing.id, existing.et, existing.eid, existing.rating, existing.mood,
            existing.text, existing.day, existing.tracksJson, existing.created, 1, now
        )
        reviewsMap[id] = ent.toRecord()
        dao.upsertReviews(listOf(ent))
        _state.update { it.copy(reviews = reviewsMap.toMap()) }
        writeAndSync(
            "reviews",
            id,
            mapOf(
                "id" to existing.id,
                "et" to existing.et,
                "eid" to existing.eid,
                "rating" to existing.rating,
                "mood" to existing.mood,
                "text" to existing.text,
                "day" to existing.day,
                "tracks" to existing.tracksJson,
                "created" to existing.created,
                "del" to 1
            )
        )
    }

    suspend fun saveNote(entityType: String, entityId: String, text: String) = withContext(Dispatchers.IO) {
        val id = "$entityType:$entityId"
        val del = if (text.isBlank()) 1 else 0
        val ent = NoteEntity(id, entityType, entityId, text, del, System.currentTimeMillis())
        notesMap[id] = ent.toRecord()
        dao.upsertNotes(listOf(ent))
        _state.update { it.copy(notes = notesMap.toMap()) }
        writeAndSync(
            "notes",
            id,
            mapOf("id" to id, "et" to entityType, "eid" to entityId, "text" to text, "del" to del)
        )
    }

    suspend fun toggleDiscOff(albumId: String, discNum: Int) {
        val cur = kvMap["albums.discOff"]?.vJson?.let {
            runCatching { json.decodeFromString<Map<String, List<Int>>>(it) }.getOrNull()
        }?.toMutableMap() ?: mutableMapOf()
        val list = cur[albumId]?.toMutableList() ?: mutableListOf()
        if (discNum in list) list.remove(discNum) else list.add(discNum)
        if (list.isEmpty()) cur.remove(albumId) else cur[albumId] = list.sorted()
        putKv("albums.discOff", json.encodeToString(cur))
    }

    suspend fun setTopTier(kind: String, entityId: String, tier: Int?) {
        val cur = _state.value.topMap.mapValues { it.value.toMutableMap() }.toMutableMap()
        val map = cur.getOrPut(kind) { mutableMapOf() }
        if (tier == null) map.remove(entityId) else map[entityId] = tier
        putKv("top", json.encodeToString(cur))
    }

    suspend fun saveTournament(tournament: UwuTournament) {
        putKv("uwu:${tournament.id}", json.encodeToString(tournament))
    }

    suspend fun deleteTournament(id: String) {
        putKv("uwu:$id", "null")
    }

    suspend fun toggleSavedRelease(item: ReleaseItem) {
        val cur = _state.value.savedReleases
        val exists = cur.any { it.id == item.id }
        val updated = if (exists) cur.filterNot { it.id == item.id }
        else listOf(item.copy(savedAt = System.currentTimeMillis())) + cur
        putKv("releases.saved", json.encodeToString(updated))
    }

    suspend fun ignoreUnresolvedName(nameId: String) = withContext(Dispatchers.IO) {
        val nr = namesMap[nameId] ?: return@withContext
        val updated = nr.copy(st = "ign", u = System.currentTimeMillis())
        namesMap[nameId] = updated
        dao.upsertNames(listOf(NameEntity(updated.id, updated.k, updated.artist, updated.track, updated.album, updated.tid, updated.st, updated.tries, updated.nt, updated.u)))
        rebuildModel()
        writeAndSync(
            "names",
            updated.id,
            mapOf(
                "id" to updated.id, "k" to updated.k, "artist" to updated.artist,
                "track" to updated.track, "album" to updated.album, "tid" to updated.tid,
                "st" to "ign", "tries" to updated.tries, "nt" to updated.nt
            )
        )
    }

    suspend fun manualLinkUnresolved(nameId: String, match: MatchedTrackResult) = withContext(Dispatchers.IO) {
        persistMatchedTrackAndAlbum(match)
        val nr = namesMap[nameId] ?: return@withContext
        val updated = nr.copy(tid = match.trackId, st = "ok", u = System.currentTimeMillis())
        namesMap[nameId] = updated
        dao.upsertNames(listOf(NameEntity(updated.id, updated.k, updated.artist, updated.track, updated.album, updated.tid, updated.st, updated.tries, updated.nt, updated.u)))
        rebuildModel()
        writeAndSync(
            "names",
            updated.id,
            mapOf(
                "id" to updated.id, "k" to updated.k, "artist" to updated.artist,
                "track" to updated.track, "album" to updated.album, "tid" to updated.tid,
                "st" to "ok", "tries" to updated.tries, "nt" to updated.nt
            )
        )
    }

    // ==================== LAST.FM SCROBBLE SYNC ====================

    private suspend fun syncLastFm(cfg: LastFmConfig, maxPages: Int = 10) {
        val stateRec = kvMap["lfm.state"]?.vJson?.let {
            runCatching { json.decodeFromString<LastFmState>(it) }.getOrNull()
        } ?: LastFmState(0L)
        val toSec = System.currentTimeMillis() / 1000L
        val firstPage = musicApi.getLastFmRecentPage(cfg, stateRec.last, toSec, 1)

        if (firstPage.nowPlaying != null || kvMap["lfm.now"]?.vJson != "null") {
            putKv("lfm.now", if (firstPage.nowPlaying != null) json.encodeToString(firstPage.nowPlaying) else "null")
        }

        var lastTs = stateRec.last
        var addedTotal = 0
        var pagesFetched = 0
        val newNames = mutableListOf<NameEntity>()
        val monthBuckets = LinkedHashMap<String, MutableList<Pair<Long, String>>>()

        val startPage = firstPage.totalPages.coerceAtLeast(1)
        for (p in startPage downTo 1) {
            if (pagesFetched >= maxPages) break
            val pageData = if (p == 1) firstPage else musicApi.getLastFmRecentPage(cfg, stateRec.last, toSec, p)
            pagesFetched++
            for (sc in pageData.tracks) {
                val nk = TextNormalizer.nameKey(sc.artist, sc.track, sc.album)
                val nid = TextNormalizer.nameIdHash(nk)
                if (!namesMap.containsKey(nid)) {
                    val ne = NameEntity(nid, nk, sc.artist, sc.track, sc.album, null, "new", 0, 0L, System.currentTimeMillis())
                    namesMap[nid] = ne.toRecord()
                    newNames.add(ne)
                }
                val ym = Instant.ofEpochSecond(sc.uts).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                monthBuckets.getOrPut(ym) { mutableListOf() }.add(sc.uts to nid)
                if (sc.uts > lastTs) lastTs = sc.uts
            }
        }

        if (newNames.isNotEmpty()) {
            dao.upsertNames(newNames)
            runCatching {
                firebase.batchSetDocuments("names", newNames.map { ne ->
                    ne.id to mapOf(
                        "id" to ne.id, "k" to ne.k, "artist" to ne.artist, "track" to ne.track,
                        "album" to ne.album, "tid" to null, "st" to "new", "tries" to 0, "nt" to 0L
                    )
                })
            }
        }

        for ((ym, entries) in monthBuckets) {
            val chunkId = "lfm:$ym"
            val existingArr = chunksMap[chunkId]?.dataJson?.let {
                runCatching { json.parseToJsonElement(it).jsonArray }.getOrNull()
            } ?: JsonArray(emptyList())
            val seen = HashSet<String>()
            val merged = mutableListOf<Pair<Long, String>>()
            for (el in existingArr) {
                val pair = el.jsonArray
                val ts = pair.getOrNull(0)?.jsonPrimitive?.longOrNull ?: continue
                val nid = pair.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: continue
                if (seen.add("$ts:$nid")) merged.add(ts to nid)
            }
            var addedChunk = 0
            for (e in entries) {
                if (seen.add("${e.first}:${e.second}")) {
                    merged.add(e)
                    addedChunk++
                }
            }
            if (addedChunk > 0) {
                merged.sortBy { it.first }
                val dataStr = buildJsonArray {
                    for ((ts, nid) in merged) {
                        add(buildJsonArray { add(JsonPrimitive(ts)); add(JsonPrimitive(nid)) })
                    }
                }.toString()
                val ce = ChunkEntity(chunkId, "lfm", dataStr, merged.size, System.currentTimeMillis())
                chunksMap[chunkId] = ce.toRecord()
                dao.upsertChunks(listOf(ce))
                writeAndSync("chunks", chunkId, mapOf("id" to chunkId, "src" to "lfm", "data" to dataStr, "n" to merged.size))
                addedTotal += addedChunk
            }
        }

        if (lastTs > stateRec.last) {
            putKv("lfm.state", json.encodeToString(LastFmState(lastTs)))
        }
        putKv(
            "lfm.status",
            json.encodeToString(LastFmSyncStatus(ok = true, at = System.currentTimeMillis(), added = addedTotal, done = pagesFetched >= startPage))
        )
        if (addedTotal > 0 || newNames.isNotEmpty()) {
            rebuildModel()
        }
    }

    // ==================== MULTI-SOURCE SMART TRACK MATCHER ====================

    suspend fun runSmartMatcher(maxItems: Int = 80) = withContext(Dispatchers.IO) {
        if (_state.value.matching) return@withContext
        val unresolvedList = _state.value.model.unresolved.values
            .sortedByDescending { it.count }
            .take(maxItems)
        if (unresolvedList.isEmpty()) return@withContext

        _state.update { it.copy(matching = true, matchProgress = "0/${unresolvedList.size}") }
        val spCfg = _state.value.spotifyConfig
        val hasSpotify = spCfg.id.isNotBlank() && spCfg.secret.isNotBlank()

        // Build local fast lookup index of already-known tracks!
        val localIndex = HashMap<String, String>()
        for (t in _state.value.model.tracks.values) {
            if (t.st != "ok" && t.st != "part") continue
            val aNorm = TextNormalizer.norm(t.artists.firstOrNull()?.second)
            val tClean = TextNormalizer.cleanTrackTitle(t.name)
            if (aNorm.isNotEmpty() && tClean.isNotEmpty()) {
                localIndex.putIfAbsent("$aNorm|$tClean", t.id)
            }
        }

        var matchedCount = 0
        try {
            unresolvedList.forEachIndexed { idx, entry ->
                _state.update { it.copy(matchProgress = "${idx + 1}/${unresolvedList.size}") }
                val aNorm = TextNormalizer.norm(entry.artist)
                val tClean = TextNormalizer.cleanTrackTitle(entry.track)

                // 1. Instant local catalog match (zero network calls!)
                val localHit = localIndex["$aNorm|$tClean"]
                if (localHit != null) {
                    markNameMatched(entry.nameId, localHit)
                    matchedCount++
                    return@forEachIndexed
                }

                // 2. Spotify search (exact + cleaned title fallback)
                var best: MatchedTrackResult? = null
                if (hasSpotify) {
                    val spCands = musicApi.searchSpotifyTracks(spCfg, entry.artist, entry.track, entry.album)
                    best = spCands.firstOrNull { musicApi.isStrongMatch(it, aNorm, tClean) }
                }

                // 3. Deezer fallback (finds tracks Spotify misses or when Spotify creds are missing!)
                if (best == null) {
                    val dzCands = musicApi.searchDeezerTracks(entry.artist, entry.track, entry.album)
                    best = dzCands.firstOrNull { musicApi.isStrongMatch(it, aNorm, tClean) }
                }

                if (best != null) {
                    persistMatchedTrackAndAlbum(best)
                    markNameMatched(entry.nameId, best.trackId)
                    localIndex["$aNorm|$tClean"] = best.trackId
                    matchedCount++
                }
            }
            if (matchedCount > 0) rebuildModel()
        } finally {
            _state.update { it.copy(matching = false, matchProgress = "") }
        }
    }

    private suspend fun markNameMatched(nameId: String, trackId: String) {
        val nr = namesMap[nameId] ?: return
        val now = System.currentTimeMillis()
        val updated = nr.copy(tid = trackId, st = "ok", u = now)
        namesMap[nameId] = updated
        dao.upsertNames(listOf(NameEntity(updated.id, updated.k, updated.artist, updated.track, updated.album, updated.tid, "ok", updated.tries, updated.nt, now)))
        writeAndSync(
            "names",
            updated.id,
            mapOf(
                "id" to updated.id, "k" to updated.k, "artist" to updated.artist,
                "track" to updated.track, "album" to updated.album, "tid" to trackId,
                "st" to "ok", "tries" to updated.tries, "nt" to updated.nt
            )
        )
    }

    suspend fun ensureAlbumLoaded(albumId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = albumsMap[albumId]
        if (existing != null && existing.st == "ok" && existing.tracksJson.length > 4) return@withContext true
        val spCfg = _state.value.spotifyConfig
        val full: FullAlbumResult? = when {
            TextNormalizer.isSpotifyId(albumId) && spCfg.id.isNotBlank() && spCfg.secret.isNotBlank() ->
                musicApi.getSpotifyAlbum(spCfg, albumId)
            albumId.startsWith("dz:") -> musicApi.getDeezerAlbum(albumId)
            else -> null
        }
        if (full != null) {
            persistFullAlbum(full)
            rebuildModel()
            return@withContext true
        }
        false
    }

    suspend fun openOrCreateReleaseAlbum(item: ReleaseItem): String? = withContext(Dispatchers.IO) {
        val targetId = when (item.src) {
            "sp" -> item.sid.ifBlank { item.id.removePrefix("sp:") }
            "dz" -> item.id
            else -> {
                // Resolve Apple Music release to Spotify or Deezer album ID so it opens natively in Solco
                val spCfg = _state.value.spotifyConfig
                val artist = item.artists.firstOrNull().orEmpty()
                if (spCfg.id.isNotBlank() && spCfg.secret.isNotBlank()) {
                    val cands = musicApi.searchSpotifyTracks(spCfg, artist, item.title, item.title)
                    val alId = cands.firstOrNull()?.albumId
                    if (alId != null) {
                        musicApi.getSpotifyAlbum(spCfg, alId)?.let { persistFullAlbum(it); rebuildModel(); return@withContext it.id }
                    }
                }
                val dzCands = musicApi.searchDeezerTracks(artist, item.title, item.title)
                val dzAlId = dzCands.firstOrNull()?.albumId
                if (dzAlId != null) {
                    musicApi.getDeezerAlbum(dzAlId)?.let { persistFullAlbum(it); rebuildModel(); return@withContext it.id }
                }
                return@withContext null
            }
        }
        if (!albumsMap.containsKey(targetId)) {
            val now = System.currentTimeMillis()
            val stub = AlbumEntity(
                id = targetId,
                name = item.title,
                type = item.type,
                total = item.tracks,
                date = item.date,
                img = item.img,
                artistsJson = "[]",
                tracksJson = "[]",
                st = "stub",
                tries = 0,
                u = now
            )
            albumsMap[targetId] = stub.toRecord()
            dao.upsertAlbums(listOf(stub))
            rebuildModel()
        }
        ensureAlbumLoaded(targetId)
        targetId
    }

    private suspend fun persistMatchedTrackAndAlbum(m: MatchedTrackResult) {
        val now = System.currentTimeMillis()
        val artistsJson = encodePairs(m.trackArtists)
        val te = TrackEntity(
            id = m.trackId, name = m.title, album = m.albumId, dur = m.durMs, isrc = m.isrc,
            artistsJson = artistsJson, hintJson = null, st = "ok", disc = m.disc, num = m.trackNum, tries = 0, u = now
        )
        tracksMap[m.trackId] = te.toRecord()
        dao.upsertTracks(listOf(te))
        writeAndSync(
            "tracks", m.trackId,
            mapOf(
                "id" to m.trackId, "name" to m.title, "album" to m.albumId, "dur" to m.durMs,
                "isrc" to m.isrc, "artists" to artistsJson, "hint" to null, "st" to "ok",
                "disc" to m.disc, "num" to m.trackNum, "tries" to 0
            )
        )

        // Ensure artists exist
        for ((aid, aname) in (m.trackArtists + m.albumArtists).distinctBy { it.first }) {
            if (!artistsMap.containsKey(aid)) {
                val ae = ArtistEntity(aid, aname, null, "stub", 0, now)
                artistsMap[aid] = ae.toRecord()
                dao.upsertArtists(listOf(ae))
                writeAndSync("artists", aid, mapOf("id" to aid, "name" to aname, "img" to null, "st" to "stub", "tries" to 0))
            }
        }

        // Load full album if missing so tracklist & album counts work immediately
        val alId = m.albumId
        if (!alId.isNullOrBlank() && albumsMap[alId]?.st != "ok") {
            val spCfg = _state.value.spotifyConfig
            val full = when {
                m.source == "sp" && spCfg.id.isNotBlank() && spCfg.secret.isNotBlank() -> musicApi.getSpotifyAlbum(spCfg, alId)
                m.source == "dz" -> musicApi.getDeezerAlbum(alId)
                else -> null
            }
            if (full != null) {
                persistFullAlbum(full)
            } else {
                val alArtJson = encodePairs(m.albumArtists)
                val alTracksJson = buildJsonArray { add(JsonPrimitive(m.trackId)) }.toString()
                val ae = AlbumEntity(alId, m.albumName ?: m.title, m.albumType, m.albumTotal, m.albumDate, m.albumImg, alArtJson, alTracksJson, "part", 0, now)
                albumsMap[alId] = ae.toRecord()
                dao.upsertAlbums(listOf(ae))
                writeAndSync(
                    "albums", alId,
                    mapOf(
                        "id" to alId, "name" to ae.name, "type" to ae.type, "total" to ae.total,
                        "date" to ae.date, "img" to ae.img, "artists" to alArtJson, "tracks" to alTracksJson,
                        "st" to "part", "tries" to 0
                    )
                )
            }
        }
    }

    private suspend fun persistFullAlbum(full: FullAlbumResult) {
        val now = System.currentTimeMillis()
        val trackEntities = mutableListOf<TrackEntity>()
        for (t in full.tracks) {
            val aj = encodePairs(t.trackArtists)
            val te = TrackEntity(t.trackId, t.title, full.id, t.durMs, t.isrc, aj, null, "ok", t.disc, t.trackNum, 0, now)
            tracksMap[t.trackId] = te.toRecord()
            trackEntities.add(te)
        }
        if (trackEntities.isNotEmpty()) {
            dao.upsertTracks(trackEntities)
            runCatching {
                firebase.batchSetDocuments("tracks", trackEntities.map { te ->
                    te.id to mapOf(
                        "id" to te.id, "name" to te.name, "album" to te.album, "dur" to te.dur,
                        "isrc" to te.isrc, "artists" to te.artistsJson, "hint" to null, "st" to "ok",
                        "disc" to te.disc, "num" to te.num, "tries" to 0
                    )
                })
            }
        }

        val alArtJson = encodePairs(full.artists)
        val alTracksJson = buildJsonArray { full.tracks.forEach { add(JsonPrimitive(it.trackId)) } }.toString()
        val ae = AlbumEntity(full.id, full.name, full.type, full.total, full.date, full.img, alArtJson, alTracksJson, "ok", 0, now)
        albumsMap[full.id] = ae.toRecord()
        dao.upsertAlbums(listOf(ae))
        writeAndSync(
            "albums", full.id,
            mapOf(
                "id" to full.id, "name" to full.name, "type" to full.type, "total" to full.total,
                "date" to full.date, "img" to full.img, "artists" to alArtJson, "tracks" to alTracksJson,
                "st" to "ok", "tries" to 0
            )
        )
    }

    // ==================== FULL-CATALOG RELEASES SCANNER (FIXES ALL MISSING RELEASES) ====================

    /**
     * Scans ALL listened canonical artists (never stops at a fixed limit!),
     * uses Spotify (including appears_on featurings) + Deezer + non-truncating Apple Music fallback,
     * and persists every discovered release in Room so reopening is instant.
     */
    suspend fun scanReleases(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (_state.value.releaseScan.scanning) return@withContext
        releaseMutex.withLock {
            val today = MusicApiClients.todayDate()
            val sinceDate = MusicApiClients.daysAgoDate(7)
            val model = _state.value.model
            val stats = _state.value.statsAll

            // Scan ALL canonical artists ordered by listening count (never truncated!)
            val allArtists = stats.artist.entries
                .sortedByDescending { it.value.plays }
                .mapNotNull { (aid, _) ->
                    val art = model.artists[aid] ?: return@mapNotNull null
                    if (art.name.isBlank() || art.name == "…" || art.name == "?") return@mapNotNull null
                    art
                }

            if (allArtists.isEmpty()) return@withContext

            val spCfg = _state.value.spotifyConfig
            val useSpotify = spCfg.id.isNotBlank() && spCfg.secret.isNotBlank()
            val sourceLabel = if (useSpotify) "Spotify + Deezer" else "Deezer + Apple Music"

            _state.update {
                it.copy(
                    releaseScan = ReleaseScanState(
                        scanning = true,
                        done = 0,
                        total = allArtists.size,
                        phase = "Scansione di tutti i ${allArtists.size} artisti…",
                        source = sourceLabel,
                        error = null
                    )
                )
            }

            // Load persisted artist ID maps
            val spMap = kvMap["spotify.map"]?.vJson?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()
            val dzMap = kvMap["deezer.map"]?.vJson?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()

            // Merge into existing cached releases
            val itemsByDedupKey = LinkedHashMap<String, ReleaseItem>()
            for (rel in _state.value.releases) {
                if (rel.date >= MusicApiClients.daysAgoDate(14)) {
                    val dk = releaseDedupKey(rel)
                    itemsByDedupKey[dk] = rel
                }
            }

            fun mergeRelease(rel: ReleaseItem) {
                val dk = releaseDedupKey(rel)
                val prev = itemsByDedupKey[dk]
                if (prev == null) {
                    itemsByDedupKey[dk] = rel
                } else {
                    val mergedBy = (prev.by + rel.by).distinct()
                    val bestImg = prev.img ?: rel.img
                    val preferNew = prev.src != "sp" && rel.src == "sp"
                    itemsByDedupKey[dk] = (if (preferNew) rel else prev).copy(by = mergedBy, img = bestImg)
                }
            }

            var spMapDirty = false
            var dzMapDirty = false

            try {
                allArtists.forEachIndexed { idx, art ->
                    _state.update {
                        it.copy(
                            releaseScan = it.releaseScan.copy(
                                done = idx + 1,
                                phase = "${art.name} (${idx + 1}/${allArtists.size})"
                            )
                        )
                    }

                    var foundForArtist = false
                    if (useSpotify) {
                        val spId = when {
                            TextNormalizer.isSpotifyId(art.id) -> art.id
                            art.aliases.any { TextNormalizer.isSpotifyId(it) } -> art.aliases.first { TextNormalizer.isSpotifyId(it) }
                            !spMap[art.id].isNullOrBlank() && spMap[art.id] != "0" -> spMap[art.id]
                            else -> {
                                val resolved = runCatching { musicApi.findSpotifyArtistId(spCfg, art.name) }.getOrNull()
                                if (resolved != null) {
                                    spMap[art.id] = resolved
                                    spMapDirty = true
                                }
                                resolved
                            }
                        }
                        if (!spId.isNullOrBlank()) {
                            val rels = runCatching {
                                musicApi.getSpotifyArtistReleases(spCfg, spId, art.name, sinceDate, today)
                            }.getOrDefault(emptyList())
                            rels.forEach(::mergeRelease)
                            foundForArtist = true
                        }
                    }

                    // Always fall back to Deezer if Spotify didn't have the artist or isn't configured!
                    if (!foundForArtist) {
                        val dzNumeric = when {
                            art.id.startsWith("dz:") -> art.id.removePrefix("dz:")
                            art.aliases.any { it.startsWith("dz:") } -> art.aliases.first { it.startsWith("dz:") }.removePrefix("dz:")
                            !dzMap[art.id].isNullOrBlank() -> dzMap[art.id]
                            else -> {
                                val resolved = runCatching { musicApi.findDeezerArtistId(art.name) }.getOrNull()
                                if (resolved != null) {
                                    dzMap[art.id] = resolved
                                    dzMapDirty = true
                                }
                                resolved
                            }
                        }
                        if (!dzNumeric.isNullOrBlank()) {
                            val rels = runCatching {
                                musicApi.getDeezerArtistReleases(dzNumeric, art.name, sinceDate, today)
                            }.getOrDefault(emptyList())
                            rels.forEach(::mergeRelease)
                        }
                    }

                    // Periodically flush progress to Room + UI every 15 artists so UI updates live!
                    if ((idx + 1) % 15 == 0 || idx == allArtists.lastIndex) {
                        val snapshot = itemsByDedupKey.values
                            .sortedWith(compareByDescending<ReleaseItem> { it.date }.thenByDescending { it.tracks })
                        dao.upsertReleases(snapshot.map {
                            ReleaseCacheEntity(it.id, json.encodeToString(it), it.date, it.seen)
                        })
                        _state.update { it.copy(releases = snapshot) }
                    }
                }

                dao.pruneOldReleases(MusicApiClients.daysAgoDate(21))
                if (spMapDirty) putKv("spotify.map", json.encodeToString(spMap))
                if (dzMapDirty) putKv("deezer.map", json.encodeToString(dzMap))

                _state.update {
                    it.copy(
                        releaseScan = it.releaseScan.copy(
                            scanning = false,
                            phase = "Completato (${allArtists.size} artisti controllati)",
                            lastRunMs = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        releaseScan = it.releaseScan.copy(
                            scanning = false,
                            error = e.message ?: "Errore scansione uscite"
                        )
                    )
                }
            }
        }
    }

    private fun releaseDedupKey(rel: ReleaseItem): String {
        val primaryArt = TextNormalizer.norm(rel.artists.firstOrNull() ?: rel.by.firstOrNull())
        val cleanTitle = TextNormalizer.cleanAlbumTitle(rel.title)
        return "$primaryArt|$cleanTitle"
    }

    // ==================== SPOTIFY STREAMING HISTORY IMPORT ====================

    suspend fun importSpotifyFiles(uris: List<Uri>): ImportSummary = withContext(Dispatchers.IO) {
        val jsonTexts = mutableListOf<Pair<String, String>>()
        for (uri in uris) {
            val name = uri.lastPathSegment.orEmpty()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (name.endsWith(".zip", ignoreCase = true)) {
                    ZipInputStream(stream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val en = entry.name.substringAfterLast('/')
                            if (!entry.isDirectory && en.endsWith(".json", ignoreCase = true) &&
                                Regex("(Streaming_History_Audio|StreamingHistory_music|StreamingHistory\\d)", RegexOption.IGNORE_CASE).containsMatchIn(en)
                            ) {
                                jsonTexts.add(en to zis.bufferedReader().readText())
                            }
                            entry = zis.nextEntry
                        }
                    }
                } else {
                    jsonTexts.add(name to stream.bufferedReader().readText())
                }
            }
        }

        var total = 0
        var shortCnt = 0
        var podcastCnt = 0
        var kept = 0
        val newNames = mutableListOf<NameEntity>()
        val byMonth = LinkedHashMap<String, MutableList<Triple<Long, Long, String>>>()

        for ((_, text) in jsonTexts) {
            val arr = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: continue
            for (el in arr) {
                val o = el as? JsonObject ?: continue
                total++
                // Extended Streaming History format
                if (o.containsKey("ts") && o.containsKey("ms_played")) {
                    if (o["episode_name"]?.jsonPrimitive?.contentOrNull != null && o["master_metadata_track_name"]?.jsonPrimitive?.contentOrNull == null) {
                        podcastCnt++; continue
                    }
                    val ms = o["ms_played"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (ms < ModelEngine.MIN_PLAY_MS) { shortCnt++; continue }
                    val tsIso = o["ts"]?.jsonPrimitive?.contentOrNull ?: continue
                    val tsSec = runCatching { Instant.parse(tsIso).epochSecond }.getOrNull() ?: continue
                    val uriStr = o["spotify_track_uri"]?.jsonPrimitive?.contentOrNull
                    val tid = TextNormalizer.extractSpotifyId(uriStr, "track")
                    val ref = if (tid != null) tid else {
                        val tr = o["master_metadata_track_name"]?.jsonPrimitive?.contentOrNull ?: continue
                        val ar = o["master_metadata_album_artist_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val al = o["master_metadata_album_album_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val nk = TextNormalizer.nameKey(ar, tr, al)
                        val nid = TextNormalizer.nameIdHash(nk)
                        if (!namesMap.containsKey(nid)) {
                            val ne = NameEntity(nid, nk, ar, tr, al, null, "new", 0, 0L, System.currentTimeMillis())
                            namesMap[nid] = ne.toRecord()
                            newNames.add(ne)
                        }
                        "#$nid"
                    }
                    val ym = Instant.ofEpochSecond(tsSec).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    byMonth.getOrPut(ym) { mutableListOf() }.add(Triple(tsSec, ms, ref))
                    kept++
                } else if (o.containsKey("endTime") && o.containsKey("msPlayed")) {
                    // Account data StreamingHistory format
                    val ms = o["msPlayed"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (ms < ModelEngine.MIN_PLAY_MS) { shortCnt++; continue }
                    val endTime = o["endTime"]?.jsonPrimitive?.contentOrNull ?: continue
                    val tsSec = runCatching {
                        Instant.parse(endTime.replace(" ", "T") + ":00Z").epochSecond
                    }.getOrNull() ?: continue
                    val ar = o["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val tr = o["trackName"]?.jsonPrimitive?.contentOrNull ?: continue
                    val nk = TextNormalizer.nameKey(ar, tr, "")
                    val nid = TextNormalizer.nameIdHash(nk)
                    if (!namesMap.containsKey(nid)) {
                        val ne = NameEntity(nid, nk, ar, tr, "", null, "new", 0, 0L, System.currentTimeMillis())
                        namesMap[nid] = ne.toRecord()
                        newNames.add(ne)
                    }
                    val ym = Instant.ofEpochSecond(tsSec).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    byMonth.getOrPut(ym) { mutableListOf() }.add(Triple(tsSec, ms, "#$nid"))
                    kept++
                }
            }
        }

        if (newNames.isNotEmpty()) dao.upsertNames(newNames)

        for ((ym, entries) in byMonth) {
            val chunkId = "sp:$ym"
            val existingArr = chunksMap[chunkId]?.dataJson?.let {
                runCatching { json.parseToJsonElement(it).jsonArray }.getOrNull()
            } ?: JsonArray(emptyList())
            val seen = HashSet<String>()
            val merged = mutableListOf<Triple<Long, Long, String>>()
            for (el in existingArr) {
                val r = el.jsonArray
                val ts = r.getOrNull(0)?.jsonPrimitive?.longOrNull ?: continue
                val ms = r.getOrNull(1)?.jsonPrimitive?.longOrNull ?: 0L
                val ref = r.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: continue
                if (seen.add("$ts:$ref")) merged.add(Triple(ts, ms, ref))
            }
            for (e in entries) {
                if (seen.add("${e.first}:${e.third}")) merged.add(e)
            }
            merged.sortBy { it.first }
            val dataStr = buildJsonArray {
                for ((ts, ms, ref) in merged) {
                    add(buildJsonArray { add(JsonPrimitive(ts)); add(JsonPrimitive(ms)); add(JsonPrimitive(ref)) })
                }
            }.toString()
            val ce = ChunkEntity(chunkId, "sp", dataStr, merged.size, System.currentTimeMillis())
            chunksMap[chunkId] = ce.toRecord()
            dao.upsertChunks(listOf(ce))
            writeAndSync("chunks", chunkId, mapOf("id" to chunkId, "src" to "sp", "data" to dataStr, "n" to merged.size))
        }

        rebuildModel()
        ImportSummary(jsonTexts.size, total, kept, shortCnt, podcastCnt)
    }

    // ==================== MANUAL SEARCH FOR PENDING TRACKS ====================

    suspend fun searchTrackCandidates(artist: String, track: String, album: String = ""): List<MatchedTrackResult> {
        val spCfg = _state.value.spotifyConfig
        val out = LinkedHashMap<String, MatchedTrackResult>()
        if (spCfg.id.isNotBlank() && spCfg.secret.isNotBlank()) {
            musicApi.searchSpotifyTracks(spCfg, artist, track, album).forEach { out.putIfAbsent(it.trackId, it) }
        }
        musicApi.searchDeezerTracks(artist, track, album).forEach { out.putIfAbsent(it.trackId, it) }
        val aNorm = TextNormalizer.norm(artist)
        val tClean = TextNormalizer.cleanTrackTitle(track)
        val alClean = TextNormalizer.cleanAlbumTitle(album)
        return out.values.sortedByDescending { musicApi.matchScore(it, aNorm, tClean, alClean) }
    }

    // ==================== JSON SERIALIZATION HELPERS ====================

    private fun encodePairs(pairs: List<Pair<String, String>>): String = buildJsonArray {
        for ((id, name) in pairs) {
            add(buildJsonArray { add(JsonPrimitive(id)); add(JsonPrimitive(name)) })
        }
    }.toString()

    private fun anyToJsonString(v: Any?): String = when (v) {
        null -> "null"
        is String -> v
        is Boolean -> v.toString()
        is Number -> v.toString()
        is List<*> -> buildJsonArray { v.forEach { add(anyToJsonElement(it)) } }.toString()
        is Map<*, *> -> buildJsonObject {
            v.forEach { (k, mv) -> if (k != null) put(k.toString(), anyToJsonElement(mv)) }
        }.toString()
        else -> v.toString()
    }

    private fun anyToJsonElement(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is List<*> -> buildJsonArray { v.forEach { add(anyToJsonElement(it)) } }
        is Map<*, *> -> buildJsonObject {
            v.forEach { (k, mv) -> if (k != null) put(k.toString(), anyToJsonElement(mv)) }
        }
        else -> JsonPrimitive(v.toString())
    }

    private fun anyMapToJsonString(map: Map<String, Any?>): String = buildJsonObject {
        for ((k, v) in map) put(k, anyToJsonElement(v))
    }.toString()

    private fun jsonStringToAnyMap(raw: String): Map<String, Any?> {
        val obj = json.parseToJsonElement(raw).jsonObject
        return obj.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    private fun jsonElementToAny(el: kotlinx.serialization.json.JsonElement): Any? = when (el) {
        is kotlinx.serialization.json.JsonNull -> null
        is JsonPrimitive -> when {
            el.isString -> el.content
            el.content == "true" -> true
            el.content == "false" -> false
            el.longOrNull != null -> el.longOrNull
            else -> el.content.toDoubleOrNull() ?: el.content
        }
        is JsonArray -> el.map { jsonElementToAny(it) }
        is JsonObject -> el.mapValues { (_, v) -> jsonElementToAny(v) }
    }
}
