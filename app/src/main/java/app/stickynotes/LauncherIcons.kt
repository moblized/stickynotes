package app.stickynotes

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Swaps the app-drawer icons between light and dark versions to match the app's theme.
 * Each icon is a pair of launcher aliases in the manifest; one of each pair is enabled.
 * Called when the app goes to the background, so nothing changes under your finger.
 */
object LauncherIcons {

    private class Pair(val light: String, val dark: String)

    private val pairs = listOf(
        Pair("app.stickynotes.LauncherLight", "app.stickynotes.LauncherDark"),
        Pair("app.stickynotes.QuickNoteLight", "app.stickynotes.QuickNoteDark"),
        Pair("app.stickynotes.VoiceNoteLight", "app.stickynotes.VoiceNoteDark"),
    )

    fun apply(context: Context, dark: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        pairs.forEach { pair ->
            val show = if (dark) pair.dark else pair.light
            val hide = if (dark) pair.light else pair.dark
            // Turn the new icon on before the old one off, so the app never has no icon.
            set(pm, ComponentName(pkg, show), enabled = true, manifestDefault = show == pair.light)
            set(pm, ComponentName(pkg, hide), enabled = false, manifestDefault = hide == pair.light)
        }
    }

    private fun set(pm: PackageManager, component: ComponentName, enabled: Boolean, manifestDefault: Boolean) {
        val current = when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
            else -> false
        }
        if (current == enabled) return
        pm.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
