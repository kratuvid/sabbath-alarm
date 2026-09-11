package com.mns.suneventalarm.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.GlanceTheme
import com.mns.suneventalarm.data.AlarmRepository
import com.mns.suneventalarm.scheduler.AlarmScheduler
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class NextAlarmWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = AlarmRepository(context)
        val scheduler = AlarmScheduler(context)
        
        val alarms = repository.getAlarms().filter { it.isEnabled }
        var nextAlarmText = "No active alarms"
        var nextAlarmTime = ""
        
        if (alarms.isNotEmpty()) {
            val upcoming = alarms.mapNotNull { alarm ->
                scheduler.calculateNextTriggerTime(alarm)?.let { time -> alarm to time }
            }.minByOrNull { it.second }
            
            if (upcoming != null) {
                val (alarm, time) = upcoming
                val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                val titleCaseEvent = alarm.eventType.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.titlecase() } }
                nextAlarmText = "$titleCaseEvent ${if (alarm.offsetMinutes != 0) "(${alarm.offsetMinutes}m)" else ""}"
                nextAlarmTime = "${time.format(dateFormatter)} at ${time.format(timeFormatter)}"
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(nextAlarmText, nextAlarmTime)
            }
        }
    }

    @Composable
    private fun WidgetContent(title: String, time: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Next Sabbath Event",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = title,
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Medium)
            )
            if (time.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = time,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}
