package com.sounote.app.data

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GitHub'daki en yeni sürüm. [build] derleme numarasıdır (uygulamanın versionCode'u ile aynı). */
data class AppRelease(val build: Int, val name: String, val notes: String, val apkUrl: String = Updates.APK_URL)

/** Yeni sürüm denetimi: GitHub Releases (repo herkese açık). */
object Updates {
    const val APK_URL = "https://github.com/uckankeskinkaya/SouNote/releases/latest/download/SouNote.apk"
    private const val API = "https://api.github.com/repos/uckankeskinkaya/SouNote/releases/latest"
    private const val API_LIST = "https://api.github.com/repos/uckankeskinkaya/SouNote/releases?per_page=15"

    /** Test kanalı (yönetici): yayınlanmamış test sürümleri de dahil en yeni derleme. */
    fun latestIncludingTests(): AppRelease {
        val arr = org.json.JSONArray(get(API_LIST))
        val best = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && it.optString("tag_name").startsWith("build-") && "⚠️" !in it.optString("body") }
            .maxByOrNull { it.optString("tag_name").removePrefix("build-").toIntOrNull() ?: 0 } ?: throw IOException("Sürüm bulunamadı")
        return AppRelease(
            best.optString("tag_name").removePrefix("build-").toInt(),
            best.optString("name").ifBlank { "SouNote" },
            best.optString("body").trim(),
            apkUrl = best.optJSONArray("assets")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name") == "SouNote.apk" }?.optString("browser_download_url")
            } ?: APK_URL,
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    fun installedBuild(context: Context): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    @Suppress("DEPRECATION")
    fun installedName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    /** En son yayınlanan sürüm (ağ isteği; ana iş parçacığında çağırma). */
    fun latest(): AppRelease {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val build = o.optString("tag_name").removePrefix("build-").toIntOrNull() ?: throw IOException("Sürüm okunamadı")
            return AppRelease(build, o.optString("name").ifBlank { "SouNote" }, o.optString("body").trim())
        } finally {
            c.disconnect()
        }
    }
}
