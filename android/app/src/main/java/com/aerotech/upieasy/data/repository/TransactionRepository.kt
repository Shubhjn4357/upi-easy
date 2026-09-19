package com.aerotech.upieasy.data.repository

import com.aerotech.upieasy.core.database.AppDatabase
import com.aerotech.upieasy.core.database.TransactionEntity
import com.aerotech.upieasy.core.network.ApiService
import com.aerotech.upieasy.core.network.CreateTransactionRequest
import com.aerotech.upieasy.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class TransactionRepository(
    private val apiService: ApiService,
    private val database: AppDatabase
) {
    private val transactionDao = database.transactionDao()

    fun getTransactionsFlow(orgId: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsFlow(orgId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun refreshTransactions(orgId: String): Result<Unit> {
        return try {
            val response = apiService.getTransactions(orgId = orgId, limit = 50)
            if (response.isSuccessful && response.body()?.success == true) {
                val dtoList = response.body()?.data ?: emptyList()
                val entities = dtoList.map { dto ->
                    TransactionEntity(
                        id = dto.id,
                        organizationId = dto.organizationId,
                        bankAccountId = dto.bankAccountId,
                        upiAccountId = dto.upiAccountId,
                        type = dto.type,
                        direction = dto.direction,
                        amount = dto.amount,
                        currency = dto.currency,
                        status = dto.status,
                        paymentMethod = dto.paymentMethod,
                        referenceNumber = dto.referenceNumber,
                        payerName = dto.payerName,
                        payerVpa = dto.payerVpa,
                        payeeName = dto.payeeName,
                        payeeVpa = dto.payeeVpa,
                        note = dto.note,
                        occurredAt = parseDate(dto.occurredAt),
                        syncStatus = "SYNCED"
                    )
                }
                transactionDao.insertTransactions(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch transactions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordPayment(
        orgId: String,
        amount: Double,
        payeeName: String,
        payeeVpa: String,
        note: String?,
        upiAccountId: String?,
        referenceNumber: String? = null
    ): Result<Transaction> {
        return try {
            val idempotencyKey = UUID.randomUUID().toString()
            val request = CreateTransactionRequest(
                upiAccountId = upiAccountId,
                direction = "RECEIVED",
                amount = amount,
                payeeName = payeeName,
                payeeVpa = payeeVpa,
                note = note,
                referenceNumber = referenceNumber
            )
            val response = apiService.createTransaction(orgId, idempotencyKey, request)
            if (response.isSuccessful && response.body()?.success == true) {
                val dto = response.body()!!.transaction
                val entity = TransactionEntity(
                    id = dto.id,
                    organizationId = orgId,
                    bankAccountId = dto.bankAccountId,
                    upiAccountId = dto.upiAccountId,
                    type = dto.type ?: "PAYMENT",
                    direction = dto.direction ?: "RECEIVED",
                    amount = dto.amount,
                    currency = dto.currency ?: "INR",
                    status = dto.status ?: "PENDING",
                    paymentMethod = "UPI",
                    referenceNumber = dto.referenceNumber,
                    payerName = dto.payerName,
                    payerVpa = dto.payerVpa,
                    payeeName = dto.payeeName,
                    payeeVpa = dto.payeeVpa,
                    note = dto.note,
                    occurredAt = System.currentTimeMillis(),
                    syncStatus = "SYNCED"
                )
                transactionDao.insertTransaction(entity)
                Result.success(entity.toDomain())
            } else {
                Result.failure(Exception("Failed to record transaction"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDate(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        isoString.toLongOrNull()?.let { return it }
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun TransactionEntity.toDomain() = Transaction(
        id = id,
        organizationId = organizationId,
        bankAccountId = bankAccountId,
        upiAccountId = upiAccountId,
        type = type,
        direction = direction,
        amount = amount,
        currency = currency,
        status = status,
        paymentMethod = paymentMethod,
        referenceNumber = referenceNumber,
        payerName = payerName,
        payerVpa = payerVpa,
        payeeName = payeeName,
        payeeVpa = payeeVpa,
        note = note,
        occurredAt = occurredAt
    )
}
