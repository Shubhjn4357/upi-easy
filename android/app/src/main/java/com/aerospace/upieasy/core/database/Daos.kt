package com.aerospace.upieasy.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM local_transactions WHERE organizationId = :orgId ORDER BY occurredAt DESC")
    fun getTransactionsFlow(orgId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM local_transactions WHERE organizationId = :orgId AND status = :status ORDER BY occurredAt DESC")
    fun getTransactionsByStatusFlow(orgId: String, status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM local_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("UPDATE local_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM local_transactions WHERE organizationId = :orgId")
    suspend fun clearTransactions(orgId: String)
}

@Dao
interface UpiDao {
    @Query("SELECT * FROM local_upi_accounts WHERE organizationId = :orgId ORDER BY isDefault DESC")
    fun getUpiAccountsFlow(orgId: String): Flow<List<UpiAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpiAccounts(accounts: List<UpiAccountEntity>)

    @Query("DELETE FROM local_upi_accounts WHERE organizationId = :orgId")
    suspend fun clearUpiAccounts(orgId: String)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM local_sync_state WHERE organizationId = :orgId")
    suspend fun getSyncState(orgId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSyncState(state: SyncStateEntity)
}
