package com.mns.suneventalarm.util

import com.mns.suneventalarm.data.SunEventType
import org.shredzone.commons.suncalc.SunTimes
import java.time.ZonedDateTime

object SunCalcHelper {
    fun getNextEventTime(lat: Double, lng: Double, eventType: SunEventType, date: ZonedDateTime): ZonedDateTime? {
        val times = SunTimes.compute()
            .on(date)
            .at(lat, lng)
            .execute()

        return when (eventType) {
            SunEventType.SUNRISE -> times.rise
            SunEventType.SUNSET -> times.set
            SunEventType.SUN_TRANSIT -> times.noon
        }
    }
}
