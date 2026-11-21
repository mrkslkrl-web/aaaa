package app.solco.music.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.solco.music.data.model.GoalsConfig
import app.solco.music.data.model.PeriodStats
import app.solco.music.data.model.ReleaseItem
import app.solco.music.data.model.TrackReviewEntry
import app.solco.music.data.model.UwuTournament
import app.solco.music.data.remote.MatchedTrackResult
import app.solco.music.data.repository.ImportSummary
import app.solco.music.data.repository.SolcoRepository
import app.solco.music.data.repository.SolcoUiSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SolcoViewModel(app: Application) : AndroidViewModel(app) {

    val repo = SolcoRepository(app.applicationContext)
    val state: StateFlow<SolcoUiSnapshot> = repo.state

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.boot()
        }
    }

    fun showToast(msg: String) {
        _toast.tryEmit(msg)
    }

    fun statsForPeriod(periodKey: String): PeriodStats = repo.statsForPeriod(periodKey)

    fun signIn(email: String, pass: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.signIn(email, pass) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Errore accesso") }
        }
    }

    fun signUp(email: String, pass: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { repo.signUp(email, pass) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Errore registrazione") }
        }
    }

    fun sendPasswordReset(email: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.sendPasswordReset(email) }
                .onSuccess { onResult("Email di ripristino inviata!") }
                .onFailure { onResult(it.message ?: "Errore invio email") }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            repo.syncAll(runMatcher = true)
            showToast("Sincronizzazione completata")
        }
    }

    fun runMatcherNow() {
        viewModelScope.launch {
            repo.runSmartMatcher(maxItems = 120)
            showToast("Collegamento automatico completato")
        }
    }

    fun syncRecentAuto() {
        viewModelScope.launch {
            repo.syncLastFmQuick()
        }
    }

    fun refreshArtistImagesNow() {
        viewModelScope.launch {
            showToast("Aggiorno foto profilo artisti (Spotify + Deezer)…")
            val (updated, checked) = repo.refreshArtistImages(force = true)
            if (updated > 0) {
                showToast("Aggiornate $updated foto profilo su $checked artisti!")
            } else if (checked > 0) {
                showToast("Controllati $checked artisti: foto già aggiornate!")
            } else {
                showToast("Nessun artista da aggiornare")
            }
        }
    }

    fun resolveUnknownTracksNow() {
        viewModelScope.launch {
            showToast("Risolvo brani e artisti senza nome…")
            repo.resolveStubTracksAndArtists()
            showToast("Catalogo aggiornato!")
        }
    }

    fun scanReleases(force: Boolean = true) {
        viewModelScope.launch {
            repo.scanReleases(force = force)
        }
    }

    fun setShowCalculatedAlbumScore(show: Boolean) {
        viewModelScope.launch {
            repo.setShowCalculatedAlbumScore(show)
        }
    }

    fun saveReview(
        entityType: String,
        entityId: String,
        rating: Double?,
        mood: Int?,
        text: String,
        day: String,
        tracks: Map<String, TrackReviewEntry>
    ) {
        viewModelScope.launch {
            repo.saveReview(entityType, entityId, rating, mood, text, day, tracks)
            showToast("Recensione salvata")
        }
    }

    fun deleteReview(id: String) {
        viewModelScope.launch {
            repo.deleteReview(id)
            showToast("Recensione eliminata")
        }
    }

    fun saveNote(entityType: String, entityId: String, text: String) {
        viewModelScope.launch {
            repo.saveNote(entityType, entityId, text)
            showToast("Nota salvata")
        }
    }

    fun toggleDiscOff(albumId: String, discNum: Int) {
        viewModelScope.launch {
            repo.toggleDiscOff(albumId, discNum)
        }
    }

    fun ensureAlbumLoaded(albumId: String) {
        viewModelScope.launch {
            repo.ensureAlbumLoaded(albumId)
        }
    }

    fun openReleaseAlbum(item: ReleaseItem, onOpenAlbumId: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.openOrCreateReleaseAlbum(item)
            if (id != null) onOpenAlbumId(id)
            else showToast("Impossibile aprire questo album: prova dal link esterno")
        }
    }

    fun toggleSavedRelease(item: ReleaseItem) {
        viewModelScope.launch {
            repo.toggleSavedRelease(item)
        }
    }

    fun setTopTier(kind: String, entityId: String, tier: Int?) {
        viewModelScope.launch {
            repo.setTopTier(kind, entityId, tier)
        }
    }

    fun saveTournament(t: UwuTournament) {
        viewModelScope.launch {
            repo.saveTournament(t)
        }
    }

    fun deleteTournament(id: String) {
        viewModelScope.launch {
            repo.deleteTournament(id)
            showToast("Torneo eliminato")
        }
    }

    fun ignoreUnresolved(nameId: String) {
        viewModelScope.launch {
            repo.ignoreUnresolvedName(nameId)
            showToast("Brano ignorato")
        }
    }

    fun linkUnresolved(nameId: String, match: MatchedTrackResult) {
        viewModelScope.launch {
            repo.manualLinkUnresolved(nameId, match)
            showToast("Collegato a ${match.title}")
        }
    }

    suspend fun searchCandidates(artist: String, track: String, album: String): List<MatchedTrackResult> =
        repo.searchTrackCandidates(artist, track, album)

    fun saveLastFm(user: String, key: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.saveLastFmConfig(user, key) }
                .onSuccess {
                    onDone(null)
                    showToast("Last.fm collegato (${it.name})")
                    repo.syncAll(runMatcher = true)
                }
                .onFailure { onDone(it.message ?: "Errore Last.fm") }
        }
    }

    fun saveSpotify(id: String, secret: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val err = repo.saveSpotifyConfig(id, secret)
            onDone(err)
            if (err == null) showToast("Credenziali Spotify salvate")
        }
    }

    fun saveGoals(goals: GoalsConfig) {
        viewModelScope.launch {
            repo.saveGoalsConfig(goals)
            showToast("Obiettivi salvati")
        }
    }

    fun toggleGoalsPause() {
        viewModelScope.launch {
            repo.toggleGoalsPause()
        }
    }

    fun importSpotifyHistory(uris: List<Uri>, onDone: (ImportSummary?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.importSpotifyFiles(uris) }
                .onSuccess { summary ->
                    onDone(summary, null)
                    showToast("Importati ${summary.keptPlays} ascolti!")
                    repo.runSmartMatcher(maxItems = 80)
                }
                .onFailure { onDone(null, it.message ?: "Errore importazione") }
        }
    }
}
