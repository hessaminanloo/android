package com.hess.meetmate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.hess.meetmate.data.MeetMateDatabase
import com.hess.meetmate.data.MeetingEntity
import com.hess.meetmate.data.MeetingRepository
import com.hess.meetmate.domain.*
import com.hess.meetmate.integration.WhisperModelInfo
import com.hess.meetmate.integration.WhisperModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

enum class MeetingPhase { SETUP, LIVE, PAUSED, COMPLETE }
data class MeetingDraft(val title: String = "Untitled meeting", val participants: String = "", val notes: String = "", val agents: List<AiAgent> = AgentCatalog.all.take(2))

class MeetMateViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, MeetMateDatabase::class.java, "meetmate.db").build()
    private val repo = MeetingRepository(db.meetings())
    val meetings = repo.observeMeetings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _draft = MutableStateFlow(MeetingDraft()); val draft: StateFlow<MeetingDraft> = _draft.asStateFlow()
    private val _phase = MutableStateFlow(MeetingPhase.SETUP); val phase = _phase.asStateFlow()
    private val _transcript = MutableStateFlow(""); val transcript = _transcript.asStateFlow()
    private val _partial = MutableStateFlow(""); val partial = _partial.asStateFlow()
    private val _elapsed = MutableStateFlow(0L); val elapsed = _elapsed.asStateFlow()
    private val _engine = MutableStateFlow(SpeechEngine.WHISPER); val engine = _engine.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    private val _analysis = MutableStateFlow<AnalysisResult?>(null); val analysis = _analysis.asStateFlow()
    private val modelManager = WhisperModelManager(app)
    private val _models = MutableStateFlow(modelManager.snapshot()); val models: StateFlow<List<WhisperModelInfo>> = _models.asStateFlow()
    private val _offlineMode = MutableStateFlow(true); val offlineMode = _offlineMode.asStateFlow()
    private val recorder = MeetingRecorder(app)
    private fun primarySpeechEngine(): SpeechToTextEngine = WhisperEngine(app)
    private var speech: SpeechToTextEngine? = null
    private var startedAt = 0L

    init { viewModelScope.launch { while (isActive) { _models.value = modelManager.snapshot(); delay(1000) } } }

    fun updateDraft(title: String? = null, participants: String? = null, notes: String? = null) { _draft.value = _draft.value.copy(title = title ?: _draft.value.title, participants = participants ?: _draft.value.participants, notes = notes ?: _draft.value.notes) }
    fun toggleAgent(id: String) { _draft.value = _draft.value.copy(agents = _draft.value.agents.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }) }
    fun setEngine(value: SpeechEngine) { _engine.value = value }
    fun setOfflineMode(value: Boolean) { _offlineMode.value = value }
    fun downloadModel(id: String) { runCatching { modelManager.enqueueDownload(id) }.onFailure { _error.value = it.message ?: "Unable to start download" } }
    fun deleteModel(id: String) { runCatching { modelManager.delete(id); _models.value = modelManager.snapshot() }.onFailure { _error.value = it.message ?: "Unable to delete model" } }
    fun selectModel(id: String) { runCatching { modelManager.select(id); _models.value = modelManager.snapshot() }.onFailure { _error.value = it.message ?: "Download this model before selecting it" } }
    fun clearError() { _error.value = null }
    fun tick() { if (_phase.value == MeetingPhase.LIVE) _elapsed.value = ((System.currentTimeMillis() - startedAt) / 1000L) }
    fun startMeeting() {
        _phase.value = MeetingPhase.LIVE; startedAt = System.currentTimeMillis() - (_elapsed.value * 1000); recorder.start()
        startSpeech()
    }
    fun pauseMeeting() { _phase.value = MeetingPhase.PAUSED; speech?.stop(); recorder.pause() }
    fun resumeMeeting() { _phase.value = MeetingPhase.LIVE; startedAt = System.currentTimeMillis() - (_elapsed.value * 1000); recorder.resume(); startSpeech() }
    private fun startSpeech() {
        val finalResult: (String) -> Unit = { final -> _transcript.value += if (_transcript.value.isBlank()) final else "\n$final"; _partial.value = "" }
        val partialResult: (String) -> Unit = { _partial.value = it }
        val errorResult: (String) -> Unit = { message -> _error.value = message }
        speech = primarySpeechEngine().also { it.start(partialResult, finalResult, errorResult) }
    }
    fun stopMeeting() {
        speech?.stop(); recorder.stop(); _phase.value = MeetingPhase.COMPLETE
        viewModelScope.launch {
            _analysis.value = LocalMeetingProvider().analyze(_transcript.value, _draft.value.agents.filter { it.enabled }.map { it.id })
            repo.save(MeetingEntity(title = _draft.value.title, participants = _draft.value.participants, notes = _draft.value.notes, transcript = _transcript.value, summary = _analysis.value?.summary.orEmpty(), tasks = _analysis.value?.tasks?.joinToString("\n").orEmpty(), durationSeconds = _elapsed.value, audioPath = recorder.currentPath, agents = _draft.value.agents.filter { it.enabled }.joinToString { it.name }))
        }
    }
    fun reset() { _draft.value = MeetingDraft(); _phase.value = MeetingPhase.SETUP; _transcript.value = ""; _partial.value = ""; _elapsed.value = 0; _error.value = null; _analysis.value = null }
    fun openMeeting(meeting: MeetingEntity) { _draft.value = MeetingDraft(meeting.title, meeting.participants, meeting.notes); _transcript.value = meeting.transcript; _elapsed.value = meeting.durationSeconds; _phase.value = MeetingPhase.COMPLETE }
    override fun onCleared() { speech?.stop(); db.close(); super.onCleared() }
}
