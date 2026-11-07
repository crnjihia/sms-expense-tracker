package com.example.smsexpensertracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsexpensertracker.data.CategorisationRepository
import com.example.smsexpensertracker.ingestion.SmsIngestionRepository
import com.example.smsexpensertracker.parser.SmsParseResult
import com.example.smsexpensertracker.parser.SmsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of a manual import operation. */
data class ImportResult(val inserted: Int, val unparsed: Int)

@HiltViewModel
class ManualImportViewModel @Inject constructor(
    private val parser: SmsParser,
    private val repository: SmsIngestionRepository,
    private val categorisationRepository: CategorisationRepository
) : ViewModel() {

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult

    /**
     * Expected format: each line `sender|body` (pipe‑separated). Empty lines are ignored.
     */
    fun importMessages(raw: String) {
        viewModelScope.launch {
            var inserted = 0
            var unparsed = 0
            raw.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                val parts = trimmed.split("|", limit = 2)
                if (parts.size != 2) {
                    unparsed++
                    return@forEach
                }
                val sender = parts[0].trim()
                val body = parts[1].trim()
                when (val result = parser.parse(sender, body)) {
                    is SmsParseResult.Parsed -> {
                        repository.saveIfNotDuplicate(result.parsed)
                        inserted++
                    }
                    SmsParseResult.Unparsed -> unparsed++
                }
            }
            categorisationRepository.applyRules()
            _importResult.value = ImportResult(inserted, unparsed)
        }
    }
}
