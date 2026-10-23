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
    private const val TEST_JSON = "https://github.com/uckankeskinkaya/SouNote/releases/download/test/test.json"
    private const val TEST_APK = "https://github.com/uckankeskinkaya/SouNote/releases/download/test/SouNote.apk"

    /** Test kanalı (yönetici): yayınlanmamış test sürümleri de dahil en yeni derleme. */
    fun latestIncludingTests(): AppRelease {
        // Sabit "test" kanalı (her derlemede güncellenir); yayınlanan sürüm daha yeniyse o.
        val o = JSONObject(get(TEST_JSON))
        val test = AppRelease(o.getInt("build"), o.optString("name", "Test"), o.optString("notes"), TEST_APK)
        val stable = runCatching { latest() }.getOrNull()
        return if (stable != null && stable.build >= test.build) stable else test
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
