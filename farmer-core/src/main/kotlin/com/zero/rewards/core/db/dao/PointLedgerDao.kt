package com.zero.rewards.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zero.rewards.core.db.entity.PointLedgerEntity

@Dao
interface PointLedgerDao {
    @Insert
    suspend fun insert(entry: PointLedgerEntity)

    @Query("SELECT COALESCE(SUM(delta), 0) FROM point_ledger WHERE accountId = :accountId")
    suspend fun totalFor(accountId: String): Int
}
