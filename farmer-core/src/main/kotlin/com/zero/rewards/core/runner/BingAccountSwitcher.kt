package com.zero.rewards.core.runner

import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPackage
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog

/**
 * Switch the Bing app's active Microsoft account by driving the Perfil drawer.
 *
 * Flow:
 *   1. Ensure Bing is foregrounded, then tap the "Perfil" button (top-left
 *      hamburger-style button in current Bing builds — a drawer opens with
 *      the active account's name + email at the top).
 *   2. If the target email is visible in the drawer, we're already on that
 *      account — close the drawer with BACK, go to Bing home via the Inicio
 *      tab, done. We NEVER tap the email row because that navigates to an
 *      AccountDetails screen that's hard to escape without exiting Bing.
 *   3. Otherwise look for a "Switch account" / "Administrar cuentas" /
 *      "Cambiar cuenta" link in the drawer and tap it. On the account list
 *      screen that opens, tap the target email, wait for Bing to re-home,
 *      then route back to Inicio.
 *
 * Requires the user to have previously signed in to each target account in
 * the Bing app. We don't type passwords.
 */
class BingAccountSwitcher(private val primitives: BingPrimitives) {

    suspend fun switchTo(targetEmail: String): Boolean {
        if (targetEmail.isBlank()) return true
        if (!primitives.ensureBingForeground()) return false
        primitives.idle(600, 1_200)

        if (!openPerfilDrawer()) {
            FarmerLog.w("Perfil drawer didn't open")
            return false
        }
        primitives.idle(500, 1_000)

        // Drawer open — does its header show the target email?
        if (primitives.host.findByText(targetEmail, 1_200L) != null) {
            FarmerLog.i("account $targetEmail already active (drawer shows it)")
            primitives.host.back(); primitives.idle(400, 700)
            primitives.backToBingHome()
            return true
        }

        // Not on target. Try multiple paths to reach the account list.
        if (openAccountListAndTap(targetEmail)) {
            // Don't poll for home to reappear — Microsoft's servers can stall
            // 5-25s and we don't need to block the whole pipeline on that.
            // Just tap the Inicio bottom-nav tab and let the next phase
            // (intervalos → opens Rewards panel via ensureBingForeground)
            // handle any residual sub-screen state.
            FarmerLog.d("switcher: commit done — tap Inicio and continue farm")
            primitives.idle(1_200, 1_800)
            tapInicioOnce()
            FarmerLog.d("switcher: switch_account phase complete (farm continues)")
            return true
        }

        FarmerLog.w("couldn't reach account list for $targetEmail. tree:\n${primitives.host.dumpTree(6)}")
        primitives.host.back()
        return false
    }

