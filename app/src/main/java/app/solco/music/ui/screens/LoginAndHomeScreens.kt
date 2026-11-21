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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.rounded.Person
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
    val recentReleasesCount = remember(state.releases) { state.releases.size }
    val unresolvedCount = state.model.unresolved.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SolcoColors.PrimaryGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Text("Solco", style = MaterialTheme.typography.headlineLarge)
                    }
                    val subText = when {
                        state.syncing -> "Sincronizzo ascolti e dati…"
                        state.matching -> "Collego brani (${state.matchProgress})…"
                        state.lastSyncMs > 0L -> "Aggiornato ${formatRelativeSec(state.lastSyncMs / 1000L)}"
                        state.error != null -> state.error
                        else -> "I tuoi ascolti e recensioni"
                    }
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error != null && !state.syncing) SolcoColors.AccentCoral else SolcoColors.TextSecondary
                    )
                }

                IconButton(
                    onClick = { vm.syncNow() },
                    enabled = !state.syncing,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SolcoColors.CardElevated)
                        .border(1.dp, SolcoColors.BorderGlow, CircleShape)
                ) {
                    if (state.syncing || state.matching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SolcoColors.AccentCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna", tint = SolcoColors.AccentCyan)
                    }
                }
            }
        }

        // Now Playing banner (if active on Last.fm)
        state.nowPlaying?.let { np ->
            if (np.track.isNotBlank()) {
                item {
                    GlassCard(
                        glowBorder = true,
                        accentColor = SolcoColors.AccentMint,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CoverArt(url = np.img, name = np.track, size = 50.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = SolcoColors.AccentMint, modifier = Modifier.size(14.dp))
                                    Text("IN ASCOLTO ORA", color = SolcoColors.AccentMint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Text(np.track, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(np.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Goals pair cards (Album + Singoli)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoalTileCard(
                    modifier = Modifier.weight(1f),
                    title = "Album",
                    icon = Icons.Rounded.Album,
                    accent = SolcoColors.AccentViolet,
                    balance = albumGoal.balance,
                    todayCount = albumGoal.todayCount,
                    paused = albumGoal.paused,
                    onClick = { onNavigate("reviews?tab=album") }
                )
                GoalTileCard(
                    modifier = Modifier.weight(1f),
                    title = "Singoli",
                    icon = Icons.Rounded.MusicNote,
                    accent = SolcoColors.AccentPink,
                    balance = singleGoal.balance,
                    todayCount = singleGoal.todayCount,
                    paused = singleGoal.paused,
                    onClick = { onNavigate("reviews?tab=track") }
                )
            }
        }

        // Releases highlight banner
        item {
            GlassCard(
                glowBorder = true,
                accentColor = SolcoColors.AccentCyan,
                onClick = { onNavigate("releases") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SolcoColors.CyanVioletGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nuove uscite", style = MaterialTheme.typography.titleLarge)
                        val msg = when {
                            state.releaseScan.scanning -> "Scansione: ${state.releaseScan.phase}"
                            todayReleasesCount > 0 -> "$todayReleasesCount uscite oggi · $recentReleasesCount negli ultimi 7 giorni"
                            recentReleasesCount > 0 -> "$recentReleasesCount uscite recenti dai tuoi artisti"
                            else -> "Controlla tutti i tuoi artisti (Spotify + Deezer)"
                        }
                        Text(msg, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SolcoColors.AccentCyan)
                }
            }
        }

        // Action notices
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
                    text = "${formatInt(unresolvedCount)} brani da collegare (tocca per collegarli)",
                    tint = SolcoColors.AccentCoral,
                    onClick = { onNavigate("pending") }
                )
            }
        }

        // Overview statistics grid
        item {
            Text("Riepilogo ascolti", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }

        item {
            val st = state.statsAll
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Ascolti",
                    value = formatInt(st.totalPlays),
                    sub = formatDurationShort(st.totalMs),
                    accent = SolcoColors.AccentCyan
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Artisti",
                    value = formatInt(st.artist.size),
                    sub = "unificati",
                    accent = SolcoColors.AccentViolet
                )
                StatMiniCard(
                    modifier = Modifier.weight(1f),
                    label = "Recensioni",
                    value = formatInt(activeReviews.size),
                    sub = "${formatInt(st.album.size)} album",
                    accent = SolcoColors.AccentPink
                )
            }
        }

        // Quick shortcuts
        item {
            Text("Accesso rapido", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.RateReview,
                    label = "Recensioni",
                    accent = SolcoColors.AccentViolet,
                    onClick = { onNavigate("reviews") }
                )
                ShortcutPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.EmojiEvents,
                    label = "Top",
                    accent = SolcoColors.AccentGold,
                    onClick = { onNavigate("top") }
                )
                ShortcutPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.SportsKabaddi,
                    label = "UwuFufu",
                    accent = SolcoColors.AccentPink,
                    onClick = { onNavigate("uwu") }
                )
            }
        }

        // Top 5 Artists Preview
        val topArtists = state.statsAll.artist.entries
            .sortedByDescending { it.value.plays }
            .take(5)
            .mapNotNull { (aid, st) -> state.model.artists[aid]?.let { it to st } }

        if (topArtists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Artisti più ascoltati", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onNavigate("artists") }) {
                        Text("Tutti", color = SolcoColors.AccentCyan)
                    }
                }
            }

            item {
                GlassCard(contentPadding = PaddingValues(10.dp)) {
                    topArtists.forEachIndexed { idx, (art, st) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigate("artist/${art.id}") }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (idx == 0) SolcoColors.AccentGold else SolcoColors.TextMuted,
                                modifier = Modifier.width(26.dp)
                            )
                            CoverArt(url = art.img, name = art.name, size = 44.dp, round = true)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(art.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatInt(st.plays)} ascolti · ${formatInt(st.songs)} brani",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SolcoColors.TextMuted)
                        }
                    }
                }
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
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (paused) {
                Icon(Icons.Rounded.PauseCircle, contentDescription = "In pausa", tint = SolcoColors.AccentAmber, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val sign = if (balance > 0) "+$balance" else balance.toString()
        Text(sign, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = balColor)
        Text(
            text = if (todayCount > 0) "Oggi: $todayCount" else "Ancora 0 oggi",
            style = MaterialTheme.typography.labelSmall,
            color = SolcoColors.TextSecondary
        )
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tint)
        }
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    accent: Color
) {
    GlassCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(24.dp))
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
        MoreMenuItem("settings", "Impostazioni", "Last.fm, Spotify, Obiettivi", Icons.Rounded.Settings, SolcoColors.AccentAmber)
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
