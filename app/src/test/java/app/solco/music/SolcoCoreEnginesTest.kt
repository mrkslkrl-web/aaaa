package app.solco.music

import app.solco.music.data.model.AlbumRecord
import app.solco.music.data.model.ArtistRecord
import app.solco.music.data.model.ChunkRecord
import app.solco.music.data.model.GoalRule
import app.solco.music.data.model.GoalsConfig
import app.solco.music.data.model.Mood
import app.solco.music.data.model.NameRecord
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.model.TrackRecord
import app.solco.music.data.model.TrackReviewEntry
import app.solco.music.data.model.UwuTournament
import app.solco.music.domain.ModelEngine
import app.solco.music.domain.RatingEngine
import app.solco.music.domain.RawStoreSnapshot
import app.solco.music.domain.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SolcoCoreEnginesTest {

    @Test
    fun bayesianAlbumRating_shortAllGoodAlbumIsSlightlyLowerThanLongAllGoodAlbum() {
        val shortAlbum = (1..4).associate { "t$it" to TrackReviewEntry(mood = Mood.GOOD.value) }
        val mediumAlbum = (1..10).associate { "t$it" to TrackReviewEntry(mood = Mood.GOOD.value) }
        val longAlbum = (1..18).associate { "t$it" to TrackReviewEntry(mood = Mood.GOOD.value) }

        val shortScore = RatingEngine.computeFromTracks(shortAlbum)!!.score
        val mediumScore = RatingEngine.computeFromTracks(mediumAlbum)!!.score
        val longScore = RatingEngine.computeFromTracks(longAlbum)!!.score

        assertTrue("Short ($shortScore) should be < medium ($mediumScore)", shortScore < mediumScore)
        assertTrue("Medium ($mediumScore) should be < long ($longScore)", mediumScore < longScore)
        assertTrue("All Bello scores should stay in 7.0..8.3 range", shortScore in 7.0..8.0 && longScore in 7.9..8.3)
    }

    @Test
    fun reviewSorting_keepsHighToLowOrderEvenWhenCalculatedScoreIsHidden() {
        val highTracks = RatingEngine.encodeTrackReviews(
            (1..10).associate { "h$it" to TrackReviewEntry(mood = Mood.AMAZING.value) }
        )
        val midTracks = RatingEngine.encodeTrackReviews(
            (1..10).associate { "m$it" to TrackReviewEntry(mood = Mood.GOOD.value) }
        )
        val lowTracks = RatingEngine.encodeTrackReviews(
            (1..10).associate { "l$it" to TrackReviewEntry(mood = Mood.DISLIKE.value) }
        )

        val rLow = ReviewRecord("album:low", "album", "low", rating = null, mood = null, day = "2026-01-03", tracksJson = lowTracks)
        val rHigh = ReviewRecord("album:high", "album", "high", rating = null, mood = null, day = "2026-01-01", tracksJson = highTracks)
        val rMid = ReviewRecord("album:mid", "album", "mid", rating = null, mood = null, day = "2026-01-02", tracksJson = midTracks)

        // When hidden, displayScore returns (null, null) for track-only reviews...
        val (hiddenScore, hiddenMood) = RatingEngine.displayScore(rHigh, showCalculatedScore = false)
        assertNull(hiddenScore)
        assertNull(hiddenMood)

        // ...yet sorting STILL ranks high -> mid -> low!
        val sorted = listOf(rLow, rHigh, rMid).sortedByDescending { RatingEngine.sortScore(it) }
        assertEquals(listOf("album:high", "album:mid", "album:low"), sorted.map { it.id })
    }

    @Test
    fun artistDeduplication_unifiesSpotifyDeezerAndSyntheticAndSumsAllPlays() {
        val spArtistId = "0OdUWJ0sBjDrqHygGUXeCF"
        val dzArtistId = "dz:12345"
        val synArtistId = "n:band of horses"

        val raw = RawStoreSnapshot(
            artists = mapOf(
                spArtistId to ArtistRecord(spArtistId, "Band of Horses", "https://img/sp.jpg", "ok"),
                dzArtistId to ArtistRecord(dzArtistId, "Band Of Horses", null, "ok"),
                synArtistId to ArtistRecord(synArtistId, "band of horses", null, "stub")
            ),
            tracks = mapOf(
                "spTrack1" to TrackRecord("spTrack1", "The Funeral", "al1", 320_000L, null, """[["$spArtistId","Band of Horses"]]""", null, "ok"),
                "dz:999" to TrackRecord("dz:999", "Is There a Ghost", "dz:al2", 180_000L, null, """[["$dzArtistId","Band Of Horses"]]""", null, "ok"),
                "synTrack" to TrackRecord("synTrack", "No One's Gonna Love You", null, 210_000L, null, """[[null,"Band of Horses"]]""", null, "part")
            ),
            names = mapOf(
                "n1" to NameRecord("n1", "k1", "Band of Horses", "Is There a Ghost", "", "dz:999", "ok"),
                "n2" to NameRecord("n2", "k2", "Band of Horses", "No One's Gonna Love You", "", "synTrack", "ok")
            ),
            chunks = mapOf(
                "sp:2026-01" to ChunkRecord("sp:2026-01", "sp", """[[1700000100,320000,"spTrack1"],[1700000500,320000,"spTrack1"]]""", 2),
                "lfm:2026-01" to ChunkRecord("lfm:2026-01", "lfm", """[[1700001000,"n1"],[1700002000,"n2"]]""", 2)
            )
        )

        val model = ModelEngine.buildModel(raw)
        val stats = ModelEngine.computeStats(model, "all")

        // All 3 duplicate artist IDs must map to the single Spotify canonical ID
        assertEquals(spArtistId, model.artistCanon[spArtistId])
        assertEquals(spArtistId, model.artistCanon[dzArtistId])
        assertEquals(spArtistId, model.artistCanon[synArtistId])
        assertEquals(1, stats.artist.size)

        // All 4 plays (2 Spotify + 1 Deezer + 1 Synthetic) must be summed on the canonical artist!
        val artStat = stats.artist[spArtistId]
        assertNotNull(artStat)
        assertEquals(4, artStat!!.plays)
        assertEquals(3, artStat.songs)
    }

    @Test
    fun albumEditionDeduplication_collapsesStandardAndDeluxeIntoOneCanonicalAlbum() {
        val artId = "1234567890123456789012"
        val stdId = "aaaaaaaaaaaaaaaaaaaaaa"
        val dlxId = "bbbbbbbbbbbbbbbbbbbbbb"

        val raw = RawStoreSnapshot(
            artists = mapOf(artId to ArtistRecord(artId, "Test Artist", null, "ok")),
            tracks = mapOf(
                "t1" to TrackRecord("t1", "Song One", stdId, 200_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 1),
                "t2" to TrackRecord("t2", "Song Two", stdId, 210_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 2),
                "t3" to TrackRecord("t3", "Song Three", stdId, 220_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 3),
                "t4" to TrackRecord("t4", "Song Four", stdId, 230_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 4),
                "d1" to TrackRecord("d1", "Song One (Remastered)", dlxId, 200_500L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 1),
                "d2" to TrackRecord("d2", "Song Two", dlxId, 210_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 2),
                "d3" to TrackRecord("d3", "Song Three", dlxId, 220_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 3),
                "d4" to TrackRecord("d4", "Song Four", dlxId, 230_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 4),
                "d5" to TrackRecord("d5", "Bonus Track", dlxId, 190_000L, null, """[["$artId","Test Artist"]]""", null, "ok", 1, 5)
            ),
            albums = mapOf(
                stdId to AlbumRecord(stdId, "Midnight Sun", "album", 4, "2024-05-01", "https://img/std.jpg", """[["$artId","Test Artist"]]""", """["t1","t2","t3","t4"]""", "ok"),
                dlxId to AlbumRecord(dlxId, "Midnight Sun (Deluxe Edition)", "album", 5, "2024-11-01", "https://img/dlx.jpg", """[["$artId","Test Artist"]]""", """["d1","d2","d3","d4","d5"]""", "ok")
            )
        )

        val model = ModelEngine.buildModel(raw)
        val canonStd = model.albumCanon[stdId]
        val canonDlx = model.albumCanon[dlxId]
        assertEquals(canonStd, canonDlx)
        val canonicalAlbums = model.albums.values.filter { it.aliasOf == null }
        assertEquals(1, canonicalAlbums.size)
        assertEquals(2, canonicalAlbums.first().editions.size)
    }

    @Test
    fun textNormalizer_stripsFeaturingAndEditionsAndHashesDeterministically() {
        assertEquals("blinding lights", TextNormalizer.cleanTrackTitle("Blinding Lights (feat. Rosalía) - 2020 Remastered"))
        assertEquals("midnight sun", TextNormalizer.cleanAlbumTitle("Midnight Sun (Super Deluxe Edition)"))
        val h1 = TextNormalizer.nameIdHash(TextNormalizer.nameKey("Daft Punk", "One More Time", "Discovery"))
        val h2 = TextNormalizer.nameIdHash(TextNormalizer.nameKey("Daft Punk", "One More Time", "Discovery"))
        assertTrue(h1.startsWith("n"))
        assertEquals(h1, h2)
    }

    @Test
    fun uwuFufuBracket_advancesToChampion() {
        val seed = ModelEngine.buildBracketSeed(listOf("A", "B", "C", "D"), shuffle = false)
        var t = UwuTournament("id1", "Test", "artist", seed = seed, picks = emptyList())
        val s1 = ModelEngine.evaluateBracket(t)
        assertFalse(s1.done)
        assertEquals("A", s1.a)
        assertEquals("B", s1.b)

        t = t.copy(picks = listOf(0, 1, 0)) // A beats B, D beats C, A beats D
        val finalState = ModelEngine.evaluateBracket(t)
        assertTrue(finalState.done)
        assertEquals("A", finalState.champion)
    }

    @Test
    fun goalsProgress_computesBalanceAccurately() {
        val today = LocalDate.of(2026, 5, 10)
        val goals = GoalsConfig(album = GoalRule(start = "2026-05-05", per = 1))
        val reviews = listOf(
            ReviewRecord("album:1", "album", "1", day = "2026-05-05", created = 1000L),
            ReviewRecord("album:2", "album", "2", day = "2026-05-06", created = 2000L),
            ReviewRecord("album:3", "album", "3", day = "2026-05-10", created = 3000L)
        )
        val prog = ModelEngine.computeGoalProgress(reviews, "album", goals, today = today)
        // Elapsed full days before May 10 = 5 (May 5..9), 3 reviews done -> balance = 3 - 5 = -2, todayCount = 1
        assertEquals(5, prog.elapsed)
        assertEquals(3, prog.count)
        assertEquals(1, prog.todayCount)
        assertEquals(-2, prog.balance)
    }

    @Test
    fun stubTrackFallback_usesNameRecordAndNeverCreatesQuestionMarkArtist() {
        val raw = RawStoreSnapshot(
            tracks = mapOf(
                "stub1" to TrackRecord("stub1", "?", null, 0L, null, "[]", null, "stub"),
                "orphan1" to TrackRecord("orphan1", "?", null, 0L, null, """[[null,"?"]]""", null, "stub")
            ),
            names = mapOf(
                "n1" to NameRecord("n1", "k1", "Måneskin", "Zitti e buoni", "Teatro d'ira", "stub1", "ok")
            ),
            chunks = mapOf(
                "sp:2026-01" to ChunkRecord("sp:2026-01", "sp", """[[1700002000,180000,"orphan1"]]""", 1),
                "lfm:2026-01" to ChunkRecord("lfm:2026-01", "lfm", """[[1700003000,"n1"]]""", 1)
            )
        )
        val model = ModelEngine.buildModel(raw)
        val stats = ModelEngine.computeStats(model, "all")

        assertEquals("Zitti e buoni", model.tracks["stub1"]?.name)
        assertTrue(stats.artist.keys.none { model.artists[it]?.name == "?" || it == "n:" })
        assertEquals(1, stats.artist.size)
        assertEquals("Måneskin", model.artists[stats.artist.keys.first()]?.name)
    }
}