    /**
     * From the Perfil drawer, open the account-list screen and tap the target
     * email. Tries, in order:
     *   1. A direct switcher link ("Administrar cuentas", "Cambiar cuenta",
     *      "Switch account", etc.) — older Bing versions.
     *   2. The "Reenviar" / "Switch" icon next to the active email header —
     *      current build uses this as its switch-account control.
     *   3. Tapping the active email row itself — some Bing versions open an
     *      account picker when you tap the header email.
     *   4. "Configuración" → scroll → "Cuentas" / "Cuenta de Microsoft".
     *
     * Returns true the moment a tap is dispatched on [targetEmail].
     */
    private suspend fun openAccountListAndTap(targetEmail: String): Boolean {
        // Path 1: direct switcher link.
        findSwitcherLink()?.let { link ->
            FarmerLog.d("switcher path 1: link ${link.describe()}")
            primitives.host.tap(link); primitives.idle(800, 1_500)
            if (tapTargetEmail(targetEmail)) return true
        }

        // Path 2: "Reenviar" icon next to the active email in the drawer header.
        findReenviarIcon()?.let { icon ->
            FarmerLog.d("switcher path 2: Reenviar icon ${icon.describe()}")
            primitives.host.tap(icon); primitives.idle(900, 1_600)
            if (tapTargetEmail(targetEmail)) return true
            findSwitcherLink()?.let { link ->
                primitives.host.tap(link); primitives.idle(800, 1_500)
                if (tapTargetEmail(targetEmail)) return true
            }
        }

        // Path 3: tap the currently-displayed active email row.
        findActiveEmailRow()?.let { row ->
            FarmerLog.d("switcher path 3: active email row ${row.describe()}")
            primitives.host.tap(row); primitives.idle(1_000, 1_700)
            if (tapTargetEmail(targetEmail)) return true
            findSwitcherLink()?.let { link ->
                primitives.host.tap(link); primitives.idle(800, 1_500)
                if (tapTargetEmail(targetEmail)) return true
            }
        }

        // Path 4: Configuración → Cuentas.
        val config = primitives.host.findByText("Configuración", 500L)
            ?: primitives.host.findByText("Settings", 300L)
        if (config != null) {
            FarmerLog.d("switcher path 4: Configuración ${config.describe()}")
            primitives.host.tap(config); primitives.idle(900, 1_600)
            val accEntry = primitives.scrollUntil(maxScrolls = 6, fraction = 0.5f) {
                primitives.host.findByText("Cuentas", 400L)
                    ?: primitives.host.findByText("Accounts", 300L)
                    ?: primitives.host.findByText("Cuenta de Microsoft", 300L)
                    ?: primitives.host.findByText("Microsoft account", 300L)
                    ?: primitives.host.findByText("Administrar cuentas", 300L)
            }
            if (accEntry != null) {
                primitives.host.tap(accEntry); primitives.idle(900, 1_600)
                if (tapTargetEmail(targetEmail)) return true
            }
        }
        return false
    }

    private suspend fun tapTargetEmail(targetEmail: String): Boolean {
        val row = primitives.host.findByText(targetEmail, 2_000L) ?: return false
        FarmerLog.d("switcher: tap target row ${row.describe()}")

        // Step 1: tapCoord on the email row to SELECT it.
        // The Cuentas screen uses a RecyclerView whose rows commit via the
        // bottom-right "Cambiar ahora" button only AFTER a row is selected.
        primitives.host.tapCoord(row.centerX.toFloat(), row.centerY.toFloat())
        primitives.idle(1_000, 1_800)

        // Verify the row got selected (a "seleccionado" ImageView appears
        // next to the chosen row). If not, retry on the clickable container.
        if (!wasRowSelected()) {
            FarmerLog.d("switcher: no 'seleccionado' marker, retry on container")
            findClickableRowContainer(row)?.let { container ->
                primitives.host.tapCoord(container.centerX.toFloat(), container.centerY.toFloat())
                primitives.idle(1_000, 1_800)
            }
        }

        // Step 2: tap the commit button that appears at bottom-right after
        // selecting a row. Labels vary by build; "Cambiar ahora" is the
        // current one, "Cambiar cuenta" / "Switch account" / etc. covered too.
        val confirmLabels = listOf(
            "Cambiar ahora", "Switch now", "Change now",
            "Cambiar cuenta", "Switch account",
            "Continuar", "Continue",
            "Confirmar", "Confirm",
            "Aceptar", "Accept",
            "Sí", "Yes",
        )
        var committed = false
        for (label in confirmLabels) {
            val btn = primitives.host.findByText(label, 400L)
            if (btn != null && btn.visible) {
                FarmerLog.d("switcher: commit tapCoord '$label' ${btn.describe()}")
                primitives.host.tapCoord(btn.centerX.toFloat(), btn.centerY.toFloat())
                primitives.idle(1_500, 2_500)
                committed = true
                break
            }
        }
        if (!committed) {
            FarmerLog.d("switcher: no commit button found — switch may be instant")
            primitives.idle(1_500, 2_500)
        }

        if (stillOnCuentasScreen()) {
            FarmerLog.w("switcher: Cuentas screen still visible after commit. dump:")
            dumpCuentasScreen(row)
        }
        return true
    }

    /** True when a "seleccionado" marker appears somewhere on screen. */
    private suspend fun wasRowSelected(): Boolean {
        return primitives.host.findByContentDesc("seleccionado", 400L) != null ||
            primitives.host.findByContentDesc("selected", 300L) != null
    }

