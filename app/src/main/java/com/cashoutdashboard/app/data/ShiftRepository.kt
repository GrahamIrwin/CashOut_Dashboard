package com.cashoutdashboard.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Backup(val version: Int = 1, val shifts: List<Shift>)

/** Stores all shifts in a single JSON file in app-private storage. */
class ShiftRepository(private val context: Context) {
    private val file = File(context.filesDir, "shifts.json")
    val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val mutex = Mutex()
    private val _shifts = MutableStateFlow<List<Shift>>(emptyList())
    val shifts: StateFlow<List<Shift>> = _shifts.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _shifts.value = if (file.exists()) {
                runCatching { json.decodeFromString<Backup>(file.readText()).shifts }.getOrElse { emptyList() }
            } else emptyList()
            _shifts.value = sorted(_shifts.value)
        }
    }

    suspend fun upsert(shift: Shift) = update { list -> list.filterNot { it.id == shift.id } + shift }

    suspend fun delete(id: String) {
        val shift = _shifts.value.firstOrNull { it.id == id }
        update { list -> list.filterNot { it.id == id } }
        shift?.photoPath?.let { withContext(Dispatchers.IO) { File(it).delete() } }
    }

    suspend fun deleteAll() {
        update { emptyList() }
        withContext(Dispatchers.IO) { photoDir.listFiles()?.forEach { it.delete() } }
    }

    /** Merge imported shifts; entries with an existing id are replaced. Returns count imported. */
    suspend fun import(backupJson: String): Int {
        val incoming = json.decodeFromString<Backup>(backupJson).shifts
        val ids = incoming.map { it.id }.toSet()
        update { list -> list.filterNot { it.id in ids } + incoming.map { it.copy(photoPath = null) } }
        return incoming.size
    }

    fun exportJson(): String = json.encodeToString(Backup(shifts = _shifts.value.map { it.copy(photoPath = null) }))

    fun exportCsv(): String {
        val header = listOf(
            "date", "day", "server", "open", "close", "hours", "sales", "tips", "tip_pct", "take_home",
            "food_sales", "lwb_sales", "covers", "avg_check", "checks", "payment_total", "net", "notes"
        )
        val rows = _shifts.value.map { s ->
            listOf(
                s.date, s.dayOfWeek.name, s.serverName ?: "", s.openTime ?: "", s.closeTime ?: "",
                s.hours?.let { "%.2f".format(it) } ?: "", "%.2f".format(s.sales), "%.2f".format(s.tips),
                s.tipPercent()?.let { "%.2f".format(it) } ?: "", s.takeHome?.let { "%.2f".format(it) } ?: "",
                s.foodSales?.toString() ?: "", s.lwbSales?.toString() ?: "",
                s.covers?.toString() ?: "", s.avgCheck?.toString() ?: "", s.checks?.toString() ?: "",
                s.paymentTotal?.toString() ?: "", s.net?.toString() ?: "", s.notes ?: "",
            ).joinToString(",") { csvEscape(it) }
        }
        return (listOf(header.joinToString(",")) + rows).joinToString("\n")
    }

    fun findDuplicate(shift: Shift): Shift? = _shifts.value.firstOrNull {
        it.id != shift.id && it.date == shift.date && it.openTime == shift.openTime &&
            kotlin.math.abs(it.sales - shift.sales) < 0.01
    }

    private suspend fun update(transform: (List<Shift>) -> List<Shift>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = sorted(transform(_shifts.value))
            val tmp = File(file.parentFile, "shifts.json.tmp")
            tmp.writeText(json.encodeToString(Backup(shifts = next)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            _shifts.value = next
        }
    }

    private fun sorted(list: List<Shift>) = list.sortedWith(compareByDescending<Shift> { it.date }.thenByDescending { it.openTime ?: "" })

    private fun csvEscape(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    }
}
