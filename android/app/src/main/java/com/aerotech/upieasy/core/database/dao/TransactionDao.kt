package com.aerotech.upieasy.core.database.dao

import androidx.room.*
import com.aerotech.upieasy.core.database.entity.TransactionEntity
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

    @Query("UPDATE local_transactions SET status = :status, referenceNumber = COALESCE(:referenceNumber, referenceNumber) WHERE id = :id")
    suspend fun updateStatusAndRef(id: String, status: String, referenceNumber: String?)

    @Query("DELETE FROM local_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Query("DELETE FROM local_transactions WHERE organizationId = :orgId")
    suspend fun clearTransactions(orgId: String)
}
