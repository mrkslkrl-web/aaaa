package app.solco.music.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solco.music.data.remote.MusicApiClients
import app.solco.music.data.repository.SolcoUiSnapshot
import app.solco.music.domain.ModelEngine
import app.solco.music.ui.SolcoViewModel
import app.solco.music.ui.components.AmbientBackdrop
import app.solco.music.ui.components.CoverArt
import app.solco.music.ui.components.GlassCard
import app.solco.music.ui.components.MonthlyBarChart
import app.solco.music.ui.components.formatDurationShort
import app.solco.music.ui.components.formatInt
import app.solco.music.ui.components.formatRelativeSec
import app.solco.music.ui.theme.SolcoColors

@Composable
fun LoginScreen(vm: SolcoViewModel) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val spin = rememberInfiniteTransition(label = "vinylSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )

    AmbientBackdrop(primaryGlow = SolcoColors.AccentViolet, secondaryGlow = SolcoColors.AccentPink) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowBorder = true,
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .rotate(angle)
                            .clip(CircleShape)
                            .background(SolcoColors.PrimaryGradient)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(SolcoColors.BgDeep),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = "Solco",
                            tint = SolcoColors.AccentViolet,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Text("Solco", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = if (isRegister) "Crea il tuo account per sincronizzare tutto."
                        else "Accedi con lo stesso account della tua app Solco: tutti i tuoi dati e recensioni si caricheranno subito.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SolcoColors.AccentViolet,
                            unfocusedBorderColor = SolcoColors.Border
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SolcoColors.AccentViolet,
                            unfocusedBorderColor = SolcoColors.Border
                        )
                    )

                    if (isRegister) {
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text("Ripeti password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SolcoColors.AccentViolet,
                                unfocusedBorderColor = SolcoColors.Border
                            )
                        )
                    }

                    error?.let {
                        Text(it, color = SolcoColors.AccentCoral, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    info?.let {
                        Text(it, color = SolcoColors.AccentMint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            error = null; info = null
                            if (email.isBlank() || password.isBlank()) {
                                error = "Inserisci email e password"
                                return@Button
                            }
                            if (isRegister && password != confirm) {
                                error = "Le password non coincidono"
                                return@Button
                            }
                            busy = true
                            if (isRegister) {
                                vm.signUp(email, password, { error = it; busy = false }, { busy = false })
                            } else {
                                vm.signIn(email, password, { error = it; busy = false }, { busy = false })
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentViolet)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color(0xFF07070D), strokeWidth = 2.5.dp)
                        } else {
                            Text(
                                if (isRegister) "Registrati" else "Entra",
                                color = Color(0xFF07070D),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { isRegister = !isRegister; error = null; info = null }) {
                            Text(if (isRegister) "Hai già un account?" else "Crea account", color = SolcoColors.AccentCyan)
                        }
                        if (!isRegister) {
                            TextButton(onClick = {
                                if (email.isBlank()) error = "Scrivi prima la tua email"
                                else vm.sendPasswordReset(email) { info = it }
                            }) {
                                Text("Password dimenticata?", color = SolcoColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RecentPlayRow(
    val key: String,
    val songId: String,
    val title: String,
    val artist: String,
    val img: String?,
    val ts: Long
)

@Composable
fun HomeScreen(
    state: SolcoUiSnapshot,
    vm: SolcoViewModel,
    onNavigate: (String) -> Unit
) {
    val activeReviews = remember(state.reviews) { state.reviews.values.filter { it.del == 0 } }
    val albumGoal = remember(activeReviews, state.kv) {
        ModelEngine.computeGoalProgress(activeReviews, "album", state.goalsConfig)
    }
    val singleGoal = remember(activeReviews, state.kv) {
        ModelEngine.computeGoalProgress(activeReviews, "track", state.goalsConfig)
    }
    val todayDate = MusicApiClients.todayDate()
    val todayReleasesCount = remember(state.releases) { state.releases.count { it.date == todayDate } }
    val unresolvedCount = state.model.unresolved.size

    // Recent plays (latest 12 real plays in reverse chronological order!)
    val recentPlays = remember(state.model.plays, state.model.songs) {
        val plays = state.model.plays
        val out = ArrayList<RecentPlayRow>(12)
        for (i in plays.lastIndex downTo 0) {
            if (out.size >= 12) break
            val p = plays[i]
            val sid = state.model.songOf[p.trackId] ?: p.trackId
            val song = state.model.songs[sid] ?: continue
            val artistStr = song.artists.joinToString(", ") { it.second }.ifBlank { "Artista" }
            out.add(
                RecentPlayRow(
                    key = "${p.ts}_${p.trackId}_$i",
                    songId = sid,
                    title = song.title,
                    artist = artistStr,
                    img = song.img,
                    ts = p.ts
                )
            )
        }
        out
    }

    // 12-month global activity chart
    val monthlyAll = remember(state.model.plays) {
        ModelEngine.monthlyTotalActivity(state.model, 12)
    }

    // 30-day highlights (#1 Artist & #1 Album of the month)
    val monthStats = remember(state.model) { vm.statsForPeriod("d30") }
    val topMonthArtist = remember(monthStats, state.model.artists) {
        monthStats.artist.entries.maxByOrNull { it.value.plays }?.let { (id, st) ->
            state.model.artists[id]?.let { it to st }
        }
    }
    val topMonthAlbum = remember(monthStats, state.model.albums) {
        monthStats.album.entries
            .filter { state.model.albums[it.key]?.let { a -> a.aliasOf == null && a.total >= ModelEngine.ALBUM_MIN_TRACKS } == true }
            .maxByOrNull { it.value.totalPlays }?.let { (id, st) ->
                state.model.albums[id]?.let { it to st }
            }
    }

    var showAllRecent by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Compact vibrant header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SolcoColors.PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("Solco", style = MaterialTheme.typography.headlineMedium)
                        val subText = when {
                            state.syncing -> "Sincronizzo ascolti…"
                            state.matching -> "Collego brani (${state.matchProgress})…"
                            state.refreshingPhotos -> "Aggiorno foto artisti (${state.photoProgress})…"
                            state.lastSyncMs > 0L -> "Aggiornato ${formatRelativeSec(state.lastSyncMs / 1000L)}"
                            state.error != null -> state.error
                            else -> "${formatInt(state.statsAll.totalPlays)} ascolti totali"
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.error != null && !state.syncing) SolcoColors.AccentCoral else SolcoColors.AccentCyan
                        )
                    }
                }

                IconButton(
                    onClick = { vm.syncNow() },
                    enabled = !state.syncing,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SolcoColors.CardElevated)
                        .border(1.dp, SolcoColors.BorderGlow, CircleShape)
                ) {
                    if (state.syncing || state.matching || state.refreshingPhotos) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SolcoColors.AccentCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna", tint = SolcoColors.AccentCyan)
                    }
                }
            }
        }

        // Now Playing banner
        state.nowPlaying?.let { np ->
            if (np.track.isNotBlank()) {
                item {
                    GlassCard(
                        glowBorder = true,
                        accentColor = SolcoColors.AccentMint,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CoverArt(url = np.img, name = np.track, size = 54.dp, glow = true)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = SolcoColors.AccentMint, modifier = Modifier.size(14.dp))
                                    Text("IN ASCOLTO ORA", color = SolcoColors.AccentMint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Text(np.track, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(np.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Hero Stats Banner + Goals in one dense, rich dashboard block
        item {
            val st = state.statsAll
            GlassCard(
                glowBorder = true,
                accentColor = SolcoColors.AccentViolet,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ASCOLTI TOTALI", style = MaterialTheme.typography.labelSmall, color = SolcoColors.AccentCyan, fontWeight = FontWeight.ExtraBold)
                        Text(formatInt(st.totalPlays), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = SolcoColors.TextPrimary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatDurationShort(st.totalMs), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = SolcoColors.AccentViolet)
                            Text("ascoltati", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatInt(st.artist.size), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = SolcoColors.AccentPink)
                            Text("artisti", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatInt(activeReviews.size), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = SolcoColors.AccentMint)
                            Text("recensioni", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Goals pair cards (Album + Singoli)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GoalTileCard(
                    modifier = Modifier.weight(1f),
                    title = "Obiettivo Album",
                    icon = Icons.Rounded.Album,
                    accent = SolcoColors.AccentViolet,
                    balance = albumGoal.balance,
                    todayCount = albumGoal.todayCount,
                    paused = albumGoal.paused,
                    onClick = { onNavigate("reviews?tab=album") }
                )
                GoalTileCard(
                    modifier = Modifier.weight(1f),
                    title = "Obiettivo Singoli",
                    icon = Icons.Rounded.MusicNote,
                    accent = SolcoColors.AccentPink,
                    balance = singleGoal.balance,
                    todayCount = singleGoal.todayCount,
                    paused = singleGoal.paused,
                    onClick = { onNavigate("reviews?tab=track") }
                )
            }
        }

        // Notices if needed
        if (state.lastFmConfig.user.isBlank() || state.lastFmConfig.key.isBlank()) {
            item {
                ActionNoticeBanner(
                    icon = Icons.Rounded.Settings,
                    text = "Collega Last.fm per aggiornare automaticamente gli ascolti",
                    tint = SolcoColors.AccentAmber,
                    onClick = { onNavigate("settings") }
                )
            }
        }
        if (unresolvedCount > 0) {
            item {
                ActionNoticeBanner(
                    icon = Icons.Rounded.Link,
                    text = "${formatInt(unresolvedCount)} brani da collegare (tocca per risolverli)",
                    tint = SolcoColors.AccentCoral,
                    onClick = { onNavigate("pending") }
                )
            }
        }

        // ========== ASCOLTATI DI RECENTE (BACK IN HOME!) ==========
        if (recentPlays.isNotEmpty()) {
            item {
                GlassCard(contentPadding = PaddingValues(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = SolcoColors.AccentCyan, modifier = Modifier.size(19.dp))
                            Text("Ascoltati di recente", style = MaterialTheme.typography.titleLarge)
                        }
                        if (recentPlays.size > 5) {
                            TextButton(
                                onClick = { showAllRecent = !showAllRecent },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(if (showAllRecent) "Meno" else "Mostra ${recentPlays.size}", color = SolcoColors.AccentCyan, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val shown = if (showAllRecent) recentPlays else recentPlays.take(5)
                    shown.forEach { rp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigate("track/${rp.songId}") }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CoverArt(url = rp.img, name = rp.title, size = 44.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rp.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rp.artist, style = MaterialTheme.typography.labelSmall, color = SolcoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                text = formatRelativeSec(rp.ts),
                                style = MaterialTheme.typography.labelSmall,
                                color = SolcoColors.TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Highlights ultimi 30 giorni (#1 Artista & #1 Album del mese)
        if (topMonthArtist != null || topMonthAlbum != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    topMonthArtist?.let { (art, st) ->
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            glowBorder = true,
                            accentColor = SolcoColors.AccentGold,
                            onClick = { onNavigate("artist/${art.id}") },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text("TOP ARTISTA · 30G", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = SolcoColors.AccentGold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CoverArt(url = art.img, name = art.name, size = 48.dp, round = true)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(art.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatInt(st.plays)} ascolti", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SolcoColors.AccentCyan)
                                }
                            }
                        }
                    }
                    topMonthAlbum?.let { (al, st) ->
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            glowBorder = true,
                            accentColor = SolcoColors.AccentPink,
                            onClick = { onNavigate("album/${al.id}") },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text("TOP ALBUM · 30G", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = SolcoColors.AccentPink)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CoverArt(url = al.img, name = al.name, size = 48.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(al.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatInt(st.totalPlays)} ascolti", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SolcoColors.AccentViolet)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nuove uscite strip (with horizontal cover art preview!)
        item {
            GlassCard(
                glowBorder = true,
                accentColor = SolcoColors.AccentCyan,
                onClick = { onNavigate("releases") },
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = SolcoColors.AccentCyan, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Nuove uscite", style = MaterialTheme.typography.titleMedium)
                            val msg = when {
                                state.releaseScan.scanning -> "Scansione: ${state.releaseScan.phase}"
                                todayReleasesCount > 0 -> "$todayReleasesCount oggi · ${state.releases.size} negli ultimi 7g"
                                state.releases.isNotEmpty() -> "${state.releases.size} uscite recenti dai tuoi artisti"
                                else -> "Tocca per scansionare tutti i tuoi artisti"
                            }
                            Text(msg, style = MaterialTheme.typography.labelSmall, color = SolcoColors.TextSecondary)
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SolcoColors.AccentCyan)
                }
                if (state.releases.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.releases.take(8).forEach { rel ->
                            Column(
                                modifier = Modifier
                                    .width(74.dp)
                                    .clickable {
                                        vm.openReleaseAlbum(rel) { alId -> onNavigate("album/$alId") }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CoverArt(url = rel.img, name = rel.title, size = 70.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(rel.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(rel.artists.firstOrNull().orEmpty(), fontSize = 10.sp, color = SolcoColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // 12-month global activity chart
        if (monthlyAll.any { it.plays > 0 }) {
            item {
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = SolcoColors.AccentViolet, modifier = Modifier.size(18.dp))
                            Text("Andamento ultimi 12 mesi", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${formatInt(monthlyAll.sumOf { it.plays })} ascolti", style = MaterialTheme.typography.labelSmall, color = SolcoColors.AccentCyan)
                    }
                    Spacer(Modifier.height(10.dp))
                    MonthlyBarChart(buckets = monthlyAll)
                }
            }
        }

        // Quick shortcuts
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutPill(Modifier.weight(1f), Icons.Rounded.RateReview, "Recensioni", SolcoColors.AccentViolet) { onNavigate("reviews") }
                ShortcutPill(Modifier.weight(1f), Icons.Rounded.EmojiEvents, "Top", SolcoColors.AccentGold) { onNavigate("top") }
                ShortcutPill(Modifier.weight(1f), Icons.Rounded.SportsKabaddi, "UwuFufu", SolcoColors.AccentPink) { onNavigate("uwu") }
            }
        }
    }
}

@Composable
private fun GoalTileCard(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    accent: Color,
    balance: Int,
    todayCount: Int,
    paused: Boolean,
    onClick: () -> Unit
) {
    val balColor = when {
        paused -> SolcoColors.AccentAmber
        balance >= 0 -> SolcoColors.AccentMint
        else -> SolcoColors.AccentCoral
    }
    GlassCard(
        modifier = modifier,
        glowBorder = true,
        accentColor = accent,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (paused) {
                Icon(Icons.Rounded.PauseCircle, contentDescription = "In pausa", tint = SolcoColors.AccentAmber, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val sign = if (balance > 0) "+$balance" else balance.toString()
            Text(sign, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = balColor)
            Text(
                text = if (todayCount > 0) "Oggi: $todayCount" else "0 oggi",
                style = MaterialTheme.typography.labelSmall,
                color = SolcoColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ActionNoticeBanner(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit
) {
    GlassCard(
        glowBorder = true,
        accentColor = tint,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tint)
        }
    }
}

@Composable
private fun ShortcutPill(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private data class MoreMenuItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val badge: Int = 0
)

@Composable
fun MoreScreen(
    state: SolcoUiSnapshot,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        MoreMenuItem("releases", "Nuove uscite", "Tutti gli artisti", Icons.Rounded.AutoAwesome, SolcoColors.AccentCyan),
        MoreMenuItem("reviews", "Recensioni", "Album, singoli, artisti", Icons.Rounded.RateReview, SolcoColors.AccentViolet),
        MoreMenuItem("top", "Top", "Classifica a fasce", Icons.Rounded.EmojiEvents, SolcoColors.AccentGold),
        MoreMenuItem("uwu", "UwuFufu", "Tornei a eliminazione", Icons.Rounded.SportsKabaddi, SolcoColors.AccentPink),
        MoreMenuItem("pending", "Da collegare", "Abbina brani", Icons.Rounded.Link, SolcoColors.AccentCoral, state.model.unresolved.size),
        MoreMenuItem("import", "Importa storico", "JSON / ZIP Spotify", Icons.Rounded.UploadFile, SolcoColors.AccentMint),
        MoreMenuItem("settings", "Impostazioni", "Last.fm, Spotify, Foto", Icons.Rounded.Settings, SolcoColors.AccentAmber)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text("Altro", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 104.dp)
        ) {
            items(items) { item ->
                GlassCard(
                    glowBorder = item.badge > 0,
                    accentColor = item.accent,
                    onClick = { onNavigate(item.route) },
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(item.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = null, tint = item.accent, modifier = Modifier.size(24.dp))
                        }
                        if (item.badge > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SolcoColors.AccentCoral)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(item.badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.subtitle, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
