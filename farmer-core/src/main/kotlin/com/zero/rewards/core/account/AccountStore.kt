package com.zero.rewards.core.account

import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountStore(private val db: FarmerDatabase) {

    fun observe(): Flow<List<Account>> =
        db.accountDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun all(): List<Account> =
        db.accountDao().all().map { it.toDomain() }

    suspend fun byId(id: String): Account? = db.accountDao().byId(id)?.toDomain()

    suspend fun upsert(account: Account) { db.accountDao().upsert(account.toEntity()) }

    suspend fun delete(id: String) { db.accountDao().delete(id) }

    suspend fun markRun(id: String, epochMs: Long, points: Int) {
        db.accountDao().markRun(id, epochMs, points)
    }

    private fun AccountEntity.toDomain() = Account(
        id = id, label = label, bingEmail = bingEmail,
        locale = locale, market = market, country = country,
        createdAtEpochMs = createdAtEpochMs,
        lastRunEpochMs = lastRunEpochMs, lastKnownPoints = lastKnownPoints,
        streakDays = streakDays, searchCount = searchCount,
        searchDelayMinSec = searchDelayMinSec, searchDelayMaxSec = searchDelayMaxSec,
        farmSearch = farmSearch, farmDailySet = farmDailySet,
        farmMoreActivities = farmMoreActivities, farmReadToEarn = farmReadToEarn,
        farmIntervalos = farmIntervalos,
    )

    private fun Account.toEntity() = AccountEntity(
        id = id, label = label, bingEmail = bingEmail,
        locale = locale, market = market, country = country,
        createdAtEpochMs = createdAtEpochMs,
        lastRunEpochMs = lastRunEpochMs, lastKnownPoints = lastKnownPoints,
        streakDays = streakDays, searchCount = searchCount,
        searchDelayMinSec = searchDelayMinSec, searchDelayMaxSec = searchDelayMaxSec,
        farmSearch = farmSearch, farmDailySet = farmDailySet,
        farmMoreActivities = farmMoreActivities, farmReadToEarn = farmReadToEarn,
        farmIntervalos = farmIntervalos,
    )
}
