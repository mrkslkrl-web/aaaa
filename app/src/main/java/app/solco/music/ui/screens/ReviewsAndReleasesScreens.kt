package app.solco.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solco.music.data.model.Mood
import app.solco.music.data.model.ReleaseItem
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.remote.MusicApiClients
import app.solco.music.data.repository.SolcoUiSnapshot
import app.solco.music.domain.ModelEngine
import app.solco.music.domain.RatingEngine
import app.solco.music.ui.SolcoViewModel
import app.solco.music.ui.components.CoverArt
import app.solco.music.ui.components.EmptyPlaceholder
import app.solco.music.ui.components.GlassCard
import app.solco.music.ui.components.ScoreBadge
import app.solco.music.ui.components.formatDateIt
import app.solco.music.ui.components.formatInt
import app.solco.music.ui.theme.SolcoColors

private data class ReviewRowModel(
    val review: ReviewRecord,
    val canonicalId: String,
    val title: String,
    val subtitle: String,
    val img: String?,
    val round: Boolean,
    val plays: Int,
    val judgedRatio: Pair<Int, Int>?,
    val sortScore: Double,
    val displayScore: Double?,
    val displayMood: Mood?
)

@Composable
fun ReviewsScreen(
    initialTab: String = "album",
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab.ifBlank { "album" }) }
    var sortBy by remember { mutableStateOf("rating") }
    val showCalc = state.showCalculatedAlbumScore
    val listState = rememberLazyListState()
    LaunchedEffect(tab, sortBy) {
        listState.scrollToItem(0)
    }

    val activeAll = remember(state.reviews) { state.reviews.values.filter { it.del == 0 } }
    val counts = remember(activeAll) {
        mapOf(
            "album" to activeAll.count { it.et == "album" },
            "track" to activeAll.count { it.et == "track" },
            "artist" to activeAll.count { it.et == "artist" }
        )
    }

    val goal = remember(activeAll, state.kv, tab) {
        if (tab == "artist") null else ModelEngine.computeGoalProgress(activeAll, tab, state.goalsConfig)
    }

    val rows = remember(activeAll, state.model, state.statsAll, tab, sortBy, showCalc) {
        val model = state.model
        val stats = state.statsAll
        val seen = HashSet<String>()
        val built = mutableListOf<ReviewRowModel>()

        for (r in activeAll) {
            if (r.et != tab) continue
            val cid = when (tab) {
                "artist" -> model.artistCanon[r.eid] ?: r.eid
                "album" -> model.albumCanon[r.eid] ?: r.eid
                else -> model.songOf[r.eid] ?: r.eid
            }
            if (!seen.add(cid)) continue

            val (title, sub, img, round, plays) = when (tab) {
                "artist" -> {
                    val a = model.artists[cid] ?: continue
                    Quint(a.name, "", a.img, true, stats.artist[cid]?.plays ?: 0)
                }
                "album" -> {
                    val a = model.albums[cid] ?: continue
                    Quint(a.name, a.artists.joinToString(", ") { it.second }, a.img, false, stats.album[cid]?.totalPlays ?: 0)
                }
                else -> {
                    val s = model.songs[cid] ?: continue
                    Quint(s.title, s.artists.joinToString(", ") { it.second }, s.img, false, stats.song[cid]?.plays ?: 0)
                }
            }

            var judged: Pair<Int, Int>? = null
            if (tab == "album") {
                val al = model.albums[cid]
                val totalTracks = al?.tracks?.map { model.songOf[it] ?: it }?.distinct()?.size ?: 0
                if (totalTracks > 0) {
                    val rated = RatingEngine.parseTrackReviews(r.tracksJson).values.count { it.mood != null }
                    if (rated > 0) judged = rated to totalTracks
                }
            }

            // CRITICAL BUG FIX:
            // sortScore ALWAYS uses RatingEngine.sortScore(r) (independent of showCalc),
            // so hiding the calculated score STILL keeps albums sorted highest-to-lowest!
            val sScore = RatingEngine.sortScore(r)
            val (ds, dm) = RatingEngine.displayScore(r, showCalc)

            built.add(
                ReviewRowModel(
                    review = r,
                    canonicalId = cid,
                    title = title,
                    subtitle = sub,
                    img = img,
                    round = round,
                    plays = plays,
                    judgedRatio = judged,
                    sortScore = sScore,
                    displayScore = ds,
                    displayMood = dm
                )
            )
        }

        when (sortBy) {
            "date" -> built.sortedWith(compareByDescending<ReviewRowModel> { it.review.day }.thenByDescending { it.review.created })
            "plays" -> built.sortedByDescending { it.plays }
            "alpha" -> built.sortedBy { it.title.lowercase() }
            else -> built.sortedWith(compareByDescending<ReviewRowModel> { it.sortScore }.thenByDescending { it.review.day })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
            }
            Column {
                Text("Recensioni", style = MaterialTheme.typography.headlineMedium)
                Text("${formatInt(activeAll.size)} totali", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Category tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("album" to "Album", "track" to "Singoli", "artist" to "Artisti").forEach { (k, lbl) ->
                val sel = tab == k
                FilterChip(
                    selected = sel,
                    onClick = { tab = k },
                    label = { Text("$lbl (${counts[k] ?: 0})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SolcoColors.AccentViolet.copy(alpha = 0.25f),
                        selectedLabelColor = SolcoColors.TextPrimary
                    )
                )
            }
        }

        if (goal != null) {
            val balStr = if (goal.balance > 0) "+${goal.balance}" else goal.balance.toString()
            Text(
                text = "Obiettivo: $balStr · ${goal.count} recensioni in ${goal.elapsed + 1} giorni",
                style = MaterialTheme.typography.labelMedium,
                color = if (goal.balance >= 0) SolcoColors.AccentMint else SolcoColors.AccentCoral,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        // Sort bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("rating" to "Voto (alto → basso)", "date" to "Più recenti", "plays" to "Più ascoltati", "alpha" to "A–Z").forEach { (k, lbl) ->
                val sel = sortBy == k
                FilterChip(
                    selected = sel,
                    onClick = { sortBy = k },
                    label = { Text(lbl, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SolcoColors.AccentCyan.copy(alpha = 0.22f),
                        selectedLabelColor = SolcoColors.TextPrimary
                    )
                )
            }
        }

        // Hide/show calculated score toggle (with clear indicator that sorting stays intact!)
        if (tab == "album") {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (showCalc) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = if (showCalc) SolcoColors.AccentCyan else SolcoColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Mostra voto calcolato dai brani", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Anche nascosto, l'ordine resta dal voto più alto al più basso",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Switch(
                        checked = showCalc,
                        onCheckedChange = { vm.setShowCalculatedAlbumScore(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SolcoColors.AccentViolet)
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            EmptyPlaceholder(
                icon = Icons.Rounded.RateReview,
                title = "Nessuna recensione",
                subtitle = "Apri un album, un singolo o un artista e scrivi cosa ne pensi."
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(rows, key = { it.review.id }) { row ->
                    GlassCard(
                        onClick = { onNavigate("$tab/${row.canonicalId}") },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CoverArt(url = row.img, name = row.title, size = 54.dp, round = row.round)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (row.subtitle.isNotBlank()) {
                                    Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (row.review.text.isNotBlank()) {
                                    Text(
                                        row.review.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SolcoColors.TextPrimary.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val meta = buildString {
                                    append(formatDateIt(row.review.day))
                                    append(" · ${formatInt(row.plays)} ascolti")
                                    row.judgedRatio?.let { (a, b) ->
                                        if (a < b) append(" · $a/$b brani")
                                    }
                                }
                                Text(meta, style = MaterialTheme.typography.labelSmall, color = SolcoColors.TextMuted)
                            }
                            if (row.displayScore != null || row.displayMood != null) {
                                ScoreBadge(score = row.displayScore, mood = row.displayMood)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

@Composable
fun ReleasesScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("today") } // "today", "week", "saved"
    val today = MusicApiClients.todayDate()
    val savedIds = remember(state.kv) { state.savedReleases.map { it.id }.toSet() }

    LaunchedEffect(Unit) {
        if (state.releases.isEmpty() && !state.releaseScan.scanning) {
            vm.scanReleases(force = false)
        }
    }

    val list = remember(state.releases, state.kv, tab, today) {
        when (tab) {
            "today" -> state.releases.filter { it.date == today }
            "week" -> state.releases
            else -> state.savedReleases
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
                }
                Column {
                    Text("Nuove uscite", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Controlla tutti i ${formatInt(state.statsAll.artist.size)} artisti (album, singoli & feat)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(
                onClick = { vm.scanReleases(force = true) },
                enabled = !state.releaseScan.scanning
            ) {
                if (state.releaseScan.scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SolcoColors.AccentCyan, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Scansiona tutti", tint = SolcoColors.AccentCyan)
                }
            }
        }

        // Live full-scan progress card
        if (state.releaseScan.scanning || state.releaseScan.error != null) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                glowBorder = true,
                accentColor = if (state.releaseScan.error != null) SolcoColors.AccentCoral else SolcoColors.AccentCyan,
                contentPadding = PaddingValues(12.dp)
            ) {
                if (state.releaseScan.scanning) {
                    val total = state.releaseScan.total.coerceAtLeast(1)
                    val prog = state.releaseScan.done.toFloat() / total.toFloat()
                    Text(
                        "${state.releaseScan.source}: ${state.releaseScan.phase}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SolcoColors.AccentCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { prog.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = SolcoColors.AccentCyan,
                        trackColor = SolcoColors.Border
                    )
                }
                state.releaseScan.error?.let {
                    Text(it, color = SolcoColors.AccentCoral, fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val todayCount = state.releases.count { it.date == today }
            listOf(
                "today" to "Oggi ($todayCount)",
                "week" to "Ultimi 7 giorni (${state.releases.size})",
                "saved" to "Salvate (${state.savedReleases.size})"
            ).forEach { (k, lbl) ->
                val sel = tab == k
                FilterChip(
                    selected = sel,
                    onClick = { tab = k },
                    label = { Text(lbl, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SolcoColors.AccentCyan.copy(alpha = 0.24f),
                        selectedLabelColor = SolcoColors.TextPrimary
                    )
                )
            }
        }

        if (list.isEmpty()) {
            EmptyPlaceholder(
                icon = Icons.Rounded.AutoAwesome,
                title = if (tab == "today") "Nessuna uscita trovata oggi" else if (tab == "saved") "Nessuna uscita salvata" else "Nessuna uscita recente",
                subtitle = if (state.releaseScan.scanning) "Scansione in corso su tutti i tuoi artisti…"
                else "Premi aggiorna per controllare tutti i tuoi artisti su Spotify e Deezer (inclusi i featuring).",
                actionLabel = if (!state.releaseScan.scanning && tab != "saved") "Scansiona tutti gli artisti" else null,
                onAction = { vm.scanReleases(force = true) }
            )
        } else {
            val relListState = rememberLazyListState()
            LaunchedEffect(tab) { relListState.scrollToItem(0) }
            LazyColumn(
                state = relListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { item ->
                    ReleaseRowCard(
                        item = item,
                        saved = item.id in savedIds,
                        onOpenInApp = {
                            vm.openReleaseAlbum(item) { albumId ->
                                onNavigate("album/$albumId")
                            }
                        },
                        onToggleSave = { vm.toggleSavedRelease(item) },
                        onOpenExternal = {
                            item.link?.let { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseRowCard(
    item: ReleaseItem,
    saved: Boolean,
    onOpenInApp: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val badgeColor = when {
        item.isFeature -> SolcoColors.AccentAmber
        item.type == "album" -> SolcoColors.AccentViolet
        else -> SolcoColors.AccentCyan
    }
    GlassCard(
        glowBorder = item.type == "album",
        accentColor = badgeColor,
        onClick = onOpenInApp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoverArt(url = item.img, name = item.title, size = 62.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when {
                                item.isFeature -> "FEAT"
                                item.type == "album" -> "ALBUM · ${item.tracks} brani"
                                else -> "SINGOLO"
                            },
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(formatDateIt(item.date), style = MaterialTheme.typography.labelSmall)
                }
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.artists.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.by.isNotEmpty()) {
                    Text(
                        "Trovato grazie a: ${item.by.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolcoColors.AccentMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleSave, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Salva",
                        tint = if (saved) SolcoColors.AccentGold else SolcoColors.TextMuted
                    )
                }
                if (!item.link.isNullOrBlank()) {
                    IconButton(onClick = onOpenExternal, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Apri esterno", tint = SolcoColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
