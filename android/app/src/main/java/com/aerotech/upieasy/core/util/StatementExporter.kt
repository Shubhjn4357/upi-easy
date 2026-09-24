package com.aerotech.upieasy.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.aerotech.upieasy.domain.model.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StatementExporter {

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val rowDateFormatter = SimpleDateFormat("dd/MM/yy HH:mm", Locale.ENGLISH)

    /**
     * Generates a genuine multi-page, formatted A4 PDF statement using Android's native PdfDocument.
     */
    fun generatePdf(
        context: Context,
        merchantName: String,
        orgName: String,
        transactions: List<Transaction>,
        totalAmount: Double
    ): File {
        val fileName = "UPIEasy_Statement_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val pdfDoc = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points
        val pageHeight = 842 // Standard A4 height in points
        val rowsPerPage = 22
        val totalPages = maxOf(1, (transactions.size + rowsPerPage - 1) / rowsPerPage)

        val headerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            isAntiAlias = true
        }

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            textSize = 9f
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            textSize = 9.5f
            isAntiAlias = true
        }

        val boldMetaPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableHeaderBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249) // Slate 100
        }

        val rowBgPaintAlt = Paint().apply {
            color = Color.rgb(248, 250, 252) // Slate 50
        }

        val rowTextPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.5f
            isAntiAlias = true
        }

        val amountPaint = Paint().apply {
            color = Color.rgb(16, 149, 106) // Emerald green
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.8f
        }

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // Header Banner
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 64f, headerPaint)
            canvas.drawText("UPIEasy Merchant Payment Statement", 28f, 32f, titlePaint)
            canvas.drawText("Verified NPCI / UPI Reconciliation & Audit Report", 28f, 48f, subtitlePaint)

            // Organization & Summary info (on first page)
            var currentY = 82f
            if (pageIndex == 0) {
                canvas.drawText("Organization: ", 28f, currentY, boldMetaPaint)
                canvas.drawText(orgName, 100f, currentY, metaPaint)

                canvas.drawText("Merchant: ", 320f, currentY, boldMetaPaint)
                canvas.drawText(merchantName, 375f, currentY, metaPaint)

                currentY += 16f
                canvas.drawText("Generated: ", 28f, currentY, boldMetaPaint)
                canvas.drawText(dateFormatter.format(Date()), 100f, currentY, metaPaint)

                canvas.drawText("Total Inflow: ", 320f, currentY, boldMetaPaint)
                canvas.drawText("INR ${String.format(Locale.ENGLISH, "%,.2f", totalAmount)} (${transactions.size} txns)", 385f, currentY, amountPaint)

                currentY += 22f
            }

            // Table Header Bar
            canvas.drawRect(24f, currentY, (pageWidth - 24).toFloat(), currentY + 22f, tableHeaderBgPaint)
            canvas.drawLine(24f, currentY, (pageWidth - 24).toFloat(), currentY, linePaint)
            canvas.drawLine(24f, currentY + 22f, (pageWidth - 24).toFloat(), currentY + 22f, linePaint)

            val textY = currentY + 15f
            canvas.drawText("Date & Time", 30f, textY, tableHeaderPaint)
            canvas.drawText("Payee / Store", 115f, textY, tableHeaderPaint)
            canvas.drawText("Payer / Customer", 225f, textY, tableHeaderPaint)
            canvas.drawText("UTR Reference", 335f, textY, tableHeaderPaint)
            canvas.drawText("Status", 435f, textY, tableHeaderPaint)
            canvas.drawText("Amount", 500f, textY, tableHeaderPaint)

            currentY += 22f

            // Rows for this page
            val startIndex = pageIndex * rowsPerPage
            val endIndex = minOf(startIndex + rowsPerPage, transactions.size)

            for (i in startIndex until endIndex) {
                val txn = transactions[i]
                val rowHeight = 24f

                if ((i - startIndex) % 2 == 1) {
                    canvas.drawRect(24f, currentY, (pageWidth - 24).toFloat(), currentY + rowHeight, rowBgPaintAlt)
                }

                val rowTextY = currentY + 15f
                val formattedDate = rowDateFormatter.format(Date(txn.occurredAt))
                val payeeShort = if (txn.payeeName.length > 18) txn.payeeName.take(16) + ".." else txn.payeeName
                val payerShort = if ((txn.payerName ?: "").length > 18) (txn.payerName ?: "").take(16) + ".." else (txn.payerName ?: "Customer")
                val refShort = if ((txn.referenceNumber ?: "").length > 16) (txn.referenceNumber ?: "").take(14) + ".." else (txn.referenceNumber ?: "-")

                canvas.drawText(formattedDate, 30f, rowTextY, rowTextPaint)
                canvas.drawText(payeeShort, 115f, rowTextY, rowTextPaint)
                canvas.drawText(payerShort, 225f, rowTextY, rowTextPaint)
                canvas.drawText(refShort, 335f, rowTextY, rowTextPaint)
                canvas.drawText(txn.status, 435f, rowTextY, rowTextPaint)
                canvas.drawText("₹${String.format(Locale.ENGLISH, "%,.2f", txn.amount)}", 500f, rowTextY, amountPaint)

                currentY += rowHeight
                canvas.drawLine(24f, currentY, (pageWidth - 24).toFloat(), currentY, linePaint)
            }

            // Footer
            val footerY = (pageHeight - 24).toFloat()
            canvas.drawLine(24f, footerY - 12f, (pageWidth - 24).toFloat(), footerY - 12f, linePaint)
            canvas.drawText("Generated securely by UPIEasy Merchant System", 28f, footerY, subtitlePaint)
            canvas.drawText("Page ${pageIndex + 1} of $totalPages", (pageWidth - 85).toFloat(), footerY, metaPaint)

            pdfDoc.finishPage(page)
        }

        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        return file
    }

    /**
     * Generates a standard RFC-4180 CSV spreadsheet file.
     */
    fun generateCsv(
        context: Context,
        transactions: List<Transaction>
    ): File {
        val fileName = "UPIEasy_Statement_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        val csvBuilder = StringBuilder()
        csvBuilder.append("Date,Time,Payee Name,UPI Handle,Payer Name,Payer UPI,Amount (INR),Status,Direction,UTR Reference,Note\n")

        val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val timeOnlyFmt = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

        transactions.forEach { t ->
            val date = Date(t.occurredAt)
            csvBuilder.append("\"${dateOnlyFmt.format(date)}\",")
            csvBuilder.append("\"${timeOnlyFmt.format(date)}\",")
            csvBuilder.append("\"${escapeCsv(t.payeeName)}\",")
            csvBuilder.append("\"${escapeCsv(t.payeeVpa)}\",")
            csvBuilder.append("\"${escapeCsv(t.payerName ?: "")}\",")
            csvBuilder.append("\"${escapeCsv(t.payerVpa ?: "")}\",")
            csvBuilder.append("${t.amount},")
            csvBuilder.append("\"${t.status}\",")
            csvBuilder.append("\"${t.direction}\",")
            csvBuilder.append("\"${escapeCsv(t.referenceNumber ?: "")}\",")
            csvBuilder.append("\"${escapeCsv(t.note ?: "")}\"\n")
        }

        FileOutputStream(file).use { it.write(csvBuilder.toString().toByteArray()) }
        return file
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }

    /**
     * Shares the generated statement file using Android's native FileProvider.
     */
    fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        subject: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Statement"))
    }
}
