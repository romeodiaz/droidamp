package com.droidamp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()
    
    private fun loadHistory(): List<String> {
        val historySet = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        val orderJson = prefs.getString(KEY_ORDER, "") ?: ""
        
        return if (orderJson.isNotEmpty()) {
            orderJson.split(SEPARATOR).filter { it in historySet }
        } else {
            historySet.toList()
        }
    }
    
    fun addSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        
        val current = _history.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        
        val limited = current.take(MAX_HISTORY_SIZE)
        
        prefs.edit()
            .putStringSet(KEY_HISTORY, limited.toSet())
            .putString(KEY_ORDER, limited.joinToString(SEPARATOR))
            .apply()
        
        _history.value = limited
    }
    
    fun removeSearch(query: String) {
        val current = _history.value.toMutableList()
        current.remove(query)
        
        prefs.edit()
            .putStringSet(KEY_HISTORY, current.toSet())
            .putString(KEY_ORDER, current.joinToString(SEPARATOR))
            .apply()
        
        _history.value = current
    }
    
    fun clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_ORDER)
            .apply()
        
        _history.value = emptyList()
    }
    
    companion object {
        private const val PREFS_NAME = "search_history"
        private const val KEY_HISTORY = "history"
        private const val KEY_ORDER = "order"
        private const val SEPARATOR = "\u001F"
        private const val MAX_HISTORY_SIZE = 20
    }
}
