package app.solco.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solco.music.data.model.Mood
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.repository.SolcoUiSnapshot
import app.solco.music.domain.ModelEngine
import app.solco.music.domain.RatingEngine
import app.solco.music.domain.TextNormalizer
import app.solco.music.ui.SolcoViewModel
import app.solco.music.ui.components.AlbumTrackForReview
import app.solco.music.ui.components.CoverArt
import app.solco.music.ui.components.EmptyPlaceholder
import app.solco.music.ui.components.GlassCard
import app.solco.music.ui.components.MonthlyBarChart
import app.solco.music.ui.components.PeriodChipRow
import app.solco.music.ui.components.ReviewEditorSheet
import app.solco.music.ui.components.ScoreBadge
import app.solco.music.ui.components.formatDateIt
import app.solco.music.ui.components.formatDurationShort
import app.solco.music.ui.components.formatInt
import app.solco.music.ui.components.formatTrackMs
import app.solco.music.ui.theme.SolcoColors

private data class LibraryRowItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val img: String?,
    val round: Boolean,
    val plays: Int,
    val secondaryStat: String,
    val lastTs: Long,
    val sortScore: Double,
    val displayScore: Double?,
    val displayMood: Mood?
)

@Composable
fun LibraryScreen(
    kind: String, // "artist", "album", "track"
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onNavigate: (String) -> Unit
) {
    var period by remember(kind) { mutableStateOf("all") }
    var sortBy by remember(kind) { mutableStateOf("plays") }
    var query by remember(kind) { mutableStateOf("") }

    val stats = remember(state.model, period) { vm.statsForPeriod(period) }
    val showCalc = state.showCalculatedAlbumScore

    val rows = remember(state.model, stats, state.reviews, kind, sortBy, query, showCalc) {
        val qNorm = TextNormalizer.norm(query)
        val model = state.model

        fun findReview(et: String, eid: String): ReviewRecord? =
            state.reviews["$et:$eid"]?.takeIf { it.del == 0 }

        val list: List<LibraryRowItem> = when (kind) {
            "artist" -> stats.artist.entries.mapNotNull { (aid, st) ->
                val art = model.artists[aid] ?: return@mapNotNull null
                if (qNorm.isNotEmpty() && !TextNormalizer.norm(art.name).contains(qNorm)) return@mapNotNull null
                val rev = findReview("artist", aid)
                val (ds, dm) = rev?.let { RatingEngine.displayScore(it, showCalc) } ?: (null to null)
                LibraryRowItem(
                    id = aid,
                    title = art.name,
                    subtitle = "${formatInt(st.songs)} brani · ${formatDurationShort(st.ms)}",
                    img = art.img,
                    round = true,
                    plays = st.plays,
                    secondaryStat = "${formatInt(st.plays)} ascolti",
                    lastTs = st.lastTs,
                    sortScore = rev?.let { RatingEngine.sortScore(it) } ?: -1.0,
                    displayScore = ds,
                    displayMood = dm
                )
            }
            "album" -> stats.album.entries.mapNotNull { (alId, st) ->
                val al = model.albums[alId] ?: return@mapNotNull null
                if (al.aliasOf != null || al.total < ModelEngine.ALBUM_MIN_TRACKS) return@mapNotNull null
                val artistLabel = al.artists.joinToString(", ") { it.second }.ifBlank { "Artista sconosciuto" }
                if (qNorm.isNotEmpty() &&
                    !TextNormalizer.norm(al.name).contains(qNorm) &&
                    !TextNormalizer.norm(artistLabel).contains(qNorm)
                ) return@mapNotNull null
                val rev = findReview("album", alId)
                val (ds, dm) = rev?.let { RatingEngine.displayScore(it, showCalc) } ?: (null to null)
                LibraryRowItem(
                    id = alId,
                    title = al.name,
                    subtitle = artistLabel,
                    img = al.img,
                    round = false,
                    plays = st.totalPlays,
                    secondaryStat = "${formatInt(st.fullPlays)}x completo · ${formatInt(st.totalPlays)}",
                    lastTs = st.lastTs,
                    sortScore = rev?.let { RatingEngine.sortScore(it) } ?: -1.0,
                    displayScore = ds,
                    displayMood = dm
                )
            }
            else -> stats.song.entries.mapNotNull { (sid, st) ->
                val s = model.songs[sid] ?: return@mapNotNull null
                val artistLabel = s.artists.joinToString(", ") { it.second }
                if (qNorm.isNotEmpty() &&
                    !TextNormalizer.norm(s.title).contains(qNorm) &&
                    !TextNormalizer.norm(artistLabel).contains(qNorm)
                ) return@mapNotNull null
                val rev = findReview("track", sid)
                val (ds, dm) = rev?.let { RatingEngine.displayScore(it, showCalc) } ?: (null to null)
                LibraryRowItem(
                    id = sid,
                    title = s.title,
                    subtitle = artistLabel,
                    img = s.img,
                    round = false,
                    plays = st.plays,
                    secondaryStat = "${formatInt(st.plays)} ascolti",
                    lastTs = st.lastTs,
                    sortScore = rev?.let { RatingEngine.sortScore(it) } ?: -1.0,
                    displayScore = ds,
                    displayMood = dm
                )
            }
        }

        when (sortBy) {
            "rating" -> list.sortedWith(compareByDescending<LibraryRowItem> { it.sortScore }.thenByDescending { it.plays })
            "recent" -> list.sortedByDescending { it.lastTs }
            "alpha" -> list.sortedBy { it.title.lowercase() }
            else -> list.sortedByDescending { it.plays }
        }
    }

    val title = when (kind) {
        "artist" -> "Artisti"
        "album" -> "Album"
        else -> "Singoli & Brani"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text("${formatInt(rows.size)} elementi", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Search input
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cerca $title…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SolcoColors.AccentViolet) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Pulisci", tint = SolcoColors.TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SolcoColors.AccentViolet,
                unfocusedBorderColor = SolcoColors.Border,
                focusedContainerColor = SolcoColors.CardBg,
                unfocusedContainerColor = SolcoColors.CardBg
            )
        )

        Spacer(Modifier.height(8.dp))
        PeriodChipRow(selectedPeriod = period, years = state.model.years, onSelect = { period = it })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("plays" to "Più ascoltati", "rating" to "Voto", "recent" to "Recenti", "alpha" to "A–Z").forEach { (k, lbl) ->
                val sel = sortBy == k
                FilterChip(
                    selected = sel,
                    onClick = { sortBy = k },
                    label = { Text(lbl, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SolcoColors.AccentPink.copy(alpha = 0.22f),
                        selectedLabelColor = SolcoColors.TextPrimary
                    )
                )
            }
        }

        if (rows.isEmpty()) {
            EmptyPlaceholder(
                icon = when (kind) {
                    "artist" -> Icons.Rounded.Person
                    "album" -> Icons.Rounded.Album
                    else -> Icons.Rounded.MusicNote
                },
                title = "Nessun risultato",
                subtitle = "Prova a cambiare periodo, ricerca o sincronizza gli ascolti."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(rows, key = { _, item -> item.id }) { idx, item ->
                    GlassCard(
                        onClick = { onNavigate("$kind/${item.id}") },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${idx + 1}",
                                color = if (idx < 3) SolcoColors.AccentGold else SolcoColors.TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(26.dp)
                            )
                            CoverArt(url = item.img, name = item.title, size = 50.dp, round = item.round)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.secondaryStat, style = MaterialTheme.typography.labelSmall, color = SolcoColors.AccentCyan)
                            }
                            if (item.displayScore != null || item.displayMood != null) {
                                ScoreBadge(score = item.displayScore, mood = item.displayMood)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: String,
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val canonId = state.model.artistCanon[artistId] ?: artistId
    val artist = state.model.artists[canonId]
    val stat = state.statsAll.artist[canonId]
    val review = state.reviews["artist:$canonId"]?.takeIf { it.del == 0 }
    val note = state.notes["artist:$canonId"]?.takeIf { it.del == 0 }?.text.orEmpty()
    var showReviewSheet by remember { mutableStateOf(false) }
    var noteDraft by remember(note) { mutableStateOf(note) }
    var editingNote by remember { mutableStateOf(false) }

    val artistAlbums = remember(state.model, state.statsAll, canonId) {
        state.model.albums.values
            .filter { it.aliasOf == null && it.total >= ModelEngine.ALBUM_MIN_TRACKS && it.artists.any { (aid, _) -> aid == canonId } }
            .sortedByDescending { state.statsAll.album[it.id]?.totalPlays ?: 0 }
    }

    val artistSongs = remember(state.model, state.statsAll, canonId) {
        state.model.songs.values
            .filter { s -> s.artists.any { it.first == canonId } }
            .sortedByDescending { state.statsAll.song[it.id]?.plays ?: 0 }
    }

    val monthly = remember(state.model, artistSongs) {
        val tids = artistSongs.flatMap { it.versions }.toSet()
        ModelEngine.monthlyActivity(state.model, tids, 12)
    }

    if (artist == null) {
        EmptyPlaceholder(Icons.Rounded.Person, "Artista non trovato", "Potrebbe essere stato unificato o rimosso.", "Torna indietro", onBack)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro", tint = SolcoColors.TextPrimary)
            }
        }

        // Hero card
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CoverArt(url = artist.img, name = artist.name, size = 120.dp, round = true, glow = true)
                Text(artist.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${formatInt(stat?.plays ?: 0)} ascolti · ${formatDurationShort(stat?.ms ?: 0L)} · ${formatInt(stat?.songs ?: 0)} brani",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SolcoColors.AccentCyan
                )
                if (artist.aliases.isNotEmpty()) {
                    Text(
                        text = "${artist.aliases.size + 1} profili duplicati unificati automaticamente",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolcoColors.AccentMint
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showReviewSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentViolet)
                    ) {
                        Icon(Icons.Rounded.RateReview, contentDescription = null, tint = Color(0xFF07070D), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (review != null) "Modifica recensione" else "Recensisci", color = Color(0xFF07070D), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { editingNote = !editingNote }) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nota")
                    }
                }
            }
        }

        if (review != null) {
            item {
                val (ds, dm) = RatingEngine.displayScore(review, true)
                GlassCard(glowBorder = true, accentColor = SolcoColors.AccentViolet) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("La tua recensione · ${formatDateIt(review.day)}", style = MaterialTheme.typography.labelMedium, color = SolcoColors.AccentViolet)
                        ScoreBadge(score = ds, mood = dm, large = true)
                    }
                    if (review.text.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(review.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (editingNote || note.isNotBlank()) {
            item {
                GlassCard {
                    Text("Nota personale", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (editingNote) {
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.saveNote("artist", canonId, noteDraft); editingNote = false }) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Salva nota")
                        }
                    } else {
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Ascolti ultimi 12 mesi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                MonthlyBarChart(buckets = monthly)
            }
        }

        if (artistAlbums.isNotEmpty()) {
            item { Text("Album (${artistAlbums.size})", style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(artistAlbums, key = { _, a -> a.id }) { _, al ->
                val aSt = state.statsAll.album[al.id]
                val aRev = state.reviews["album:${al.id}"]?.takeIf { it.del == 0 }
                val (ds, dm) = aRev?.let { RatingEngine.displayScore(it, state.showCalculatedAlbumScore) } ?: (null to null)
                GlassCard(onClick = { onNavigate("album/${al.id}") }, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverArt(url = al.img, name = al.name, size = 52.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(al.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${al.date.take(4)} · ${formatInt(aSt?.totalPlays ?: 0)} ascolti",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (ds != null || dm != null) ScoreBadge(score = ds, mood = dm)
                    }
                }
            }
        }

        if (artistSongs.isNotEmpty()) {
            item { Text("Brani più ascoltati (${artistSongs.size})", style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(artistSongs.take(40), key = { _, s -> s.id }) { idx, s ->
                val sSt = state.statsAll.song[s.id]
                GlassCard(onClick = { onNavigate("track/${s.id}") }, contentPadding = PaddingValues(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${idx + 1}", color = SolcoColors.TextMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                        CoverArt(url = s.img, name = s.title, size = 42.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatInt(sSt?.plays ?: 0)} ascolti", style = MaterialTheme.typography.labelSmall, color = SolcoColors.AccentCyan)
                        }
                    }
                }
            }
        }
    }

    if (showReviewSheet) {
        ReviewEditorSheet(
            title = artist.name,
            entityType = "artist",
            existing = review,
            onDismiss = { showReviewSheet = false },
            onDelete = review?.let { r -> { vm.deleteReview(r.id); showReviewSheet = false } },
            onSave = { r, m, t, d, tr ->
                vm.saveReview("artist", canonId, r, m, t, d, tr)
                showReviewSheet = false
            }
        )
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: String,
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val canonId = state.model.albumCanon[albumId] ?: albumId
    val album = state.model.albums[canonId]
    val stat = state.statsAll.album[canonId]
    val review = state.reviews["album:$canonId"]?.takeIf { it.del == 0 }
    val trackReviewMap = remember(review) { RatingEngine.parseTrackReviews(review?.tracksJson) }
    val calcScore = remember(trackReviewMap) { RatingEngine.computeFromTracks(trackReviewMap) }

    var showReviewSheet by remember { mutableStateOf(false) }

    LaunchedEffect(canonId) {
        vm.ensureAlbumLoaded(canonId)
    }

    if (album == null) {
        EmptyPlaceholder(Icons.Rounded.Album, "Album non trovato", "Impossibile trovare questo album.", "Indietro", onBack)
        return
    }

    val tracks = remember(state.model, album) {
        val seen = LinkedHashSet<String>()
        album.tracks.mapNotNull { tid ->
            val tr = state.model.tracks[tid] ?: return@mapNotNull null
            val sid = state.model.songOf[tid] ?: tid
            if (!seen.add(sid)) return@mapNotNull null
            Triple(sid, tr, state.statsAll.song[sid]?.plays ?: 0)
        }
    }

    val discs = remember(tracks) { tracks.map { it.second.disc }.distinct().sorted() }
    val offDiscs = state.model.discOff[canonId].orEmpty().toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro", tint = SolcoColors.TextPrimary)
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverArt(url = album.img, name = album.name, size = 164.dp, glow = true)
                Text(album.name, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    album.artists.forEach { (aid, aname) ->
                        Text(
                            text = aname,
                            color = SolcoColors.AccentCyan,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigate("artist/$aid") }
                        )
                    }
                }
                Text(
                    text = buildString {
                        if (album.date.isNotBlank()) append("${formatDateIt(album.date)} · ")
                        append("${formatInt(stat?.totalPlays ?: 0)} ascolti")
                        if ((stat?.fullPlays ?: 0) > 0) append(" · ${stat?.fullPlays}x completo")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (album.editions.size > 1) {
                    Text(
                        "${album.editions.size} edizioni unificate",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolcoColors.AccentMint
                    )
                }

                // Calculated or manual score display
                val (ds, dm) = review?.let { RatingEngine.displayScore(it, state.showCalculatedAlbumScore) } ?: (null to null)
                if (ds != null || dm != null) {
                    ScoreBadge(score = ds, mood = dm, large = true)
                } else if (calcScore != null && !state.showCalculatedAlbumScore) {
                    Text(
                        "Voto calcolato nascosto (${calcScore.ratedCount}/${tracks.size} brani votati)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolcoColors.TextMuted
                    )
                }

                Button(
                    onClick = { showReviewSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentViolet)
                ) {
                    Icon(Icons.Rounded.RateReview, contentDescription = null, tint = Color(0xFF07070D), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (review != null) "Modifica recensione & voti brani" else "Recensisci album", color = Color(0xFF07070D), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (review != null && review.text.isNotBlank()) {
            item {
                GlassCard(glowBorder = true, accentColor = SolcoColors.AccentViolet) {
                    Text("Recensione · ${formatDateIt(review.day)}", style = MaterialTheme.typography.labelMedium, color = SolcoColors.AccentViolet)
                    Spacer(Modifier.height(6.dp))
                    Text(review.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (discs.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    discs.forEach { d ->
                        val active = d !in offDiscs
                        FilterChip(
                            selected = active,
                            onClick = { vm.toggleDiscOff(canonId, d) },
                            label = { Text("Disco $d") }
                        )
                    }
                }
            }
        }

        item { Text("Tracce (${tracks.size})", style = MaterialTheme.typography.titleLarge) }

        itemsIndexed(tracks, key = { _, t -> t.first }) { idx, (sid, tr, plays) ->
            val tEntry = trackReviewMap[sid]
            val tMood = Mood.fromValue(tEntry?.mood)
            val dimmed = tr.disc in offDiscs
            GlassCard(
                onClick = { onNavigate("track/$sid") },
                glowBorder = tMood != null,
                accentColor = tMood?.let { Color(it.colorHex) } ?: SolcoColors.Border,
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (tr.num > 0) tr.num.toString() else "${idx + 1}",
                        color = if (dimmed) SolcoColors.TextMuted.copy(alpha = 0.4f) else SolcoColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tr.name,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dimmed) SolcoColors.TextMuted else SolcoColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatInt(plays)} ascolti ${if (tr.dur > 0) "· ${formatTrackMs(tr.dur)}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolcoColors.TextSecondary
                        )
                        if (!tEntry?.text.isNullOrBlank()) {
                            Text(tEntry!!.text!!, style = MaterialTheme.typography.bodyMedium, color = SolcoColors.AccentCyan)
                        }
                    }
                    if (tMood != null) {
                        ScoreBadge(score = null, mood = tMood)
                    }
                }
            }
        }
    }

    if (showReviewSheet) {
        ReviewEditorSheet(
            title = album.name,
            entityType = "album",
            existing = review,
            albumTracks = tracks.mapIndexed { i, (sid, tr, _) ->
                AlbumTrackForReview(sid, if (tr.num > 0) tr.num else i + 1, tr.name)
            },
            showCalculatedScore = state.showCalculatedAlbumScore,
            onDismiss = { showReviewSheet = false },
            onDelete = review?.let { r -> { vm.deleteReview(r.id); showReviewSheet = false } },
            onSave = { r, m, t, d, trMap ->
                vm.saveReview("album", canonId, r, m, t, d, trMap)
                showReviewSheet = false
            }
        )
    }
}

@Composable
fun TrackDetailScreen(
    songId: String,
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val canonSongId = state.model.songOf[songId] ?: songId
    val song = state.model.songs[canonSongId]
    val stat = state.statsAll.song[canonSongId]
    val review = state.reviews["track:$canonSongId"]?.takeIf { it.del == 0 }
    var showReviewSheet by remember { mutableStateOf(false) }

    if (song == null) {
        EmptyPlaceholder(Icons.Rounded.MusicNote, "Brano non trovato", "Questo brano non è più presente.", "Indietro", onBack)
        return
    }

    val monthly = remember(state.model, song) {
        ModelEngine.monthlyActivity(state.model, song.versions.toSet(), 12)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro", tint = SolcoColors.TextPrimary)
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverArt(url = song.img, name = song.title, size = 148.dp, glow = true)
                Text(song.title, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    song.artists.forEach { (aid, aname) ->
                        Text(
                            text = aname,
                            color = SolcoColors.AccentCyan,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigate("artist/$aid") }
                        )
                    }
                }
                Text(
                    "${formatInt(stat?.plays ?: 0)} ascolti · ${formatDurationShort(stat?.ms ?: 0L)} ${if (song.dur > 0) "· ${formatTrackMs(song.dur)}" else ""}",
                    style = MaterialTheme.typography.bodyMedium
                )
                review?.let {
                    val (ds, dm) = RatingEngine.displayScore(it, true)
                    ScoreBadge(score = ds, mood = dm, large = true)
                }
                Button(
                    onClick = { showReviewSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentPink)
                ) {
                    Icon(Icons.Rounded.RateReview, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (review != null) "Modifica recensione" else "Recensisci brano", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (review != null && review.text.isNotBlank()) {
            item {
                GlassCard(glowBorder = true, accentColor = SolcoColors.AccentPink) {
                    Text("Recensione · ${formatDateIt(review.day)}", style = MaterialTheme.typography.labelMedium, color = SolcoColors.AccentPink)
                    Spacer(Modifier.height(6.dp))
                    Text(review.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            GlassCard {
                Text("Ascolti ultimi 12 mesi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                MonthlyBarChart(buckets = monthly)
            }
        }

        if (song.albums.isNotEmpty()) {
            item { Text("Presente in", style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(song.albums, key = { _, aid -> aid }) { _, aid ->
                val al = state.model.albums[aid] ?: return@itemsIndexed
                GlassCard(onClick = { onNavigate("album/$aid") }, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverArt(url = al.img, name = al.name, size = 48.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(al.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(al.date.take(4), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showReviewSheet) {
        ReviewEditorSheet(
            title = song.title,
            entityType = "track",
            existing = review,
            onDismiss = { showReviewSheet = false },
            onDelete = review?.let { r -> { vm.deleteReview(r.id); showReviewSheet = false } },
            onSave = { r, m, t, d, tr ->
                vm.saveReview("track", canonSongId, r, m, t, d, tr)
                showReviewSheet = false
            }
        )
    }
}
