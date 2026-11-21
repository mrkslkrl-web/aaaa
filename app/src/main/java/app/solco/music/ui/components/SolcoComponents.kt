package app.solco.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solco.music.data.model.Mood
import app.solco.music.data.model.ReviewRecord
import app.solco.music.data.model.TimePeriod
import app.solco.music.data.model.TrackReviewEntry
import app.solco.music.domain.MonthlyBucket
import app.solco.music.domain.RatingEngine
import app.solco.music.ui.theme.SolcoColors
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private val itNumFmt: NumberFormat = NumberFormat.getIntegerInstance(Locale.ITALY)

fun formatInt(n: Long): String = itNumFmt.format(n)
fun formatInt(n: Int): String = itNumFmt.format(n)

fun formatDurationShort(ms: Long): String {
    if (ms <= 0L) return "0 min"
    val totalMin = ms / 60_000L
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return when {
        hours >= 24 -> "${hours / 24}g ${hours % 24}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

fun formatTrackMs(ms: Long): String {
    if (ms <= 0L) return ""
    val s = ms / 1000L
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatDateIt(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""
    return runCatching {
        val d = LocalDate.parse(isoDate.take(10))
        d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY))
    }.getOrDefault(isoDate)
}

fun formatRelativeSec(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    val diff = (System.currentTimeMillis() / 1000L) - epochSec
    return when {
        diff < 60 -> "adesso"
        diff < 3600 -> "${diff / 60} min fa"
        diff < 86400 -> "${diff / 3600} ore fa"
        diff < 7 * 86400 -> "${diff / 86400}g fa"
        else -> Instant.ofEpochSecond(epochSec)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALY))
    }
}

