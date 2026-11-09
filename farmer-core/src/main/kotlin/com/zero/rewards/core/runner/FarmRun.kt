package com.zero.rewards.core.runner

import android.content.Context
import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.AutomationHost
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.db.entity.PointLedgerEntity
import com.zero.rewards.core.db.entity.RunEntity
import com.zero.rewards.core.db.entity.TaskEntity
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.market.MarketConfig
import com.zero.rewards.core.search.StaticTermSource
import com.zero.rewards.core.search.TermPool
import com.zero.rewards.core.search.WikipediaRandomSource
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * One full farm pass for an account on the Bing android app.
 *
 * Phase order (each skippable per [Account] flags):
 *   0. Account switch — ensure the Bing app is on this account's MSA
 *   1. Intervalos diarios (streak coins)   — scroll to top, tap coins
 *   2. Daily set (3 tasks)                 — scroll to section, tap each pending
 *   3. More activities (variable tasks)    — scroll to section, tap each pending
 *   4. Search N times
 *   5. Read-to-earn (up to 10 articles)
 *   6. Final sweep — re-run intervalos / daily_set / more_activities to
 *      catch anything the first pass missed (lazy-loaded tiles, race
 *      conditions, section drift).
 */
class FarmRun(
    @Suppress("unused") private val context: Context,
    private val db: FarmerDatabase,
    private val host: AutomationHost,
) {

    private fun newPool(account: Account): TermPool {
        val market = MarketConfig.forCode(account.market) ?: MarketConfig.DEFAULT
        val sources = buildList {
            market.bakedWordlist?.let { add(StaticTermSource(it)) }
            add(WikipediaRandomSource(lang = market.wikiLang))
        }
        return TermPool(sources)
    }

    suspend fun execute(account: Account): Long {
        val startedAt = System.currentTimeMillis()
        val runId = db.runDao().insertRun(
            RunEntity(
                accountId = account.id,
                startedAtEpochMs = startedAt,
                finishedAtEpochMs = null,
                status = "running",
                pointsBefore = account.lastKnownPoints,
                pointsAfter = account.lastKnownPoints,
                errorMessage = null,
            ),
        )
        if (!host.isAvailable) {
            db.runDao().updateRun(
                RunEntity(
                    id = runId, accountId = account.id,
                    startedAtEpochMs = startedAt, finishedAtEpochMs = System.currentTimeMillis(),
                    status = "failed", pointsBefore = account.lastKnownPoints,
                    pointsAfter = account.lastKnownPoints,
                    errorMessage = "AccessibilityService not enabled",
                ),
            )
            return runId
        }

        val primitives = BingPrimitives(host)
        val switcher = BingAccountSwitcher(primitives)
        val pool = newPool(account)

        var totalPoints = 0
        var errorMsg: String? = null

        try {
            // Defensive relaunch: kill any leftover Bing state from a prior run
            // (results page, Cuentas screen, mid-quiz, whatever). This fixes the
            // "run doesn't work after the first one" bug.
            FarmerLog.d("relaunching Bing from scratch")
            host.launchApp(com.zero.rewards.core.automation.BingPackage.PACKAGE)
            host.waitForPackage(com.zero.rewards.core.automation.BingPackage.PACKAGE, 10_000L)
            delay(1_200L)

            // Phase 0: account switch — only if email provided AND not already active.
            // BingAccountSwitcher short-circuits if the target is already the active
            // account, returning Ok without any UI changes.
            if (account.bingEmail.isNotBlank()) {
                errorMsg = runPhase(runId, "switch_account", null, errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    if (switcher.switchTo(account.bingEmail)) TaskResult.Ok(0, "on ${account.bingEmail}")
                    else TaskResult.Failed("could not switch to ${account.bingEmail}")
                }
                delay(Random.nextLong(800L, 2_000L))
            }

            // Phase 1: Intervalos diarios — tap streak coin tiles (top of hub).
            if (account.farmIntervalos) {
                errorMsg = runPhase(runId, "intervalos", null, errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingIntervalosRunner(primitives).run(account)
                }
                delay(Random.nextLong(1_000L, 2_500L))
            }

            // Phase 2: Daily set — scroll to CONJUNTO DIARIO section, tap pending tiles.
            if (account.farmDailySet) {
                errorMsg = runPhase(runId, "daily_set", null, errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingDailySetRunner(primitives).run(account)
                }
                delay(Random.nextLong(1_500L, 4_000L))
            }

            // Phase 3: More activities — scroll to MÁS ACTIVIDADES, tap all pending.
            if (account.farmMoreActivities) {
                errorMsg = runPhase(runId, "more_activities", null, errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingMoreActivitiesRunner(primitives).run(account)
                }
                delay(Random.nextLong(2_000L, 5_000L))
            }

            // Phase 4: Search.
            if (account.farmSearch && account.searchCount > 0) {
                errorMsg = runPhase(runId, "search", null, errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingSearchRunner(primitives, pool).run(account, account.searchCount)
                }
                delay(Random.nextLong(2_000L, 5_000L))
            }

            // Phase 5: Read-to-earn.
            if (account.farmReadToEarn) {
                errorMsg = runPhase(runId, "read_to_earn", null, errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingReadToEarnRunner(primitives).run(account)
                }
                delay(Random.nextLong(1_500L, 3_500L))
            }

            // Phase 6: Final sweep — re-run tile-based phases to catch anything
            // the first pass missed. Each runner is idempotent (already-claimed
            // tiles no-op silently), so this is safe and cheap when there's
            // nothing left to do.
            FarmerLog.i("final sweep: re-checking intervalos / daily_set / more_activities")
            if (account.farmIntervalos) {
                errorMsg = runPhase(runId, "intervalos", "sweep", errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingIntervalosRunner(primitives).run(account)
                }
                delay(Random.nextLong(1_000L, 2_000L))
            }
            if (account.farmDailySet) {
                errorMsg = runPhase(runId, "daily_set", "sweep", errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingDailySetRunner(primitives).run(account)
                }
                delay(Random.nextLong(1_000L, 2_000L))
            }
            if (account.farmMoreActivities) {
                errorMsg = runPhase(runId, "more_activities", "sweep", errorMsg,
                    pointsAccum = { totalPoints += it }, account = account) {
                    BingMoreActivitiesRunner(primitives).run(account)
                }
            }
            FarmerLog.i("final sweep complete for ${account.label}")
        } catch (t: Throwable) {
            errorMsg = t.message
            FarmerLog.e("run crashed for ${account.label}", t)
        }

        val finishedAt = System.currentTimeMillis()
        val newPoints = account.lastKnownPoints + totalPoints
        db.runDao().updateRun(
            RunEntity(
                id = runId, accountId = account.id,
                startedAtEpochMs = startedAt, finishedAtEpochMs = finishedAt,
                status = if (errorMsg == null) "ok" else "partial",
                pointsBefore = account.lastKnownPoints, pointsAfter = newPoints,
                errorMessage = errorMsg,
            ),
        )
        db.accountDao().markRun(account.id, finishedAt, newPoints)
        return runId
    }

    private suspend fun runPhase(
        runId: Long,
        kind: String,
        subKind: String?,
        errorAccum: String?,
        pointsAccum: (Int) -> Unit,
        account: Account,
        block: suspend () -> TaskResult,
    ): String? {
        val taskStarted = System.currentTimeMillis()
        val taskId = db.runDao().insertTask(
            TaskEntity(
                runId = runId, kind = kind, subKind = subKind,
                status = "running", pointsClaimed = 0,
                startedAtEpochMs = taskStarted, finishedAtEpochMs = null, detail = null,
            ),
        )
        val result = runCatching { block() }.getOrElse { TaskResult.Failed("exception: ${it.message}", it) }
        val finishedAt = System.currentTimeMillis()
        val label = subKind?.let { "$kind/$it" } ?: kind
        return when (result) {
            is TaskResult.Ok -> {
                pointsAccum(result.pointsClaimed)
                db.runDao().updateTask(
                    TaskEntity(
                        id = taskId, runId = runId, kind = kind, subKind = subKind,
                        status = "ok", pointsClaimed = result.pointsClaimed,
                        startedAtEpochMs = taskStarted, finishedAtEpochMs = finishedAt, detail = result.detail,
                    ),
                )
                db.pointLedgerDao().insert(
                    PointLedgerEntity(
                        accountId = account.id, epochMs = finishedAt,
                        delta = result.pointsClaimed, reason = label,
                    ),
                )
                errorAccum
            }
            is TaskResult.Skipped -> {
                db.runDao().updateTask(
                    TaskEntity(
                        id = taskId, runId = runId, kind = kind, subKind = subKind,
                        status = "skipped", pointsClaimed = 0,
                        startedAtEpochMs = taskStarted, finishedAtEpochMs = finishedAt, detail = result.reason,
                    ),
                )
                errorAccum
            }
            is TaskResult.Failed -> {
                FarmerLog.w("$label failed for ${account.label}: ${result.reason}", result.cause)
                db.runDao().updateTask(
                    TaskEntity(
                        id = taskId, runId = runId, kind = kind, subKind = subKind,
                        status = "failed", pointsClaimed = 0,
                        startedAtEpochMs = taskStarted, finishedAtEpochMs = finishedAt, detail = result.reason,
                    ),
                )
                errorAccum ?: result.reason
            }
        }
    }
}
