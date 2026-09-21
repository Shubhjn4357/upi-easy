package com.aerotech.upieasy.core.database.dao

import androidx.room.*
import com.aerotech.upieasy.core.database.entity.UpiAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpiDao {
    @Query("SELECT * FROM local_upi_accounts WHERE organizationId = :orgId ORDER BY isDefault DESC")
    fun getUpiAccountsFlow(orgId: String): Flow<List<UpiAccountEntity>>

    @Query("SELECT * FROM local_upi_accounts ORDER BY isDefault DESC")
    fun getAllUpiAccountsFlow(): Flow<List<UpiAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpiAccounts(accounts: List<UpiAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpiAccount(account: UpiAccountEntity)

    @Query("UPDATE local_upi_accounts SET isDefault = 0 WHERE organizationId = :orgId")
    suspend fun clearDefaultUpi(orgId: String)

    @Query("UPDATE local_upi_accounts SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultUpi(id: String)

    @Query("DELETE FROM local_upi_accounts WHERE id = :id")
    suspend fun deleteUpiAccount(id: String)

    @Query("DELETE FROM local_upi_accounts WHERE organizationId = :orgId")
    suspend fun clearUpiAccounts(orgId: String)
}
