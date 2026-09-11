package com.mns.suneventalarm.data

import android.content.Context
import android.content.SharedPreferences

class LocationRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)

    fun saveLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putString("lat", lat.toString())
            .putString("lng", lng.toString())
            .apply()
    }

    fun getLocation(): Pair<Double, Double>? {
        val latStr = prefs.getString("lat", null)
        val lngStr = prefs.getString("lng", null)
        if (latStr != null && lngStr != null) {
            return Pair(latStr.toDouble(), lngStr.toDouble())
        }
        return null
    }
}
