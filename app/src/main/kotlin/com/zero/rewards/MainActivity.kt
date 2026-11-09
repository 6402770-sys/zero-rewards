package com.zero.rewards

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.zero.rewards.notifications.StreakPromptBus
import com.zero.rewards.service.ZeroAccessibilityService
import com.zero.rewards.ui.AppIcons
import com.zero.rewards.ui.ZeroRewardsRoot
import com.zero.rewards.ui.theme.AppearanceStore
import com.zero.rewards.ui.theme.ZeroTheme
import com.zero.rewards.ui.theme.accentColors

class MainActivity : ComponentActivity() {

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeAskForNotifications()
        maybeHandleStreakPrompt(intent)
        val container = (application as ZeroApp).container
        setContent {
            ZeroTheme { ZeroRewardsRoot(container) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeHandleStreakPrompt(intent)
    }

    private fun maybeHandleStreakPrompt(intent: Intent?) {
        if (intent?.getBooleanExtra(StreakPromptBus.EXTRA_STREAK_PROMPT, false) == true) {
            // Clear the extra so a config-change recreation doesn't re-fire.
            intent.removeExtra(StreakPromptBus.EXTRA_STREAK_PROMPT)
            StreakPromptBus.request()
        }
    }

    override fun onResume() {
        super.onResume()
        maybeAskForAccessibility()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppIcons.applyPending(this)
    }

    private fun maybeAskForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val perm = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            requestNotif.launch(perm)
        }
    }

    private fun maybeAskForAccessibility() {
        if (ZeroAccessibilityService.get() != null) return
        val appearance = AppearanceStore.get(this)
        val dark = appearance.isDark(this)
        val dialogTheme = if (dark) R.style.Theme_ZeroRewards_Dialog_Dark else R.style.Theme_ZeroRewards_Dialog_Light
        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Enable Accessibility")
            .setMessage(
                "Zero Rewards drives the Bing app through Android's Accessibility API. " +
                    "Enable 'Zero Rewards' in:\n\n" +
                    "Settings → Accessibility → Downloaded apps → Zero Rewards → Use\n\n" +
                    "The farmer can't run without this.",
            )
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Not now", null)
            .setCancelable(false)
            .show()
            .also { dialog ->
                val accent = accentColors(appearance.accentSeed.value, dark).primary
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(accent)
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(accent)
            }
    }
}
