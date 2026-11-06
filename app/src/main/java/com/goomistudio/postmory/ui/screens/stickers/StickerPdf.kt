package com.goomistudio.postmory.ui.screens.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Builds the sticker PDF one page at a time (each page is a picture of the sheet, drawn by Compose
 * at print resolution) so only one page's pixels are in memory at once. Platform PdfDocument: no
 * new dependency.
 */
class StickerPdf(private val paper: SheetPaper) {
    private val doc = PdfDocument()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    var pageCount = 0
        private set

    /** Adds [bitmap] as the next page, stretched to the paper (it was drawn at the paper's shape). */
    fun addPage(bitmap: Bitmap) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, pageCount + 1).create())
        page.canvas.drawBitmap(bitmap, null, Rect(0, 0, paper.widthPt, paper.heightPt), paint)
        doc.finishPage(page)
        pageCount++
    }

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { doc.writeTo(it) }
    }

    fun close() = doc.close()

    companion object {
        /** Print resolution of the page pictures (plenty for stickers, keeps a page near 15 MB in memory). */
        const val Dpi = 200
        private const val MmPerInch = 25.4f

        fun widthPx(paper: SheetPaper) = (paper.widthMm / MmPerInch * Dpi).toInt()
        fun heightPx(paper: SheetPaper) = (paper.heightMm / MmPerInch * Dpi).toInt()

        /** Where the PDF is written before printing (cache: Android may clear it, and that's fine). */
        fun file(context: Context, name: String) = File(File(context.cacheDir, "print"), name)
    }
}

/**
 * Opens the system print dialog on a finished PDF. Its printer list includes "Save as PDF", so the
 * one button both prints and saves. The paper is preselected; the PDF already has its pages.
 */
fun printPdf(context: Context, file: File, jobName: String, pages: Int, paper: SheetPaper) {
    val manager = context.getSystemService(PrintManager::class.java) ?: return
    val attributes = PrintAttributes.Builder()
        .setMediaSize(if (paper == SheetPaper.A4) PrintAttributes.MediaSize.ISO_A4 else PrintAttributes.MediaSize.NA_LETTER)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()
    manager.print(jobName, PdfFileAdapter(file, jobName, pages), attributes)
}

/** Hands the print system a ready PDF file as is. */
private class PdfFileAdapter(private val file: File, private val name: String, private val pages: Int) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages)
            .build()
        callback.onLayoutFinished(info, oldAttributes == null)
    }

    override fun onWrite(
        pageRanges: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        runCatching {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
        }.onSuccess {
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }.onFailure {
            callback.onWriteFailed(it.message)
        }
    }
}
