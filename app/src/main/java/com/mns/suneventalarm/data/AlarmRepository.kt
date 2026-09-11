package com.mns.suneventalarm.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

enum class SunEventType { SUNRISE, SUNSET, SUN_TRANSIT }

data class SunAlarm(
    val id: String = UUID.randomUUID().toString(),
    val eventType: SunEventType,
    val offsetMinutes: Int = 0, // Negative for before, positive for after
    val daysOfWeek: Set<Int> = emptySet(), // 1=Monday, 7=Sunday (ISO)
    val targetDateMillis: Long? = null, // For one-off alarms
    val isEnabled: Boolean = true,
    val useFullScreenIntent: Boolean = false
) {
    fun isDuplicateOf(other: SunAlarm): Boolean {
        return eventType == other.eventType &&
               offsetMinutes == other.offsetMinutes &&
               daysOfWeek == other.daysOfWeek &&
               targetDateMillis == other.targetDateMillis
    }
}

class AlarmRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAlarms(): List<SunAlarm> {
        val json = prefs.getString("alarms", null) ?: return emptyList()
        val type = object : TypeToken<List<SunAlarm>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveAlarms(alarms: List<SunAlarm>) {
        prefs.edit().putString("alarms", gson.toJson(alarms)).apply()
    }

    fun addAlarm(alarm: SunAlarm) {
        val alarms = getAlarms().toMutableList()
        alarms.add(alarm)
        saveAlarms(alarms)
    }

    fun updateAlarm(alarm: SunAlarm) {
        val alarms = getAlarms().toMutableList()
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = alarm
            saveAlarms(alarms)
        }
    }

    fun deleteAlarm(alarmId: String) {
        val alarms = getAlarms().filter { it.id != alarmId }
        saveAlarms(alarms)
    }
}
