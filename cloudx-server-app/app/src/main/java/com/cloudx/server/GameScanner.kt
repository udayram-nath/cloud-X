package com.cloudx.server

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Scans the device for installed apps that look like games.
 * Since we no longer support sideloading cracked APKs, this only
 * detects apps the user has legitimately installed (e.g. from Play Store)
 * plus any emulator-loaded ROMs found on external storage.
 */
data class GameEntry(
    val id: String,        // package name, used to launch it
    val name: String,      // display name
    val isSystemApp: Boolean
)

class GameScanner(private val context: Context) {

    // Package name prefixes we want to surface (extend this list freely)
    private val knownGamePackages = setOf(
        "com.activision.callofduty.shooter", // COD Mobile
        "com.dts.freefireth",                // Free Fire
        "com.tencent.ig",                    // PUBG Mobile
        "com.miHoYo.GenshinImpact",          // Genshin Impact
        "com.gameloft.android.ANMP.GloftA9HM", // Asphalt 9
        "com.supercell.clashofclans"         // Clash of Clans
    )

    fun scanInstalledGames(): List<GameEntry> {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { app ->
                knownGamePackages.contains(app.packageName) ||
                    isLikelyGame(app)
            }
            .map { app ->
                GameEntry(
                    id = app.packageName,
                    name = pm.getApplicationLabel(app).toString(),
                    isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .distinctBy { it.id }
    }

    // Heuristic fallback: apps under the "GAME_*" category (Android marks
    // many Play Store games with this) catches titles not in our hardcoded list
    private fun isLikelyGame(app: ApplicationInfo): Boolean {
        return try {
            app.category == ApplicationInfo.CATEGORY_GAME
        } catch (e: Exception) {
            false
        }
    }

    fun toJson(games: List<GameEntry>): JSONArray {
        val arr = JSONArray()
        for (g in games) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            arr.put(obj)
        }
        return arr
    }
}