/**
 * Ambient luminous multi-orb background that gives Solco depth and vibrant color without hurting legibility.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    primaryGlow: Color = SolcoColors.AccentViolet,
    secondaryGlow: Color = SolcoColors.AccentCyan,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SolcoColors.BgDeep)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.06f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.15f, size.height * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.90f, size.height * 0.25f),
                    radius = size.width * 0.70f
                ),
                radius = size.width * 0.70f,
                center = Offset(size.width * 0.90f, size.height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SolcoColors.AccentPink.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 0.50f, size.height * 0.92f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.50f, size.height * 0.92f)
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowBorder: Boolean = false,
    accentColor: Color = SolcoColors.AccentViolet,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val borderBrush = if (glowBorder) {
        Brush.linearGradient(listOf(accentColor.copy(alpha = 0.65f), SolcoColors.AccentCyan.copy(alpha = 0.35f)))
    } else {
        Brush.linearGradient(listOf(SolcoColors.Border.copy(alpha = 0.95f), SolcoColors.Border.copy(alpha = 0.45f)))
    }
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(BorderStroke(1.dp, borderBrush), shape),
        shape = shape,
        color = SolcoColors.CardBg.copy(alpha = 0.88f)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun CoverArt(
    url: String?,
    name: String,
    size: Dp = 54.dp,
    round: Boolean = false,
    glow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape: Shape = if (round) CircleShape else RoundedCornerShape((size.value * 0.22f).coerceIn(10f, 22f).dp)
    val palette = listOf(
        listOf(Color(0xFF7B61FF), Color(0xFF38D9F5)),
        listOf(Color(0xFFFF5C8D), Color(0xFFFF9F43)),
        listOf(Color(0xFF2EC4B6), Color(0xFF5B8CFF)),
        listOf(Color(0xFFB565F7), Color(0xFFFF5C8D))
    )
    val grad = palette[abs(name.hashCode()) % palette.size]
    val initials = name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (glow) Modifier.shadow(18.dp, shape, ambientColor = SolcoColors.AccentViolet, spotColor = SolcoColors.AccentPink)
                else Modifier
            )
            .clip(shape)
            .background(Brush.linearGradient(grad))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifEmpty { "♪" },
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.34f).coerceIn(12f, 34f).sp
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ScoreBadge(
    score: Double?,
    mood: Mood?,
    large: Boolean = false,
    modifier: Modifier = Modifier
) {
    when {
        mood != null -> {
            val c = Color(mood.colorHex)
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(50))
                    .background(c.copy(alpha = 0.20f))
                    .border(1.dp, c.copy(alpha = 0.8f), RoundedCornerShape(50))
                    .padding(horizontal = if (large) 12.dp else 9.dp, vertical = if (large) 6.dp else 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(if (large) 9.dp else 7.dp).clip(CircleShape).background(c))
                    Text(
                        text = if (large) mood.label else mood.value.toString(),
                        color = c,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (large) 13.sp else 12.sp
                    )
                }
            }
        }
        score != null -> {
            val tint = when {
                score >= 8.5 -> SolcoColors.AccentCyan
                score >= 7.0 -> SolcoColors.AccentMint
                score >= 5.5 -> SolcoColors.AccentAmber
                else -> SolcoColors.AccentCoral
            }
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.18f))
                    .border(1.dp, tint.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 7.dp else 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = RatingEngine.formatScore(score),
                    color = tint,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (large) 17.sp else 13.sp
                )
            }
        }
    }
}

@Composable
fun MoodSelectorRow(
    selectedMood: Int?,
    onSelect: (Int?) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Mood.entries.forEach { mood ->
            val selected = selectedMood == mood.value
            val color = Color(mood.colorHex)
            val bg by animateColorAsState(
                if (selected) color else color.copy(alpha = 0.12f),
                label = "moodBg"
            )
            val size by animateDpAsState(
                targetValue = if (compact) (if (selected) 34.dp else 30.dp) else (if (selected) 44.dp else 40.dp),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "moodSz"
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(bg)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color.White.copy(alpha = 0.9f) else color.copy(alpha = 0.55f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(if (selected) null else mood.value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mood.value.toString(),
                    color = if (selected) Color(0xFF07070D) else color,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 12.sp else 15.sp
                )
            }
        }
    }
}

@Composable
fun PeriodChipRow(
    selectedPeriod: String,
    years: List<Int>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePeriod.entries.forEach { p ->
            val sel = selectedPeriod == p.key
            FilterChip(
                selected = sel,
                onClick = { onSelect(p.key) },
                label = { Text(p.label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SolcoColors.AccentViolet.copy(alpha = 0.25f),
                    selectedLabelColor = SolcoColors.TextPrimary,
                    containerColor = SolcoColors.CardBg,
                    labelColor = SolcoColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = sel,
                    borderColor = SolcoColors.Border,
                    selectedBorderColor = SolcoColors.AccentViolet
                )
            )
        }
        years.take(6).forEach { y ->
            val key = "year:$y"
            val sel = selectedPeriod == key
            FilterChip(
                selected = sel,
                onClick = { onSelect(key) },
                label = { Text(y.toString(), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SolcoColors.AccentCyan.copy(alpha = 0.22f),
                    selectedLabelColor = SolcoColors.TextPrimary,
                    containerColor = SolcoColors.CardBg,
                    labelColor = SolcoColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = sel,
                    borderColor = SolcoColors.Border,
                    selectedBorderColor = SolcoColors.AccentCyan
                )
            )
        }
    }
}

@Composable
fun MonthlyBarChart(
    buckets: List<MonthlyBucket>,
    modifier: Modifier = Modifier
) {
    val maxPlays = (buckets.maxOfOrNull { it.plays } ?: 1).coerceAtLeast(1)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEach { b ->
            val ratio by animateFloatAsState(
                targetValue = (b.plays.toFloat() / maxPlays.toFloat()).coerceIn(0f, 1f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "bar"
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (b.plays > 0) {
                    Text(
                        text = b.plays.toString(),
                        fontSize = 9.sp,
                        color = SolcoColors.TextMuted,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height((64f * ratio).dp.coerceAtLeast(if (b.plays > 0) 6.dp else 3.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(
                            if (b.plays > 0) Brush.verticalGradient(listOf(SolcoColors.AccentCyan, SolcoColors.AccentViolet))
                            else Brush.verticalGradient(listOf(SolcoColors.Border, SolcoColors.Border))
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = b.label.take(1),
                    fontSize = 10.sp,
                    color = SolcoColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SolcoColors.AccentViolet.copy(alpha = 0.14f))
                .border(1.dp, SolcoColors.AccentViolet.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SolcoColors.AccentViolet, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentViolet)
            ) {
                Text(actionLabel, color = Color(0xFF07070D), fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class AlbumTrackForReview(
    val songId: String,
    val num: Int,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditorSheet(
    title: String,
    entityType: String,
    existing: ReviewRecord?,
    albumTracks: List<AlbumTrackForReview> = emptyList(),
    showCalculatedScore: Boolean = true,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (rating: Double?, mood: Int?, text: String, day: String, tracks: Map<String, TrackReviewEntry>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember {
        mutableStateOf(
            when {
                existing?.rating != null -> "rating"
                existing?.mood != null -> "mood"
                entityType == "album" && albumTracks.isNotEmpty() -> "none"
                else -> "mood"
            }
        )
    }
    var rating by remember { mutableDoubleStateOf(existing?.rating ?: 7.5) }
    var mood by remember { mutableStateOf(existing?.mood) }
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    var day by remember { mutableStateOf(existing?.day?.ifBlank { LocalDate.now().toString() } ?: LocalDate.now().toString()) }

    val trackReviews = remember {
        mutableStateMapOf<String, TrackReviewEntry>().apply {
            putAll(RatingEngine.parseTrackReviews(existing?.tracksJson))
        }
    }
    val expandedCommentIds = remember { mutableStateMapOf<String, Boolean>() }

    val calcScore = RatingEngine.computeFromTracks(trackReviews)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SolcoColors.BgSurface,
        contentColor = SolcoColors.TextPrimary
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Recensione", style = MaterialTheme.typography.labelMedium, color = SolcoColors.AccentViolet)
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (existing != null && existing.del == 0 && onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Elimina", tint = SolcoColors.AccentCoral)
                        }
                    }
                }
            }

            // Mode switcher
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mood" to "Giudizio", "rating" to "Voto 0–10", "none" to "Solo brani/testo").forEach { (k, lbl) ->
                        val sel = mode == k
                        FilterChip(
                            selected = sel,
                            onClick = { mode = k },
                            label = { Text(lbl) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SolcoColors.AccentViolet.copy(alpha = 0.25f),
                                selectedLabelColor = SolcoColors.TextPrimary
                            )
                        )
                    }
                }
            }

            if (mode == "mood") {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        MoodSelectorRow(selectedMood = mood, onSelect = { mood = it })
                        Spacer(Modifier.height(6.dp))
                        val mObj = Mood.fromValue(mood)
                        Text(
                            text = mObj?.label ?: "Tocca un giudizio (1 = Che schifo · 5 = Spettacolare)",
                            color = mObj?.let { Color(it.colorHex) } ?: SolcoColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (mode == "rating") {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = RatingEngine.formatScore(rating),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SolcoColors.AccentCyan
                        )
                        Slider(
                            value = rating.toFloat(),
                            onValueChange = { rating = round(it * 2.0) / 2.0 },
                            valueRange = 0f..10f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = SolcoColors.AccentCyan,
                                activeTrackColor = SolcoColors.AccentViolet
                            )
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (entityType == "album") "Pensieri sull'album" else "Cosa ne pensi?") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolcoColors.AccentViolet,
                        unfocusedBorderColor = SolcoColors.Border
                    )
                )
            }

            if (albumTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Brano per brano", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Media pesata sulla lunghezza (album corti leggermente sotto a quelli lunghi)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (calcScore != null && showCalculatedScore) {
                            ScoreBadge(score = calcScore.score, mood = null, large = true)
                        }
                    }
                }

                items(albumTracks, key = { it.songId }) { tr ->
                    val cur = trackReviews[tr.songId] ?: TrackReviewEntry()
                    val showComment = expandedCommentIds[tr.songId] == true || !cur.text.isNullOrBlank()
                    val moodObj = Mood.fromValue(cur.mood)
                    GlassCard(
                        contentPadding = PaddingValues(10.dp),
                        glowBorder = moodObj != null,
                        accentColor = moodObj?.let { Color(it.colorHex) } ?: SolcoColors.Border
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
                                Text(
                                    text = tr.num.toString(),
                                    color = SolcoColors.TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tr.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    if (moodObj != null) {
                                        Text(moodObj.label, color = Color(moodObj.colorHex), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            IconButton(
                                onClick = { expandedCommentIds[tr.songId] = !showComment },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "Commento",
                                    tint = if (showComment) SolcoColors.AccentCyan else SolcoColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        MoodSelectorRow(
                            selectedMood = cur.mood,
                            compact = true,
                            onSelect = { newMood ->
                                val next = cur.copy(mood = newMood)
                                if (next.mood == null && next.text.isNullOrBlank()) trackReviews.remove(tr.songId)
                                else trackReviews[tr.songId] = next
                            }
                        )
                        if (showComment) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = cur.text.orEmpty(),
                                onValueChange = { txt ->
                                    val next = cur.copy(text = txt.ifBlank { null })
                                    if (next.mood == null && next.text == null) trackReviews.remove(tr.songId)
                                    else trackReviews[tr.songId] = next
                                },
                                placeholder = { Text("Nota sul brano…", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SolcoColors.AccentCyan,
                                    unfocusedBorderColor = SolcoColors.Border
                                )
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = { Text("Data (AAAA-MM-GG)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolcoColors.AccentViolet,
                        unfocusedBorderColor = SolcoColors.Border
                    )
                )
            }

            item {
                Button(
                    onClick = {
                        onSave(
                            if (mode == "rating") rating else null,
                            if (mode == "mood") mood else null,
                            text.trim(),
                            day.ifBlank { LocalDate.now().toString() },
                            trackReviews.toMap()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SolcoColors.AccentViolet)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF07070D))
                    Spacer(Modifier.width(8.dp))
                    Text("Salva recensione", color = Color(0xFF07070D), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

fun iconForKind(kind: String): ImageVector = when (kind) {
    "artist" -> Icons.Rounded.Person
    "album" -> Icons.Rounded.Album
    else -> Icons.Rounded.MusicNote
}