    private suspend fun stillOnCuentasScreen(): Boolean {
        return primitives.host.findByText("Sesión iniciada como", 600L) != null ||
            primitives.host.findByText("Cambiar a otra cuenta", 400L) != null ||
            primitives.host.findByText("Quitar cuenta", 400L) != null
    }

    /**
     * Dump every clickable + nearby node on the Cuentas screen, especially
     * around the target row, so we can pick the right tap target.
     */
    private suspend fun dumpCuentasScreen(targetRow: AutomationNode) {
        FarmerLog.d("CUENTAS DIAG >>>")
        FarmerLog.d("  target=${targetRow.describe()} screen=${primitives.host.screenWidth}x${primitives.host.screenHeight}")

        val classes = listOf(
            "android.widget.LinearLayout", "android.widget.RelativeLayout",
            "android.widget.FrameLayout", "android.view.ViewGroup",
            "android.view.View", "android.widget.Button",
            "android.widget.ImageView", "android.widget.TextView",
        )
        val all = buildList {
            for (c in classes) addAll(primitives.host.findByClass(c))
        }.distinctBy { it.handle }.filter { it.visible }

        // ALL clickable nodes on the screen.
        val clickables = all.filter { it.clickable }.sortedBy { it.boundsT }
        FarmerLog.d("  CLICKABLE nodes on screen: ${clickables.size}")
        for (c in clickables.take(30)) {
            FarmerLog.d("    click ${c.className?.substringAfterLast('.')} " +
                "text='${c.text?.take(30)}' desc='${c.contentDescription?.take(40)}' " +
                "size=${c.width}x${c.height} xy=(${c.boundsL},${c.boundsT})")
        }

        // Nodes near the target row — ±120px vertical band.
        val bandTop = targetRow.boundsT - 120
        val bandBot = targetRow.boundsB + 120
        val nearby = all.filter {
            (it.boundsT in bandTop..bandBot) || (it.boundsB in bandTop..bandBot)
        }.sortedBy { it.boundsT }
        FarmerLog.d("  nodes NEAR target row (y=$bandTop..$bandBot): ${nearby.size}")
        for (n in nearby.take(30)) {
            FarmerLog.d("    ${n.className?.substringAfterLast('.')} " +
                "text='${n.text?.take(30)}' desc='${n.contentDescription?.take(40)}' " +
                "size=${n.width}x${n.height} xy=(${n.boundsL},${n.boundsT}) click=${n.clickable}")
        }

        // Full tree too for cross-reference.
        FarmerLog.d("  tree:\n${primitives.host.dumpTree(8)}")
        FarmerLog.d("<<< CUENTAS DIAG")
    }

    /**
     * Find the clickable container whose bounds contain the target row's
     * text. The row's click handler usually lives on a parent View (not the
     * TextView) that spans the whole row horizontally.
     */
    private suspend fun findClickableRowContainer(targetRow: AutomationNode): AutomationNode? {
        val classes = listOf(
            "android.widget.LinearLayout", "android.widget.RelativeLayout",
            "android.widget.FrameLayout", "android.view.ViewGroup",
            "android.view.View",
        )
        val all = buildList {
            for (c in classes) addAll(primitives.host.findByClass(c))
        }.distinctBy { it.handle }

        // Clickable, overlaps the target row's Y, wide enough to be a row.
        val candidates = all.filter { n ->
            n.visible && n.clickable &&
                n.boundsT <= targetRow.boundsT && n.boundsB >= targetRow.boundsB &&
                n.width > primitives.host.screenWidth * 0.5
        }
        // Prefer the smallest one (innermost = the actual row container).
        return candidates.minByOrNull { it.width.toLong() * it.height }
    }

    /**
     * Finds the "Reenviar" icon — on current Bing builds the switch-account
     * control in the Perfil drawer header is rendered as an ImageView with
     * contentDescription "Reenviar" (resend/forward icon). Only accepts hits
     * in the top third of the screen (drawer header area), to avoid matching
     * the browser chrome's own Reenviar button on sub-screens.
     */
    private suspend fun findReenviarIcon(): AutomationNode? {
        val top = (primitives.host.screenHeight * 0.4).toInt()
        val n = primitives.host.findByContentDesc("Reenviar", 500L)
            ?: primitives.host.findByContentDesc("Switch account", 300L)
            ?: primitives.host.findByContentDesc("Cambiar cuenta", 300L)
        return n?.takeIf { it.visible && it.boundsT < top }
    }

