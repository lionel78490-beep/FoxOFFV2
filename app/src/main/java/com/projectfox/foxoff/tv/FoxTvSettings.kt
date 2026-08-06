package com.projectfox.foxoff.tv

import android.content.Context

/**
 * Stockage minimal de la TV sélectionnée.
 */
object FoxTvSettings {

    private const val PREFS_NAME = "fox_tv_settings"
    private const val KEY_TV_IP = "selected_tv_ip"

    fun saveTvIp(
        context: Context,
        ip: String
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(KEY_TV_IP, ip.trim())
            .apply()
    }

    fun getTvIp(
        context: Context
    ): String? {
        return context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(KEY_TV_IP, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clear(
        context: Context
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_TV_IP)
            .apply()
    }
}