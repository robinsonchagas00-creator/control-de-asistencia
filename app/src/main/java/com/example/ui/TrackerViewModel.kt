package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AttendanceDatabase
import com.example.data.AttendanceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application.applicationContext,
        AttendanceDatabase::class.java,
        "attendance_tracker.db"
    ).fallbackToDestructiveMigration().build()

    private val attendanceDao = db.attendanceDao()

    // Expose all logs as a state flow
    val allEntries: StateFlow<List<AttendanceEntry>> = attendanceDao.getAllEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val prefs = application.getSharedPreferences("salary_tracker_prefs", Context.MODE_PRIVATE)

    // Corporate Profile Settings
    private val _fullName = MutableStateFlow(prefs.getString("full_name", "") ?: "")
    val fullName: StateFlow<String> = _fullName.asStateFlow()

    private val _baseSalary = MutableStateFlow(prefs.getFloat("base_salary", 250000f).toDouble())
    val baseSalary: StateFlow<Double> = _baseSalary.asStateFlow()

    private val _qualifiesForBonus = MutableStateFlow(prefs.getBoolean("qualifies_for_bonus", true))
    val qualifiesForBonus: StateFlow<Boolean> = _qualifiesForBonus.asStateFlow()

    private val _bonusAmount = MutableStateFlow(prefs.getFloat("bonus_amount", 40000f).toDouble())
    val bonusAmount: StateFlow<Double> = _bonusAmount.asStateFlow()

    private val _adelanto = MutableStateFlow(prefs.getFloat("adelanto", 0f).toDouble())
    val adelanto: StateFlow<Double> = _adelanto.asStateFlow()

    private val _scheduleImageUri = MutableStateFlow(prefs.getString("schedule_image_uri", "") ?: "")
    val scheduleImageUri: StateFlow<String> = _scheduleImageUri.asStateFlow()

    private val _idealSchedule = MutableStateFlow(
        prefs.getString("ideal_schedule", "Monday - Friday: 09:00 AM - 06:00 PM\nSaturday: Off\nSunday: Off") ?: ""
    )
    val idealSchedule: StateFlow<String> = _idealSchedule.asStateFlow()

    // Is App Initialized?
    private val _isInitialized = MutableStateFlow(prefs.getBoolean("is_initialized", false))
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun updateProfile(name: String, salary: Double, qualifiers: Boolean, bonus: Double) {
        prefs.edit()
            .putString("full_name", name)
            .putFloat("base_salary", salary.toFloat())
            .putBoolean("qualifies_for_bonus", qualifiers)
            .putFloat("bonus_amount", bonus.toFloat())
            .putBoolean("is_initialized", true)
            .apply()

        _fullName.value = name
        _baseSalary.value = salary
        _qualifiesForBonus.value = qualifiers
        _bonusAmount.value = bonus
        _isInitialized.value = true
    }

    fun updateIdealSchedule(scheduleText: String) {
        prefs.edit().putString("ideal_schedule", scheduleText).apply()
        _idealSchedule.value = scheduleText
    }

    fun updateAdelanto(amount: Double) {
        prefs.edit().putFloat("adelanto", amount.toFloat()).apply()
        _adelanto.value = amount
    }

    fun updateScheduleImageUri(uri: String) {
        prefs.edit().putString("schedule_image_uri", uri).apply()
        _scheduleImageUri.value = uri
    }

    fun addLog(date: String, type: String, time: String, lateness: Int, notes: String) {
        viewModelScope.launch {
            val entry = AttendanceEntry(
                date = date,
                type = type,
                time = time,
                latenessMinutes = if (type == "In" || type == "Falta") lateness else 0,
                notes = notes
            )
            attendanceDao.insertEntry(entry)
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            attendanceDao.deleteEntryById(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            attendanceDao.clearAll()
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            attendanceDao.clearAll()
            prefs.edit().clear().apply()
            _fullName.value = ""
            _baseSalary.value = 250000.0
            _qualifiesForBonus.value = true
            _bonusAmount.value = 40000.0
            _adelanto.value = 0.0
            _scheduleImageUri.value = ""
            _idealSchedule.value = "Monday - Friday: 09:00 AM - 06:00 PM\nSaturday: Off\nSunday: Off"
            _isInitialized.value = false
        }
    }

    // Helper to extract day of month
    fun getDayFromDate(dateStr: String): Int {
        val parts = dateStr.split("-")
        if (parts.size >= 3) {
            return parts[2].toIntOrNull() ?: 1
        }
        return 1
    }
}