    /**
     * Finds the active email row in the drawer header (any visible TextView
     * whose text contains "@" and ".") — tapping it on some Bing builds
     * opens an account picker.
     */
    private suspend fun findActiveEmailRow(): AutomationNode? {
        val top = (primitives.host.screenHeight * 0.5).toInt()
        val tvs = primitives.host.findByClass("android.widget.TextView")
        return tvs.firstOrNull { n ->
            n.visible && n.boundsT < top &&
                n.text?.contains("@") == true &&
                n.text?.contains(".") == true
        }
    }

    private suspend fun openPerfilDrawer(): Boolean {
        // Make sure we're on Bing home before hunting for the hamburger Perfil
        // button. If we're still on a sub-screen (e.g. AccountDetails left over
        // from a previous run), the TOP-LEFT Perfil hamburger doesn't exist —
        // the only "Perfil" node would be the avatar INSIDE that sub-screen,
        // which tapping does nothing useful. backToBingHome routes via the
        // Inicio bottom-nav tab.
        primitives.backToBingHome()
        primitives.idle(400, 800)

        // Only accept Perfil candidates in the TOP-LEFT header region
        // (y < 300 and x < screenWidth * 0.3) — that's the hamburger button.
        // The avatar inside AccountDetails sits lower and more centered.
        val screenH = primitives.host.screenHeight
        val screenW = primitives.host.screenWidth
        val perfil = pickHeaderNode("Perfil", screenH, screenW)
            ?: pickHeaderNode("Profile", screenH, screenW)
            ?: pickHeaderNode("Cuenta", screenH, screenW)
            ?: pickHeaderNode("Account", screenH, screenW)
            ?: pickHeaderNode("Avatar", screenH, screenW)
            ?: return false
        FarmerLog.d("openPerfilDrawer: tap ${perfil.describe()}")
        primitives.host.tap(perfil)
        return true
    }

    private suspend fun pickHeaderNode(desc: String, screenH: Int, screenW: Int): AutomationNode? {
        val maxY = (screenH * 0.15).toInt()
        val maxX = (screenW * 0.30).toInt()
        val n = primitives.host.findByContentDesc(desc, 400L)
            ?: return null
        return n.takeIf { it.visible && it.boundsT < maxY && it.boundsL < maxX }
    }

    private suspend fun findSwitcherLink(): AutomationNode? {
        return primitives.host.findByText("Administrar cuentas", 1_200L)
            ?: primitives.host.findByText("Manage accounts", 600L)
            ?: primitives.host.findByText("Cambiar cuenta", 600L)
            ?: primitives.host.findByText("Switch account", 600L)
            ?: primitives.host.findByText("Switch to another account", 600L)
            ?: primitives.host.findByText("Cambiar a otra cuenta", 600L)
            ?: primitives.host.findByText("Cuentas", 600L)
            ?: primitives.host.findByText("Accounts", 600L)
    }

    /**
     * Fire-and-forget Inicio tap. No polling, no verification — the farm's
     * next phase will foreground Bing and open the Rewards panel itself.
     * Falls back to launchApp if the Inicio tab isn't visible (e.g. we
     * ended up on a dialog / splash).
     */
    private suspend fun tapInicioOnce() {
        val inicio = primitives.host.findByContentDesc("Inicio", 1_000L)
            ?: primitives.host.findByContentDesc("Home", 500L)
            ?: primitives.host.findByText("Inicio", 400L)
            ?: primitives.host.findByText("Home", 300L)
        if (inicio != null && inicio.visible) {
            FarmerLog.d("switcher: tap Inicio ${inicio.describe()}")
            primitives.host.tap(inicio)
            primitives.idle(700, 1_200)
            return
        }
        FarmerLog.d("switcher: no Inicio tab visible — launchApp fallback")
        primitives.host.launchApp(BingPackage.PACKAGE)
        primitives.idle(800, 1_300)
    }
}
