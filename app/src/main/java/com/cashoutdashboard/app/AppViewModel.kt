package com.cashoutdashboard.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashoutdashboard.app.data.AppSettings
import com.cashoutdashboard.app.data.SettingsStore
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.data.ShiftRepository
import com.cashoutdashboard.app.ocr.Field
import com.cashoutdashboard.app.ocr.Issue
import com.cashoutdashboard.app.ocr.ReceiptScanner
import com.cashoutdashboard.app.ocr.Validation
import com.cashoutdashboard.app.ocr.isBlank
import com.cashoutdashboard.app.ocr.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class ScanStatus { PROCESSING, READY, FAILED }

/** A photo waiting to be transcribed and/or reviewed before it becomes a saved shift. */
data class ScanItem(
    val id: String = UUID.randomUUID().toString(),
    val photoPath: String?,
    val status: ScanStatus,
    val draft: Shift? = null,
    /** Fields the scanner couldn't read; highlighted until the user fills them in. */
    val missing: Set<Field> = emptySet(),
    val notes: List<String> = emptyList(),
    val error: String? = null,
    val duplicate: Boolean = false,
) {
    val issues: List<Issue> get() = draft?.let { Validation.check(it) }.orEmpty()

    val unread: List<Field> get() = draft?.let { d -> missing.filter { d.isBlank(it) } }.orEmpty()

    /** Ready to save without a human look: everything was read and every cross-check passes. */
    val isClean: Boolean
        get() = status == ScanStatus.READY && draft != null && issues.isEmpty() && notes.isEmpty() && !duplicate && unread.isEmpty()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ShiftRepository(app)
    private val settingsStore = SettingsStore(app)
    private val scanner = ReceiptScanner(app)

    val shifts: StateFlow<List<Shift>> = repo.shifts
    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _queue = MutableStateFlow<List<ScanItem>>(emptyList())
    val queue: StateFlow<List<ScanItem>> = _queue.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private val scanLimit = Semaphore(2)

    init {
        viewModelScope.launch { repo.load() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun toast(msg: String) { _messages.value = msg }
    fun consumeMessage() { _messages.value = null }

    fun shift(id: String): Shift? = shifts.value.firstOrNull { it.id == id }

    private val sources = mutableMapOf<String, Uri>()

    /** Queue photos for transcription. Each is copied into app storage first so the source can go away. */
    fun scan(uris: List<Uri>) {
        for (uri in uris) {
            val item = ScanItem(photoPath = null, status = ScanStatus.PROCESSING)
            sources[item.id] = uri
            _queue.update { it + item }
            launchScan(item.id, uri)
        }
    }

    fun retry(itemId: String) {
        val uri = sources[itemId] ?: return
        _queue.update { list -> list.map { if (it.id == itemId) it.copy(status = ScanStatus.PROCESSING, error = null) else it } }
        launchScan(itemId, uri)
    }

    private fun launchScan(id: String, uri: Uri) {
        viewModelScope.launch {
            val result = scanLimit.withPermit { runScan(uri, id) }
            _queue.update { list -> list.map { if (it.id == id) result else it } }
        }
    }

    private suspend fun runScan(uri: Uri, id: String): ScanItem = withContext(Dispatchers.Default) {
        var photo: File? = null
        try {
            photo = scanner.importPhoto(uri, File(repo.photoDir, "$id.jpg"))
            val result: ScanResult = scanner.scan(photo)
            val draft = result.shift.copy(photoPath = photo.absolutePath)
            val dup = repo.findDuplicate(draft)
            ScanItem(
                id = id, photoPath = photo.absolutePath, status = ScanStatus.READY, draft = draft,
                missing = result.missing, notes = result.notes, duplicate = dup != null,
            )
        } catch (e: Exception) {
            ScanItem(
                id = id, photoPath = photo?.absolutePath, status = ScanStatus.FAILED,
                draft = Shift(date = LocalDate.now().toString(), photoPath = photo?.absolutePath, source = "manual"),
                missing = Field.entries.toSet(),
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    fun newManualEntry(): String {
        val item = ScanItem(
            photoPath = null, status = ScanStatus.READY, draft = Shift(date = LocalDate.now().toString()),
        )
        _queue.update { it + item }
        return item.id
    }

    fun discard(itemId: String) {
        val item = _queue.value.firstOrNull { it.id == itemId } ?: return
        _queue.update { list -> list.filterNot { it.id == itemId } }
        item.photoPath?.let { path ->
            if (shifts.value.none { it.photoPath == path }) File(path).delete()
        }
    }

    /** Drops every queued scan that matches a shift already saved (common when bulk-importing). */
    fun discardDuplicates(): Int {
        val dups = _queue.value.filter { it.duplicate }
        dups.forEach { discard(it.id) }
        return dups.size
    }

    /** Keeps in-progress edits on the queued item so switching between scans doesn't lose them. */
    fun updateDraft(itemId: String, shift: Shift) {
        _queue.update { list ->
            list.map { if (it.id == itemId) it.copy(draft = shift, duplicate = repo.findDuplicate(shift) != null) else it }
        }
    }

    fun saveDraft(itemId: String, shift: Shift) {
        viewModelScope.launch {
            repo.upsert(shift)
            _queue.update { list -> list.filterNot { it.id == itemId } }
        }
    }

    /** Saves every queued scan that passed every check; returns how many were saved. */
    fun saveAllClean(): Int {
        val clean = _queue.value.filter { it.isClean }
        viewModelScope.launch {
            clean.forEach { repo.upsert(it.draft!!) }
            val ids = clean.map { it.id }.toSet()
            _queue.update { list -> list.filterNot { it.id in ids } }
        }
        return clean.size
    }

    fun saveShift(shift: Shift) = viewModelScope.launch { repo.upsert(shift) }
    fun deleteShift(id: String) = viewModelScope.launch { repo.delete(id) }
    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    fun exportCsv(uri: Uri) = writeTo(uri) { repo.exportCsv() }
    fun exportBackup(uri: Uri) = writeTo(uri) { repo.exportJson() }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            repo.import(text)
        }.onSuccess { toast("Imported $it shifts") }
            .onFailure { toast("Import failed: ${it.message}") }
    }

    private fun writeTo(uri: Uri, content: () -> String) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(content()) }
            }
        }.onSuccess { toast("Exported") }
            .onFailure { toast("Export failed: ${it.message}") }
    }
}
