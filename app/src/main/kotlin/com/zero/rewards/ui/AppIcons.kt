package com.zero.rewards.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.zero.rewards.R
import com.zero.rewards.service.FarmerState

/** A launcher icon. Each one is an `activity-alias` of MainActivity in the manifest. */
data class AppIcon(
    val alias: String,
    val label: String,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
)

object AppIcons {
    val Colors = listOf(
        AppIcon("IconMidnight", "Midnight", R.drawable.ic_bg_midnight, R.drawable.ic_fg_midnight),
        AppIcon("IconFoil", "Foil", R.drawable.ic_bg_foil, R.drawable.ic_fg_foil),
        AppIcon("IconEmerald", "Emerald", R.drawable.ic_bg_emerald, R.drawable.ic_fg_emerald),
        AppIcon("IconSapphire", "Sapphire", R.drawable.ic_bg_sapphire, R.drawable.ic_fg_sapphire),
        AppIcon("IconAmethyst", "Amethyst", R.drawable.ic_bg_amethyst, R.drawable.ic_fg_amethyst),
        AppIcon("IconRose", "Rose", R.drawable.ic_bg_rose, R.drawable.ic_fg_rose),
        AppIcon("IconEmber", "Ember", R.drawable.ic_bg_ember, R.drawable.ic_fg_ember),
        AppIcon("IconSilver", "Silver", R.drawable.ic_bg_silver, R.drawable.ic_fg_silver),
    )

    val Specials = listOf(
        AppIcon("IconCoin", "Coin", R.drawable.ic_bg_coin, R.drawable.ic_fg_coin),
        AppIcon("IconCrown", "Crown", R.drawable.ic_bg_crown, R.drawable.ic_fg_crown),
        AppIcon("IconGem", "Gem", R.drawable.ic_bg_gem, R.drawable.ic_fg_gem),
        AppIcon("IconBolt", "Bolt", R.drawable.ic_bg_bolt, R.drawable.ic_fg_bolt),
        AppIcon("IconSunset", "Sunset", R.drawable.ic_bg_sunset, R.drawable.ic_fg_sunset),
        AppIcon("IconPixel", "Pixel", R.drawable.ic_bg_pixel, R.drawable.ic_fg_pixel),
        AppIcon("IconOrbit", "Orbit", R.drawable.ic_bg_orbit, R.drawable.ic_fg_orbit),
        AppIcon("IconAurora", "Aurora", R.drawable.ic_bg_aurora, R.drawable.ic_fg_aurora),
    )

    private const val KEY_PENDING = "pending_icon"
    private val All = Colors + Specials
    private val Default = Colors.first()

    private fun component(context: Context, icon: AppIcon) =
        ComponentName(context.packageName, "${context.packageName}.${icon.alias}")

    private fun prefs(context: Context) =
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private fun pending(context: Context): AppIcon? =
        prefs(context).getString(KEY_PENDING, null)?.let { alias -> All.firstOrNull { it.alias == alias } }

    /** The icon shown on the launcher right now, according to the package manager. */
    private fun installed(context: Context): AppIcon {
        val pm = context.packageManager
        return All.firstOrNull {
            pm.getComponentEnabledSetting(component(context, it)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: Default
    }

    /** The user's choice, even if it hasn't reached the launcher yet. */
    fun current(context: Context): AppIcon = pending(context) ?: installed(context)

    /**
     * Android closes a task when the alias that launched it gets disabled, so changing
     * the icon straight away would throw the user out of the app. It's only remembered
     * here, and [applyPending] does the switch once the app is in the background.
     */
    fun select(context: Context, icon: AppIcon) {
        prefs(context).edit().putString(KEY_PENDING, icon.alias).apply()
    }

    /** Called when the app leaves the foreground. Waits if a run is in progress, so it's never disturbed. */
    fun applyPending(context: Context) {
        val icon = pending(context) ?: return
        if (FarmerState.runningAccountId.value != null) return
        if (icon != installed(context)) switchTo(context, icon)
        prefs(context).edit().remove(KEY_PENDING).apply()
    }

    /** Enables the new alias before disabling the rest, so the app always has a launcher entry. */
    private fun switchTo(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        All.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
