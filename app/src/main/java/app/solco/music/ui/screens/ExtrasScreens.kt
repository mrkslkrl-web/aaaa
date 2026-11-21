package app.solco.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solco.music.data.model.GoalRule
import app.solco.music.data.model.UnresolvedEntry
import app.solco.music.data.model.UwuTournament
import app.solco.music.data.remote.MatchedTrackResult
import app.solco.music.data.repository.ImportSummary
import app.solco.music.data.repository.SolcoUiSnapshot
import app.solco.music.domain.ModelEngine
import app.solco.music.domain.TextNormalizer
import app.solco.music.ui.SolcoViewModel
import app.solco.music.ui.components.CoverArt
import app.solco.music.ui.components.EmptyPlaceholder
import app.solco.music.ui.components.GlassCard
import app.solco.music.ui.components.formatInt
import app.solco.music.ui.theme.SolcoColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

// ==================== TOP SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var kind by remember { mutableStateOf("artist") }
    var mode by remember { mutableStateOf("mine") } // "mine", "auto"
    var maxTier by remember { mutableIntStateOf(10) }
    var pickingTier by remember { mutableStateOf<Int?>(null) }
    var pickQuery by remember { mutableStateOf("") }

    val model = state.model
    val stats = state.statsAll
    val userMap = state.topMap[kind].orEmpty()

    val autoRankedIds = remember(model, stats, kind) {
        when (kind) {
            "artist" -> stats.artist.entries.sortedByDescending { it.value.plays }.map { it.key }
            "album" -> stats.album.entries
                .filter { model.albums[it.key]?.let { al -> al.aliasOf == null && al.total >= ModelEngine.ALBUM_MIN_TRACKS } == true }
                .sortedByDescending { it.value.totalPlays }.map { it.key }
            else -> stats.song.entries.sortedByDescending { it.value.plays }.map { it.key }
        }
    }

    fun entityInfo(id: String): Triple<String, String?, Boolean>? = when (kind) {
        "artist" -> model.artists[model.artistCanon[id] ?: id]?.let { Triple(it.name, it.img, true) }
        "album" -> model.albums[model.albumCanon[id] ?: id]?.let { Triple(it.name, it.img, false) }
        else -> model.songs[model.songOf[id] ?: id]?.let { Triple(it.title, it.img, false) }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
            Column {
                Text("Top", style = MaterialTheme.typography.headlineMedium)
                Text(if (mode == "mine") "La tua classifica a fasce" else "Classifica automatica per ascolti", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("artist" to "Artisti", "album" to "Album", "track" to "Singoli").forEach { (k, lbl) ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(lbl) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == "mine", onClick = { mode = "mine" }, label = { Text("Mia Top") })
            FilterChip(selected = mode == "auto", onClick = { mode = "auto" }, label = { Text("Automatica") })
            listOf(10, 20, 50).forEach { sz ->
                FilterChip(selected = maxTier == sz, onClick = { maxTier = sz }, label = { Text("Top $sz") })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var prevTier = 0
            val tiers = ModelEngine.TOP_TIERS.filter { it <= maxTier }
            items(tiers, key = { it }) { tier ->
                val cap = ModelEngine.TOP_CAPS[tier] ?: 1
                val itemsInTier = if (mode == "auto") {
                    val start = tiers.filter { it < tier }.sumOf { ModelEngine.TOP_CAPS[it] ?: 0 }
                    autoRankedIds.drop(start).take(cap)
                } else {
                    userMap.filterValues { it == tier }.keys.toList()
                }
                val tierColor = when (tier) {
                    1 -> SolcoColors.AccentGold
                    3 -> SolcoColors.AccentCyan
                    5 -> SolcoColors.AccentViolet
                    else -> SolcoColors.AccentPink
                }
                GlassCard(glowBorder = tier <= 3, accentColor = tierColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (tier == 1) "🏆 Top 1" else "Top $tier (${itemsInTier.size}/$cap)",
                            style = MaterialTheme.typography.titleMedium,
                            color = tierColor
                        )
                        if (mode == "mine" && itemsInTier.size < cap) {
                            TextButton(onClick = { pickingTier = tier; pickQuery = "" }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Aggiungi")
                            }
                        }
                    }
                    if (itemsInTier.isEmpty()) {
                        Text("Fascia vuota", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Spacer(Modifier.height(6.dp))
                        itemsInTier.forEach { eid ->
                            val info = entityInfo(eid) ?: return@forEach
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onNavigate("$kind/$eid") }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CoverArt(url = info.second, name = info.first, size = 40.dp, round = info.third)
                                Text(info.first, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (mode == "mine") {
                                    IconButton(onClick = { vm.setTopTier(kind, eid, null) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Rimuovi", tint = SolcoColors.TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                prevTier = tier
            }
        }
    }

    pickingTier?.let { tier ->
        ModalBottomSheet(
            onDismissRequest = { pickingTier = null },
            containerColor = SolcoColors.BgSurface
        ) {
            val qNorm = TextNormalizer.norm(pickQuery)
            val candidates = autoRankedIds.mapNotNull { id ->
                val info = entityInfo(id) ?: return@mapNotNull null
                if (qNorm.isNotEmpty() && !TextNormalizer.norm(info.first).contains(qNorm)) return@mapNotNull null
                id to info
            }.take(40)

            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aggiungi a Top $tier", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pickQuery,
                    onValueChange = { pickQuery = it },
                    placeholder = { Text("Cerca…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(candidates, key = { it.first }) { (id, info) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    vm.setTopTier(kind, id, tier)
                                    pickingTier = null
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CoverArt(url = info.second, name = info.first, size = 42.dp, round = info.third)
                            Text(info.first, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ==================== UWUFUFU TOURNAMENTS SCREEN ====================

@Composable
fun UwuFufuScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit
) {
    var activeId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val model = state.model
    val stats = state.statsAll

    fun resolveEntity(kind: String, id: String): Triple<String, String?, Boolean> = when (kind) {
        "artist" -> model.artists[model.artistCanon[id] ?: id]?.let { Triple(it.name, it.img, true) } ?: Triple(id, null, true)
        "album" -> model.albums[model.albumCanon[id] ?: id]?.let { Triple(it.name, it.img, false) } ?: Triple(id, null, false)
        else -> model.songs[model.songOf[id] ?: id]?.let { Triple(it.title, it.img, false) } ?: Triple(id, null, false)
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("artist") }
        var size by remember { mutableIntStateOf(16) }

        val pool = remember(model, stats, kind, size) {
            val ids = when (kind) {
                "artist" -> stats.artist.entries.sortedByDescending { it.value.plays }.map { it.key }
                "album" -> stats.album.entries
                    .filter { model.albums[it.key]?.let { a -> a.aliasOf == null && a.total >= ModelEngine.ALBUM_MIN_TRACKS } == true }
                    .sortedByDescending { it.value.totalPlays }.map { it.key }
                else -> stats.song.entries.sortedByDescending { it.value.plays }.map { it.key }
            }
            ids.take(size)
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { creating = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
                Text("Nuovo torneo UwuFufu", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome torneo") },
                placeholder = { Text("Es. Miglior album di sempre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Categoria", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("artist" to "Artisti", "album" to "Album", "track" to "Singoli").forEach { (k, l) ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(l) })
                }
            }
            Text("Partecipanti (i tuoi più ascoltati)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 16, 32, 64).forEach { n ->
                    FilterChip(selected = size == n, onClick = { size = n }, label = { Text("$n") })
                }
            }
            Text("Trovati ${pool.size} partecipanti pronti", style = MaterialTheme.typography.bodyMedium, color = SolcoColors.AccentCyan)
            Button(
                onClick = {
                    if (pool.size < 2) {
                        vm.showToast("Servono almeno 2 partecipanti")
                        return@Button
                    }
                    val id = UUID.randomUUID().toString().take(8)
                    val now = System.currentTimeMillis()
                    val t = UwuTournament(
                        id = id,
                        name = name.ifBlank { "Torneo ${if (kind == "artist") "Artisti" else if (kind == "album") "Album" else "Singoli"}" },
                        kind = kind,
                        seed = ModelEngine.buildBracketSeed(pool, shuffle = true),
                        picks = emptyList(),
                        created = now,
                        updated = now
                    )
                    vm.saveTournament(t)
                    creating = false
                    activeId = id
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentPink)
            ) {
                Text("Inizia torneo", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
        return
    }

    val active = activeId?.let { id -> state.tournaments.find { it.id == id } }
    if (active != null) {
        val bracket = remember(active) { ModelEngine.evaluateBracket(active) }
        val totalMatches = (active.seed.count { it != null } - 1).coerceAtLeast(1)
        var confirmDelete by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = { activeId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
                    Column {
                        Text(active.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (bracket.done) "Completato!" else "${ModelEngine.roundLabel(bracket.roundSize)} · Sfida ${bracket.matchNo}/${bracket.matchesInRound}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolcoColors.AccentPink
                        )
                    }
                }
                Row {
                    if (active.picks.isNotEmpty()) {
                        IconButton(onClick = {
                            vm.saveTournament(active.copy(picks = active.picks.dropLast(1), updated = System.currentTimeMillis()))
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Annulla", tint = SolcoColors.AccentCyan)
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Elimina", tint = SolcoColors.AccentCoral)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { (bracket.picksUsed.toFloat() / totalMatches.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = SolcoColors.AccentPink,
                trackColor = SolcoColors.Border
            )

            if (bracket.done && bracket.champion != null) {
                val champ = resolveEntity(active.kind, bracket.champion)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassCard(glowBorder = true, accentColor = SolcoColors.AccentGold, contentPadding = PaddingValues(28.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🏆 VINCITORE", color = SolcoColors.AccentGold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            CoverArt(url = champ.second, name = champ.first, size = 140.dp, round = champ.third, glow = true)
                            Text(champ.first, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                            OutlinedButton(onClick = {
                                vm.saveTournament(active.copy(picks = emptyList(), updated = System.currentTimeMillis()))
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Rigioca torneo")
                            }
                        }
                    }
                }
            } else if (bracket.a != null && bracket.b != null) {
                val entA = resolveEntity(active.kind, bracket.a)
                val entB = resolveEntity(active.kind, bracket.b)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DuelCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        name = entA.first,
                        img = entA.second,
                        round = entA.third,
                        accent = SolcoColors.AccentViolet,
                        onPick = {
                            vm.saveTournament(active.copy(picks = active.picks + 0, updated = System.currentTimeMillis()))
                        }
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SolcoColors.PrimaryGradient)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("VS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                    DuelCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        name = entB.first,
                        img = entB.second,
                        round = entB.third,
                        accent = SolcoColors.AccentCyan,
                        onPick = {
                            vm.saveTournament(active.copy(picks = active.picks + 1, updated = System.currentTimeMillis()))
                        }
                    )
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Eliminare il torneo?") },
                text = { Text("Questa azione rimuoverà \"${active.name}\".") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteTournament(active.id)
                        activeId = null
                        confirmDelete = false
                    }) { Text("Elimina", color = SolcoColors.AccentCoral) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
                }
            )
        }
        return
    }

    // Tournament list
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
                Text("UwuFufu", style = MaterialTheme.typography.headlineMedium)
            }
            Button(
                onClick = { creating = true },
                colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentPink),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nuovo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (state.tournaments.isEmpty()) {
            EmptyPlaceholder(
                icon = Icons.Rounded.SportsKabaddi,
                title = "Nessun torneo",
                subtitle = "Sfida i tuoi artisti, album o singoli preferiti 1 contro 1 finché ne resta uno.",
                actionLabel = "Crea torneo",
                onAction = { creating = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.tournaments, key = { it.id }) { t ->
                    val b = remember(t) { ModelEngine.evaluateBracket(t) }
                    val champ = b.champion?.let { resolveEntity(t.kind, it) }
                    GlassCard(onClick = { activeId = t.id }, glowBorder = b.done, accentColor = SolcoColors.AccentGold) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (champ != null) {
                                CoverArt(url = champ.second, name = champ.first, size = 50.dp, round = champ.third)
                            } else {
                                Box(
                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(SolcoColors.AccentPink.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.SportsKabaddi, contentDescription = null, tint = SolcoColors.AccentPink)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.Bold)
                                Text(
                                    if (b.done && champ != null) "🏆 ${champ.first}"
                                    else "${t.seed.count { it != null }} in gara · ${b.picksUsed} scelte fatte",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (b.done) SolcoColors.AccentGold else SolcoColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuelCard(
    modifier: Modifier,
    name: String,
    img: String?,
    round: Boolean,
    accent: Color,
    onPick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        glowBorder = true,
        accentColor = accent,
        onClick = onPick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CoverArt(url = img, name = name, size = 112.dp, round = round, glow = true)
            Spacer(Modifier.height(14.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("Tocca per scegliere", style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

// ==================== PENDING ("DA COLLEGARE") SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit
) {
    val entries = remember(state.model.unresolved) {
        state.model.unresolved.values.sortedByDescending { it.count }
    }
    var selected by remember { mutableStateOf<UnresolvedEntry?>(null) }
    var searchResults by remember { mutableStateOf<List<MatchedTrackResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var customQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
                Column {
                    Text("Da collegare", style = MaterialTheme.typography.headlineMedium)
                    Text("${formatInt(entries.size)} brani in attesa", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (entries.isNotEmpty()) {
                Button(
                    onClick = { vm.runMatcherNow() },
                    enabled = !state.matching,
                    colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (state.matching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF07070D), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = Color(0xFF07070D), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto-collega", color = Color(0xFF07070D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyPlaceholder(
                icon = Icons.Rounded.CheckCircle,
                title = "Tutto collegato!",
                subtitle = "Ogni ascolto è stato abbinato correttamente al suo brano e artista."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.nameId }) { item ->
                    GlassCard(
                        onClick = {
                            selected = item
                            customQuery = "${item.artist} ${item.track}"
                            searching = true
                            scope.launch {
                                searchResults = vm.searchCandidates(item.artist, item.track, item.album)
                                searching = false
                            }
                        },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.track, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${item.artist}${if (item.album.isNotBlank()) " · ${item.album}" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${item.count} ascolti", style = MaterialTheme.typography.labelSmall, color = SolcoColors.AccentAmber)
                            }
                            Icon(Icons.Rounded.Link, contentDescription = "Collega", tint = SolcoColors.AccentCyan)
                        }
                    }
                }
            }
        }
    }

    selected?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = SolcoColors.BgSurface
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Collega \"${entry.track}\"", style = MaterialTheme.typography.titleLarge)
                Text("${entry.artist} · ${entry.count} ascolti", style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customQuery,
                        onValueChange = { customQuery = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Cerca su Spotify / Deezer…") }
                    )
                    IconButton(onClick = {
                        searching = true
                        scope.launch {
                            searchResults = vm.searchCandidates("", customQuery, "")
                            searching = false
                        }
                    }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Cerca", tint = SolcoColors.AccentCyan)
                    }
                }

                if (searching) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SolcoColors.AccentCyan)
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchResults, key = { it.trackId }) { cand ->
                            GlassCard(
                                onClick = {
                                    vm.linkUnresolved(entry.nameId, cand)
                                    selected = null
                                },
                                contentPadding = PaddingValues(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CoverArt(url = cand.albumImg, name = cand.title, size = 44.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cand.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${cand.trackArtists.joinToString(", ") { it.second }} · ${cand.albumName.orEmpty()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        if (cand.source == "sp") "Spotify" else "Deezer",
                                        fontSize = 11.sp,
                                        color = if (cand.source == "sp") SolcoColors.AccentMint else SolcoColors.AccentViolet,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        vm.ignoreUnresolved(entry.nameId)
                        selected = null
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Ignora questo brano", color = SolcoColors.AccentCoral)
                }
            }
        }
    }
}

// ==================== IMPORT SPOTIFY HISTORY SCREEN ====================

@Composable
fun ImportScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit
) {
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true; error = null
            vm.importSpotifyHistory(uris) { sum, err ->
                summary = sum; error = err; busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
            Text("Importa storico Spotify", style = MaterialTheme.typography.headlineMedium)
        }

        GlassCard(glowBorder = true, accentColor = SolcoColors.AccentMint) {
            Text("Carica i file di Spotify Privacy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Supporta sia i file ZIP sia i singoli JSON (Streaming_History_Audio_*.json o StreamingHistory_music_*.json). Gli ascolti già presenti vengono deduplicati automaticamente.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { picker.launch(arrayOf("application/zip", "application/json", "*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentMint)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF07070D), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = Color(0xFF07070D))
                    Spacer(Modifier.width(8.dp))
                    Text("Scegli file ZIP o JSON", color = Color(0xFF07070D), fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Text("Ascolti Spotify già salvati: ${formatInt(state.model.spCount)}", style = MaterialTheme.typography.bodyMedium, color = SolcoColors.AccentCyan)

        summary?.let { s ->
            GlassCard(accentColor = SolcoColors.AccentCyan, glowBorder = true) {
                Text("Importazione completata!", style = MaterialTheme.typography.titleMedium, color = SolcoColors.AccentMint)
                Text("File letti: ${s.filesParsed} · Ascolti importati: ${formatInt(s.keptPlays)}")
                Text("Scartati (<30s): ${formatInt(s.skippedShort)} · Podcast: ${formatInt(s.skippedPodcasts)}", style = MaterialTheme.typography.labelSmall)
            }
        }
        error?.let {
            Text(it, color = SolcoColors.AccentCoral)
        }
    }
}

// ==================== SETTINGS SCREEN ====================

@Composable
fun SettingsScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onBack: () -> Unit
) {
    val lfm = state.lastFmConfig
    val sp = state.spotifyConfig
    val goals = state.goalsConfig

    var lfmUser by remember(lfm) { mutableStateOf(lfm.user) }
    var lfmKey by remember(lfm) { mutableStateOf(lfm.key) }
    var lfmBusy by remember { mutableStateOf(false) }
    var lfmErr by remember { mutableStateOf<String?>(null) }

    var spId by remember(sp) { mutableStateOf(sp.id) }
    var spSecret by remember(sp) { mutableStateOf(sp.secret) }
    var spBusy by remember { mutableStateOf(false) }
    var spErr by remember { mutableStateOf<String?>(null) }

    var albumStart by remember(goals) { mutableStateOf(goals.album.start.ifBlank { LocalDate.now().toString() }) }
    var albumPer by remember(goals) { mutableStateOf(goals.album.per.toString()) }
    var trackStart by remember(goals) { mutableStateOf(goals.track.start.ifBlank { LocalDate.now().toString() }) }
    var trackPer by remember(goals) { mutableStateOf(goals.track.per.toString()) }
    val paused = goals.pauses.any { it.to == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro") }
                Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)
            }
        }

        // Last.fm
        item {
            GlassCard(glowBorder = lfm.user.isNotBlank(), accentColor = SolcoColors.AccentCoral) {
                Text("Last.fm", style = MaterialTheme.typography.titleLarge)
                Text("Sincronizza automaticamente gli ascolti in tempo reale.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = lfmUser, onValueChange = { lfmUser = it }, label = { Text("Username Last.fm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = lfmKey, onValueChange = { lfmKey = it }, label = { Text("API Key Last.fm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                lfmErr?.let { Text(it, color = SolcoColors.AccentCoral, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        lfmBusy = true; lfmErr = null
                        vm.saveLastFm(lfmUser, lfmKey) { err -> lfmErr = err; lfmBusy = false }
                    },
                    enabled = !lfmBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lfmBusy) "Verifica…" else "Salva & Sincronizza Last.fm", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Spotify API
        item {
            GlassCard(glowBorder = sp.id.isNotBlank(), accentColor = SolcoColors.AccentMint) {
                Text("Spotify API", style = MaterialTheme.typography.titleLarge)
                Text("Migliora il riconoscimento dei brani e la scansione delle nuove uscite (inclusi i featuring). Se vuoto, Solco usa comunque Deezer.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = spId, onValueChange = { spId = it }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = spSecret, onValueChange = { spSecret = it }, label = { Text("Client Secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                spErr?.let { Text(it, color = SolcoColors.AccentCoral, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        spBusy = true; spErr = null
                        vm.saveSpotify(spId, spSecret) { err -> spErr = err; spBusy = false }
                    },
                    enabled = !spBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (spBusy) "Verifica…" else "Salva credenziali Spotify", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Goals
        item {
            GlassCard {
                Text("Obiettivi giornalieri", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = albumStart, onValueChange = { albumStart = it }, label = { Text("Inizio album") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(value = albumPer, onValueChange = { albumPer = it }, label = { Text("Album/g") }, singleLine = true, modifier = Modifier.weight(0.8f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = trackStart, onValueChange = { trackStart = it }, label = { Text("Inizio singoli") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(value = trackPer, onValueChange = { trackPer = it }, label = { Text("Singoli/g") }, singleLine = true, modifier = Modifier.weight(0.8f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.saveGoals(
                                goals.copy(
                                    album = GoalRule(albumStart, albumPer.toIntOrNull()?.coerceAtLeast(1) ?: 1),
                                    track = GoalRule(trackStart, trackPer.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Salva")
                    }
                    OutlinedButton(onClick = { vm.toggleGoalsPause() }) {
                        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (paused) "Riprendi" else "Pausa")
                    }
                }
            }
        }

        // Account
        item {
            GlassCard {
                Text("Account sincronizzato", style = MaterialTheme.typography.titleMedium)
                Text(state.userEmail.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = SolcoColors.AccentCyan)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { vm.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null, tint = SolcoColors.AccentCoral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Esci dall'account", color = SolcoColors.AccentCoral)
                }
            }
        }
    }
}
