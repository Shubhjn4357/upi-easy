package com.aerotech.upieasy.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aerotech.upieasy.core.database.dao.OrganizationDao
import com.aerotech.upieasy.core.database.dao.PaymentAccountDao
import com.aerotech.upieasy.core.database.dao.ObservedPaymentEventDao
import com.aerotech.upieasy.core.database.dao.SyncDao
import com.aerotech.upieasy.core.database.dao.TransactionDao
import com.aerotech.upieasy.core.database.dao.UpiDao
import com.aerotech.upieasy.core.database.entity.OrganizationEntity
import com.aerotech.upieasy.core.database.entity.OrganizationInviteEntity
import com.aerotech.upieasy.core.database.entity.PaymentAccountEntity
import com.aerotech.upieasy.core.database.entity.ObservedPaymentEventEntity
import com.aerotech.upieasy.core.database.entity.SyncStateEntity
import com.aerotech.upieasy.core.database.entity.TransactionEntity
import com.aerotech.upieasy.core.database.entity.UpiAccountEntity

@Database(
    entities = [
        TransactionEntity::class,
        UpiAccountEntity::class,
        SyncStateEntity::class,
        OrganizationEntity::class,
        OrganizationInviteEntity::class,
        PaymentAccountEntity::class,
        ObservedPaymentEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun upiDao(): UpiDao
    abstract fun syncDao(): SyncDao
    abstract fun organizationDao(): OrganizationDao
    abstract fun paymentAccountDao(): PaymentAccountDao
    abstract fun observedPaymentEventDao(): ObservedPaymentEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upi_easy_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
