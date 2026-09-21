package com.aerotech.upieasy.core.database.dao

import androidx.room.*
import com.aerotech.upieasy.core.database.entity.PaymentAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAccountDao {

    @Query("SELECT * FROM payment_accounts WHERE organizationId = :orgId AND status != 'ARCHIVED' ORDER BY createdAt DESC")
    fun getAccountsForOrg(orgId: String): Flow<List<PaymentAccountEntity>>

    @Query("SELECT * FROM payment_accounts WHERE organizationId = :orgId AND status != 'ARCHIVED' ORDER BY createdAt DESC")
    suspend fun getAccountsForOrgSync(orgId: String): List<PaymentAccountEntity>

    @Query("SELECT * FROM payment_accounts WHERE id = :id")
    suspend fun getAccountById(id: String): PaymentAccountEntity?

    @Query("SELECT * FROM payment_accounts WHERE paymentAppPackage = :packageName AND detectionEnabled = 1 AND status = 'ACTIVE'")
    suspend fun getActiveAccountsForPackage(packageName: String): List<PaymentAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: PaymentAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<PaymentAccountEntity>)

    @Query("UPDATE payment_accounts SET detectionEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun updateDetectionState(id: String, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE payment_accounts SET lastNotificationDetectedAt = :time, updatedAt = :time WHERE id = :id")
    suspend fun updateLastDetectedTime(id: String, time: Long)

    @Query("DELETE FROM payment_accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)
}
