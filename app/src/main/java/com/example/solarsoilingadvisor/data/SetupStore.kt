package com.example.solarsoilingadvisor.data

import android.content.Context

/** The basic, non-technical profile the user gives us once, on first launch. */
data class SetupData(
    val systemName: String = "My Solar System",
    val panelCount: Int = 1,
    val location: String = "",
)

/**
 * Tiny SharedPreferences-backed store for the user's setup. Persists across
 * launches so the app never falls back to silent defaults the user didn't choose.
 */
class SetupStore(context: Context) {
    private val prefs = context.getSharedPreferences("solar_setup", Context.MODE_PRIVATE)

    val isOnboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)

    fun load(): SetupData = SetupData(
        systemName = prefs.getString(KEY_NAME, "My Solar System") ?: "My Solar System",
        panelCount = prefs.getInt(KEY_PANELS, 1),
        location = prefs.getString(KEY_LOCATION, "") ?: "",
    )

    fun save(data: SetupData) {
        prefs.edit()
            .putString(KEY_NAME, data.systemName)
            .putInt(KEY_PANELS, data.panelCount)
            .putString(KEY_LOCATION, data.location)
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_NAME = "system_name"
        const val KEY_PANELS = "panel_count"
        const val KEY_LOCATION = "location"
    }
}
