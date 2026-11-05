package com.example.ratecalc

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object InvoiceImage {
    private fun fit(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    fun render(items: List<Product>): Bitmap {
        val w = 1080
        val pad = 56f
        val rowH = 84f
        val firstRowY = 392f
        val h = (firstRowY + items.size * rowH + 130f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 3f }

        p.color = Color.BLACK
        p.textSize = 96f
        p.textAlign = Paint.Align.CENTER
        c.drawText("INVOICE", w / 2f, 150f, p)

        val xRate = 620f
        val xQty = 830f
        val xTot = w - pad
        p.textSize = 44f
        p.color = Color.DKGRAY
        p.textAlign = Paint.Align.LEFT
        c.drawText("Item", pad, 290f, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("Rate", xRate, 290f, p)
        c.drawText("Qty", xQty, 290f, p)
        c.drawText("Total", xTot, 290f, p)
        c.drawLine(pad, 322f, w - pad, 322f, line)

        p.color = Color.BLACK
        var y = firstRowY
        items.forEach { it ->
            p.textAlign = Paint.Align.LEFT
            c.drawText(fit(it.name, p, 380f), pad, y, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText(money(it.rate), xRate, y, p)
            c.drawText(Calc.qtyLabel(it), xQty, y, p)
            c.drawText(money(Calc.lineTotal(it)), xTot, y, p)
            y += rowH
        }
        c.drawLine(pad, y - 40f, w - pad, y - 40f, line)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 60f
        p.textAlign = Paint.Align.RIGHT
        c.drawText("Grand Total: " + money(items.sumOf { Calc.lineTotal(it) }), xTot, y + 40f, p)
        return bmp
    }

    /** Folder that holds the saved history PNGs. */
    fun dir(ctx: Context): File = File(ctx.filesDir, "invoices").apply { mkdirs() }

    /** Renders the invoice, saves it as Invoice_yyyyMMdd_HHmmss.png in the history folder (and the gallery). */
    fun savePng(ctx: Context, items: List<Product>): File {
        val name = "Invoice_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val f = File(dir(ctx), name)
        FileOutputStream(f).use { render(items).compress(Bitmap.CompressFormat.PNG, 100, it) }
        try { copyToGallery(ctx, f) } catch (e: Exception) { /* history copy is what matters */ }
        return f
    }

    /** Copies a PNG into Pictures/RateCalculator (Android 10+; no permission needed). */
    fun copyToGallery(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RateCalculator")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }

    fun shareFile(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share invoice"))
    }

    fun share(ctx: Context, items: List<Product>) {
        val bmp = render(items)
        val dir = File(ctx.cacheDir, "invoices").apply { mkdirs() }
        val f = File(dir, "invoice.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share invoice"))
    }
}
