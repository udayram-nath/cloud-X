package com.cloudx.server

import android.content.Context

/**
 * Simple local storage for the server's connection password.
 * The user sets this once in MainActivity on first launch.
 * Stored in plain SharedPreferences for now — fine for a personal/hobby
 * setup, but swap for EncryptedSharedPreferences before any real release.
 */
object ServerPrefs {
    private const val PREFS_NAME = "cloudx_server_prefs"
    private const val KEY_PASSWORD = "server_password"

    fun setPassword(context: Context, password: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getPassword(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PASSWORD, "cloudx123") ?: "cloudx123"
    }
}
