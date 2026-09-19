package com.aerotech.upieasy.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

object QrCodeGenerator {
    fun generateQrBitmap(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 1)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a high-resolution, custom-designed upi-easy branded payment card poster
     * with merchant avatar, trade name, verified VPA, theme-colored squircle frame,
     * center cutout badge, optional amount highlight, and multi-UPI network footer.
     */
    fun generateCustomQrCardBitmap(
        content: String,
        payeeName: String,
        payeeVpa: String,
        amount: String? = null,
        description: String? = null,
        themeColor: Int = 0xFF00C07F.toInt(), // Default upi-easy Emerald
        width: Int = 900
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hasAmount = !amount.isNullOrBlank()
            val hasDesc = !description.isNullOrBlank()
            val cardHeight = if (hasAmount || hasDesc) 1300 else 1200
            val cardBitmap = Bitmap.createBitmap(width, cardHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(cardBitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 1. Full Card Background & Rounded Border
            val cardRect = RectF(0f, 0f, width.toFloat(), cardHeight.toFloat())
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(cardRect, 44f, 44f, paint)

            // Outer subtle card border
            paint.color = Color.argb(30, 15, 23, 42)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRoundRect(cardRect, 44f, 44f, paint)
            paint.style = Paint.Style.FILL

            // Top decorative accent line
            paint.color = themeColor
            val topAccentRect = RectF(0f, 0f, width.toFloat(), 14f)
            canvas.drawRoundRect(topAccentRect, 7f, 7f, paint)

            // 2. Verified Trust Badge Pill at top right
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(26, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
                style = Paint.Style.FILL
            }
            val pillRect = RectF(width - 320f, 44f, width - 48f, 92f)
            canvas.drawRoundRect(pillRect, 24f, 24f, pillPaint)

            paint.color = themeColor
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("0% MDR • DIRECT UPI", width - 184f, 75f, paint)

            // 3. Payee Header (Avatar Circle, Payee Trade Name, VPA)
            val avatarCenterX = 96f
            val avatarCenterY = 168f
            val avatarRadius = 46f

            // Avatar background
            paint.color = Color.argb(35, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
            paint.style = Paint.Style.FILL
            canvas.drawCircle(avatarCenterX, avatarCenterY, avatarRadius, paint)

            // Avatar initial letter
            paint.color = themeColor
            paint.textSize = 42f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val initial = (payeeName.take(1).ifBlank { "M" }).uppercase()
            val textBounds = Rect()
            paint.getTextBounds(initial, 0, 1, textBounds)
            canvas.drawText(initial, avatarCenterX, avatarCenterY + (textBounds.height() / 2f), paint)

            // Payee Trade Name
            paint.color = Color.rgb(15, 23, 42) // Dark Slate
            paint.textSize = 36f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            val displayName = if (payeeName.length > 28) payeeName.take(28) + "…" else payeeName
            canvas.drawText(displayName, 160f, 156f, paint)

            // Payee VPA Handle
            paint.color = Color.rgb(100, 116, 139) // Slate Gray
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(payeeVpa, 160f, 192f, paint)

            // 4. Optional Amount / Description Highlight Badge
            var currentY = 236f
            if (hasAmount || hasDesc) {
                val amtBoxRect = RectF(56f, currentY, width - 56f, currentY + 76f)
                val amtBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(22, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(amtBoxRect, 20f, 20f, amtBgPaint)

                // Amount Text
                paint.color = themeColor
                paint.textSize = 34f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                val amountLabel = if (hasAmount) "Paying ₹$amount" else "UPI Direct Payment"
                canvas.drawText(amountLabel, 84f, currentY + 48f, paint)

                if (hasDesc) {
                    paint.color = Color.rgb(71, 85, 105)
                    paint.textSize = 22f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.RIGHT
                    val descLabel = if (description!!.length > 22) description.take(22) + "…" else description
                    canvas.drawText(descLabel, width - 84f, currentY + 48f, paint)
                }

                currentY += 96f
            } else {
                currentY += 10f
            }

            // 5. Generate high-res QR with ErrorCorrectionLevel.H (30% redundancy for center cutout)
            val qrSize = 510
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
            }
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
            val qrPixels = IntArray(qrSize * qrSize)
            val qrForeground = Color.rgb(15, 23, 42) // Dark Slate for high contrast & elegance
            val qrBackground = Color.WHITE

            for (y in 0 until qrSize) {
                val offset = y * qrSize
                for (x in 0 until qrSize) {
                    qrPixels[offset + x] = if (bitMatrix[x, y]) qrForeground else qrBackground
                }
            }
            val rawQrBitmap = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.ARGB_8888).apply {
                setPixels(qrPixels, 0, qrSize, 0, 0, qrSize, qrSize)
            }

            // 6. Theme-Colored Squircle QR Frame
            val frameSize = 590f
            val frameLeft = (width - frameSize) / 2f
            val frameTop = currentY
            val frameRect = RectF(frameLeft, frameTop, frameLeft + frameSize, frameTop + frameSize)

            // Inner white fill
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(frameRect, 48f, 48f, paint)

            // Thick theme colored border
            paint.color = themeColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 14f
            canvas.drawRoundRect(frameRect, 48f, 48f, paint)
            paint.style = Paint.Style.FILL

            // Draw QR code inside frame
            val qrLeft = frameLeft + ((frameSize - qrSize) / 2f)
            val qrTop = frameTop + ((frameSize - qrSize) / 2f)
            canvas.drawBitmap(rawQrBitmap, qrLeft, qrTop, null)

            // 7. Proper Center Cutout with upi-easy / UPI Emblem
            val qrCenterX = qrLeft + (qrSize / 2f)
            val qrCenterY = qrTop + (qrSize / 2f)
            val cutoutRadiusOuter = 58f
            val cutoutRadiusInner = 48f

            // Clean white circular backdrop to wipe out QR modules
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(qrCenterX, qrCenterY, cutoutRadiusOuter, paint)

            // Themed circular badge
            paint.color = themeColor
            canvas.drawCircle(qrCenterX, qrCenterY, cutoutRadiusInner, paint)

            // Crisp white inner ring
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(qrCenterX, qrCenterY, cutoutRadiusInner, paint)
            paint.style = Paint.Style.FILL

            // Indian Rupee Symbol '₹' in center of cutout badge
            paint.color = Color.WHITE
            paint.textSize = 46f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val symbolBounds = Rect()
            paint.getTextBounds("₹", 0, 1, symbolBounds)
            canvas.drawText("₹", qrCenterX, qrCenterY + (symbolBounds.height() / 2f), paint)

            // 8. Footer Section (Payment Networks & Trust Marks)
            val footerY = frameTop + frameSize + 48f

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 26f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Scan & Pay with any UPI App", width / 2f, footerY, paint)

            paint.color = Color.rgb(100, 116, 139)
            paint.textSize = 21f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Google Pay • PhonePe • Paytm • BHIM • Cred • Any Bank UPI", width / 2f, footerY + 36f, paint)

            // Bottom trust mark
            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 17f
            canvas.drawText("⚡ Direct Bank Settlement • Instant Notification Alerts", width / 2f, footerY + 70f, paint)

            cardBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareQr(
        context: Context,
        bitmap: Bitmap?,
        textMessage: String,
        title: String = "Share Payment Request"
    ) {
        if (bitmap == null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textMessage)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
            return
        }

        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "upi_upieasy_qr_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, textMessage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            e.printStackTrace()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textMessage)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        }
    }
}
