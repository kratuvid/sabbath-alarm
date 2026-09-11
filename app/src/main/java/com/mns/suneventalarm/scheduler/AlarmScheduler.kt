package com.mns.suneventalarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mns.suneventalarm.data.SunAlarm
import com.mns.suneventalarm.data.LocationRepository
import com.mns.suneventalarm.util.SunCalcHelper
import java.time.ZonedDateTime

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val locationRepository = LocationRepository(context)

    fun calculateNextTriggerTime(alarm: SunAlarm): ZonedDateTime? {
        val location = locationRepository.getLocation() ?: return null
        val now = ZonedDateTime.now()
        var eventTime: ZonedDateTime?
        
        if (alarm.targetDateMillis != null) {
            val targetDate = java.time.Instant.ofEpochMilli(alarm.targetDateMillis).atZone(java.time.ZoneId.systemDefault())
            eventTime = SunCalcHelper.getNextEventTime(location.first, location.second, alarm.eventType, targetDate)
            eventTime = eventTime?.plusMinutes(alarm.offsetMinutes.toLong())
            if (eventTime != null && eventTime.isBefore(now)) {
                return null
            }
        } else {
            eventTime = SunCalcHelper.getNextEventTime(location.first, location.second, alarm.eventType, now)
            if (eventTime != null) {
                eventTime = eventTime.plusMinutes(alarm.offsetMinutes.toLong())
                if (eventTime.isBefore(now)) {
                    eventTime = SunCalcHelper.getNextEventTime(location.first, location.second, alarm.eventType, now.plusDays(1))
                    eventTime = eventTime?.plusMinutes(alarm.offsetMinutes.toLong())
                }
            }
            if (eventTime != null && alarm.daysOfWeek.isNotEmpty()) {
                var daysAdvanced = 0
                while (!alarm.daysOfWeek.contains(eventTime!!.dayOfWeek.value) && daysAdvanced < 7) {
                    eventTime = SunCalcHelper.getNextEventTime(location.first, location.second, alarm.eventType, now.plusDays((daysAdvanced + 1).toLong()))
                    eventTime = eventTime?.plusMinutes(alarm.offsetMinutes.toLong())
                    daysAdvanced++
                }
            }
        }
        return eventTime
    }

    fun scheduleAlarm(alarm: SunAlarm) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return
        }

        val eventTime = calculateNextTriggerTime(alarm) ?: return

        val triggerTimeMs = eventTime!!.toInstant().toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("USE_FULL_SCREEN", alarm.useFullScreenIntent)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTimeMs, pendingIntent),
                pendingIntent
            )
            Log.d("AlarmScheduler", "Alarm scheduled for $eventTime")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Exact alarm permission missing", e)
        }
    }

    fun cancelAlarm(alarmId: String) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
