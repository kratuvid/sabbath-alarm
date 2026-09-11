package com.mns.suneventalarm.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mns.suneventalarm.MainActivity
import com.mns.suneventalarm.data.AlarmRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID") ?: return
        val useFullScreen = intent.getBooleanExtra("USE_FULL_SCREEN", false)
        
        val repository = AlarmRepository(context)
        val alarm = repository.getAlarms().find { it.id == alarmId } ?: return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sun_alarm_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sun Events",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sabbath Event Alarm")
            .setContentText("${alarm.eventType} event is happening now!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

        if (useFullScreen) {
            val fullScreenIntent = Intent(context, com.mns.suneventalarm.FullScreenAlarmActivity::class.java).apply {
                putExtra("ALARM_ID", alarmId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, alarmId.hashCode(), fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        notificationManager.notify(alarmId.hashCode(), builder.build())

        // Reschedule alarm for next occurrence
        val scheduler = AlarmScheduler(context)
        scheduler.scheduleAlarm(alarm)

        val pendingResult = goAsync()
        GlobalScope.launch {
            try {
                com.mns.suneventalarm.widget.NextAlarmWidget().updateAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
