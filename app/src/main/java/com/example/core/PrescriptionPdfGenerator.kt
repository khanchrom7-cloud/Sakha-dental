package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.PrescriptionRecord
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PrescriptionPdfGenerator {

    fun generatePdfFile(
        context: Context,
        prescription: PrescriptionRecord,
        clinicNameEn: String,
        clinicNameFa: String,
        clinicPhone: String,
        clinicAddress: String
    ): File {
        val dir = File(context.cacheDir, "prescriptions").apply { mkdirs() }
        val safeId = prescription.id.ifBlank { System.currentTimeMillis().toString() }
        val file = File(dir, "Sakha_Prescription_$safeId.pdf")

        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait @ 72dpi
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

        val royalBlue = Color.rgb(10, 36, 67)
        val gold = Color.rgb(201, 162, 77)
        val slate = Color.rgb(93, 107, 122)

        val headerPaint = Paint().apply {
            color = royalBlue
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 595f, 118f, headerPaint)

        val goldLinePaint = Paint().apply {
            color = gold
            strokeWidth = 4f
        }
        canvas.drawLine(0f, 118f, 595f, 118f, goldLinePaint)

        // Clinic Logo (genuine sakha_logo drawable)
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.sakha_logo)
        }.getOrNull()
        if (logoBitmap != null) {
            val logoBg = Paint().apply { color = Color.WHITE }
            canvas.drawRoundRect(28f, 18f, 108f, 98f, 10f, 10f, logoBg)
            canvas.drawBitmap(
                logoBitmap,
                null,
                Rect(32, 22, 104, 94),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(245, 236, 212)
            textSize = 13f
            isAntiAlias = true
        }
        val subLightPaint = Paint().apply {
            color = Color.rgb(220, 226, 232)
            textSize = 10.5f
            isAntiAlias = true
        }

        canvas.drawText(clinicNameEn, 124f, 44f, titlePaint)
        canvas.drawText(clinicNameFa, 124f, 66f, subtitlePaint)
        canvas.drawText("Dr. Ghulam Sediq Sakha | BDS, RDS — Oral & Dental Specialist", 124f, 86f, subLightPaint)
        canvas.drawText("Tel: $clinicPhone  |  $clinicAddress", 124f, 103f, subLightPaint)

        // Patient Information Box
        val boxPaint = Paint().apply {
            color = Color.rgb(244, 246, 249)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(191, 197, 201)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(32f, 138f, 563f, 208f, 8f, 8f, boxPaint)
        canvas.drawRoundRect(32f, 138f, 563f, 208f, 8f, 8f, borderPaint)

        val labelPaint = Paint().apply {
            color = royalBlue
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            isAntiAlias = true
        }

        canvas.drawText("Patient: ${prescription.patientName}", 48f, 164f, labelPaint)
        canvas.drawText("Age: ${prescription.patientAge} yrs", 340f, 164f, bodyPaint)
        canvas.drawText("Doctor: ${prescription.doctorName}", 48f, 188f, bodyPaint)
        canvas.drawText("Date: ${prescription.visitDate}", 340f, 188f, bodyPaint)

        // Rx Symbol
        val rxPaint = Paint().apply {
            color = royalBlue
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            isAntiAlias = true
        }
        canvas.drawText("Rx", 36f, 246f, rxPaint)
        canvas.drawLine(36f, 256f, 560f, 256f, borderPaint)

        var y = 284f
        if (prescription.medicines.isEmpty()) {
            canvas.drawText("No medicines prescribed — clinical instructions only.", 42f, y, bodyPaint)
            y += 28f
        } else {
            prescription.medicines.forEachIndexed { idx, item ->
                canvas.drawText(
                    "${idx + 1}. ${item.medicineName}  (${item.dosage})  — Qty: ${item.quantity}",
                    42f,
                    y,
                    labelPaint
                )
                y += 18f
                val mealText = if (item.mealTiming.isNotBlank()) " | ${item.mealTiming}" else ""
                canvas.drawText(
                    "   Frequency: ${item.frequency} | Duration: ${item.duration}$mealText",
                    42f,
                    y,
                    bodyPaint
                )
                y += 16f
                if (item.instructions.isNotBlank()) {
                    canvas.drawText("   Notes: ${item.instructions}", 42f, y, bodyPaint.apply { color = slate })
                    bodyPaint.color = Color.DKGRAY
                    y += 16f
                }
                y += 10f
            }
        }

        if (prescription.notes.isNotBlank()) {
            y += 10f
            canvas.drawText("Clinical Notes:", 36f, y, labelPaint)
            y += 18f
            prescription.notes.chunked(82).forEach { line ->
                canvas.drawText(line, 42f, y, bodyPaint)
                y += 16f
            }
        }

        // Genuine Doctor Signature Block at bottom right
        val sigTop = 665f
        canvas.drawLine(36f, sigTop - 16f, 560f, sigTop - 16f, borderPaint)
        val sigBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.doctor_signature)
        }.getOrNull()
        if (sigBitmap != null) {
            val scaledHeight = 64
            val scaledWidth = (sigBitmap.width.toFloat() / sigBitmap.height.toFloat() * scaledHeight).toInt().coerceAtMost(180)
            val scaled = Bitmap.createScaledBitmap(sigBitmap, scaledWidth, scaledHeight, true)
            canvas.drawBitmap(scaled, 370f, sigTop, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        canvas.drawText("Doctor Signature & Stamp", 370f, sigTop + 82f, labelPaint)
        canvas.drawText("Dr. Ghulam Sediq Sakha", 370f, sigTop + 98f, bodyPaint)
        canvas.drawText("Sakha Specialised Dental Centre — Sheberghan", 36f, sigTop + 98f, bodyPaint)

            doc.finishPage(page)
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            doc.close()
        } catch (_: IllegalStateException) {
            runCatching { doc.close() }
            file.writeBytes(buildFallbackPdfBytes(clinicNameEn, clinicPhone, prescription))
        }
        return file
    }

    private fun buildFallbackPdfBytes(
        clinicNameEn: String,
        clinicPhone: String,
        prescription: PrescriptionRecord
    ): ByteArray {
        val summary = buildString {
            append(clinicNameEn).append(" | Tel: ").append(clinicPhone)
            append(" | Patient: ").append(prescription.patientName)
            append(" | Doctor: ").append(prescription.doctorName)
            append(" | Items: ").append(prescription.medicines.joinToString("; ") { "${it.medicineName} ${it.dosage}" })
        }.replace("(", "[").replace(")", "]")
        val contentStream = "BT /F1 12 Tf 40 780 Td ($summary) Tj ET"
        val pdf = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
            append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n")
            append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n")
            append("4 0 obj << /Length ${contentStream.length} >> stream\n$contentStream\nendstream endobj\n")
            append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")
            append("%%EOF\n")
        }
        return pdf.toByteArray(Charsets.UTF_8)
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Prescription PDF"))
    }

    fun printPdf(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true || destination == null) {
                    callback?.onWriteCancelled()
                    return
                }
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
