package com.aerotech.upieasy.core.database.dao

import androidx.room.*
import com.aerotech.upieasy.core.database.entity.ObservedPaymentEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservedPaymentEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ObservedPaymentEventEntity): Long

    @Query("SELECT * FROM observed_payment_events WHERE organizationId = :orgId AND eventFingerprint = :fingerprint LIMIT 1")
    suspend fun getEventByFingerprint(orgId: String, fingerprint: String): ObservedPaymentEventEntity?

    @Query("SELECT * FROM observed_payment_events WHERE syncState = 'PENDING_UPLOAD' ORDER BY observedAt ASC LIMIT :limit")
    suspend fun getPendingUploadEvents(limit: Int = 50): List<ObservedPaymentEventEntity>

    @Query("UPDATE observed_payment_events SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)

    @Query("SELECT * FROM observed_payment_events WHERE organizationId = :orgId ORDER BY observedAt DESC")
    fun getEventsForOrg(orgId: String): Flow<List<ObservedPaymentEventEntity>>

    @Query("SELECT * FROM observed_payment_events ORDER BY observedAt DESC LIMIT 1")
    suspend fun getLatestObservedEvent(): ObservedPaymentEventEntity?
}
