package app.solco.music.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import app.solco.music.data.model.AlbumRecord
import app.solco.music.data.model.ArtistRecord
import app.solco.music.data.model.ChunkRecord
import app.solco.music.data.model.KvRecord
import app.solco.music.data.model.NameRecord
import app.solco.music.data.model.NoteRecord
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.model.TrackRecord

@Entity(tableName = "kv")
data class KvEntity(
    @PrimaryKey val k: String,
    val vJson: String,
    val u: Long
) {
    fun toRecord() = KvRecord(k, vJson, u)
}

@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey val id: String,
    val src: String,
    val dataJson: String,
    val n: Int,
    val u: Long
) {
    fun toRecord() = ChunkRecord(id, src, dataJson, n, u)
}

@Entity(tableName = "names")
data class NameEntity(
    @PrimaryKey val id: String,
    val k: String,
    val artist: String,
    val track: String,
    val album: String,
    val tid: String?,
    val st: String,
    val tries: Int,
    val nt: Long,
    val u: Long
) {
    fun toRecord() = NameRecord(id, k, artist, track, album, tid, st, tries, nt, u)
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val album: String?,
    val dur: Long,
    val isrc: String?,
    val artistsJson: String,
    val hintJson: String?,
    val st: String,
    val disc: Int,
    val num: Int,
    val tries: Int,
    val u: Long
) {
    fun toRecord() = TrackRecord(id, name, album, dur, isrc, artistsJson, hintJson, st, disc, num, tries, u)
}

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val total: Int?,
    val date: String,
    val img: String?,
    val artistsJson: String,
    val tracksJson: String,
    val st: String,
    val tries: Int,
    val u: Long
) {
    fun toRecord() = AlbumRecord(id, name, type, total, date, img, artistsJson, tracksJson, st, tries, u)
}

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val img: String?,
    val st: String,
    val tries: Int,
    val u: Long
) {
    fun toRecord() = ArtistRecord(id, name, img, st, tries, u)
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val et: String,
    val eid: String,
    val text: String,
    val del: Int,
    val u: Long
) {
    fun toRecord() = NoteRecord(id, et, eid, text, del, u)
}

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey val id: String,
    val et: String,
    val eid: String,
    val rating: Double?,
    val mood: Int?,
    val text: String,
    val day: String,
    val tracksJson: String?,
    val created: Long,
    val del: Int,
    val u: Long
) {
    fun toRecord() = ReviewRecord(id, et, eid, rating, mood, text, day, tracksJson, created, del, u)
}

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val opKey: String, // "$collection:$docId"
    val collection: String,
    val docId: String,
    val payloadJson: String,
    val createdAt: Long
)

@Entity(tableName = "releases_cache")
data class ReleaseCacheEntity(
    @PrimaryKey val id: String,
    val payloadJson: String,
    val date: String,
    val seen: String
)

@Dao
interface SolcoDao {
    @Query("SELECT * FROM kv")
    suspend fun getAllKv(): List<KvEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKv(items: List<KvEntity>)

    @Query("SELECT * FROM chunks")
    suspend fun getAllChunks(): List<ChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(items: List<ChunkEntity>)

    @Query("SELECT * FROM names")
    suspend fun getAllNames(): List<NameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNames(items: List<NameEntity>)

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracks(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(items: List<TrackEntity>)

    @Query("SELECT * FROM albums")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(items: List<AlbumEntity>)

    @Query("SELECT * FROM artists")
    suspend fun getAllArtists(): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(items: List<ArtistEntity>)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(items: List<NoteEntity>)

    @Query("SELECT * FROM reviews")
    suspend fun getAllReviews(): List<ReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviews(items: List<ReviewEntity>)

    @Query("SELECT * FROM sync_meta")
    suspend fun getAllMeta(): List<SyncMetaEntity>

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(item: SyncMetaEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun getOutbox(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOutbox(item: OutboxEntity)

    @Query("DELETE FROM outbox WHERE opKey = :opKey")
    suspend fun deleteOutbox(opKey: String)

    @Query("SELECT * FROM releases_cache ORDER BY date DESC")
    suspend fun getReleases(): List<ReleaseCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReleases(items: List<ReleaseCacheEntity>)

    @Query("DELETE FROM releases_cache WHERE seen < :cutoffDay")
    suspend fun pruneOldReleases(cutoffDay: String)

    @Query("DELETE FROM kv")
    suspend fun clearKv()
    @Query("DELETE FROM chunks")
    suspend fun clearChunks()
    @Query("DELETE FROM names")
    suspend fun clearNames()
    @Query("DELETE FROM tracks")
    suspend fun clearTracks()
    @Query("DELETE FROM albums")
    suspend fun clearAlbums()
    @Query("DELETE FROM artists")
    suspend fun clearArtists()
    @Query("DELETE FROM notes")
    suspend fun clearNotes()
    @Query("DELETE FROM reviews")
    suspend fun clearReviews()
    @Query("DELETE FROM sync_meta")
    suspend fun clearMeta()
    @Query("DELETE FROM outbox")
    suspend fun clearOutbox()
    @Query("DELETE FROM releases_cache")
    suspend fun clearReleases()
}

@Database(
    entities = [
        KvEntity::class,
        ChunkEntity::class,
        NameEntity::class,
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        NoteEntity::class,
        ReviewEntity::class,
        SyncMetaEntity::class,
        OutboxEntity::class,
        ReleaseCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SolcoDatabase : RoomDatabase() {
    abstract fun dao(): SolcoDao

    suspend fun clearAllUserTables() {
        val d = dao()
        d.clearKv()
        d.clearChunks()
        d.clearNames()
        d.clearTracks()
        d.clearAlbums()
        d.clearArtists()
        d.clearNotes()
        d.clearReviews()
        d.clearMeta()
        d.clearOutbox()
        d.clearReleases()
    }

    companion object {
        @Volatile
        private var INSTANCE: SolcoDatabase? = null

        fun getInstance(context: Context): SolcoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SolcoDatabase::class.java,
                    "solco_native_v2.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
