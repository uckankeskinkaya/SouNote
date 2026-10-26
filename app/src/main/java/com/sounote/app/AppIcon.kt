package com.sounote.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sounote.app.ui.AppTheme

/**
 * Ana ekran simgesi temaya göre: manifestteki takma adlardan (activity-alias) seçili temanınki açılır, ötekiler kapanır.
 * Elektrik ".MainActivity" (eski sürümlerin simgesi), diğerleri ".Launcher<Tema>". Önce yenisi açılır ki uygulama simgesiz kalmasın.
 */
object AppIcon {
    private fun alias(t: AppTheme) = when (t) {
        AppTheme.ELECTRIC -> "MainActivity"
        AppTheme.SAPPHIRE -> "LauncherSapphire"
        AppTheme.SILVER -> "LauncherSilver"
        AppTheme.MONO -> "LauncherMono"
        AppTheme.EMERALD -> "LauncherEmerald"
        AppTheme.ROSE -> "LauncherRose"
    }

    fun apply(context: Context, theme: AppTheme) {
        val pm = context.packageManager
        fun set(t: AppTheme, on: Boolean) {
            val c = ComponentName(context.packageName, "com.sounote.app." + alias(t))
            val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (pm.getComponentEnabledSetting(c) != state) pm.setComponentEnabledSetting(c, state, PackageManager.DONT_KILL_APP)
        }
        runCatching {
            set(theme, true)
            AppTheme.entries.filter { it != theme }.forEach { set(it, false) }
        }.onFailure { ErrorLog.record("Uygulama simgesi değiştirilemedi", it) }
    }
}
