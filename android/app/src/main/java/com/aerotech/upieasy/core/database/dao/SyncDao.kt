package com.aerotech.upieasy.core.database.dao

import androidx.room.*
import com.aerotech.upieasy.core.database.entity.SyncStateEntity

@Dao
interface SyncDao {
    @Query("SELECT * FROM local_sync_state WHERE organizationId = :orgId")
    suspend fun getSyncState(orgId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSyncState(syncState: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSyncState(syncState: SyncStateEntity)

    @Query("DELETE FROM local_sync_state WHERE organizationId = :orgId")
    suspend fun clearSyncState(orgId: String)
}
