package com.example.filemanagementapp.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

object PdfEditHelper {

    data class PageModification(
        val originalIndex: Int,
        var rotation: Int = 0,
        var isDeleted: Boolean = false,
        var replacementImageFile: File? = null
    )

    fun getPageCount(pdfFile: File): Int {
        if (!pdfFile.exists()) return 0
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        renderer.close()
        pfd.close()
        return count
    }

    fun renderPageToBitmap(
        pdfFile: File,
        pageIndex: Int,
        renderScale: Float = 2.0f
    ): Bitmap {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val page = renderer.openPage(pageIndex)
            val width = (page.width * renderScale).toInt().coerceAtLeast(1)
            val height = (page.height * renderScale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    fun renderPageToFile(
        pdfFile: File,
        pageIndex: Int,
        outputImageFile: File,
        renderScale: Float = 2.0f
    ): Result<File> = runCatching {
        val bitmap = renderPageToBitmap(pdfFile, pageIndex, renderScale)
        FileOutputStream(outputImageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        outputImageFile
    }

    fun applyPageModifications(
        sourceFile: File,
        outputFile: File,
        modifications: List<PageModification>,
        renderScale: Float = 2.0f
    ): Result<Unit> = runCatching {
        val activeMods = modifications.filter { !it.isDeleted }
        if (activeMods.isEmpty()) {
            throw IllegalArgumentException("Cannot create an empty PDF document")
        }

        val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val document = PdfDocument()

        try {
            var newPageNum = 1
            for (mod in activeMods) {
                val sourcePage = renderer.openPage(mod.originalIndex)
                val origWidth = sourcePage.width
                val origHeight = sourcePage.height

                val replacementFile = mod.replacementImageFile
                if (replacementFile != null && replacementFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(replacementFile.absolutePath)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(origWidth, origHeight, newPageNum++).create()
                        val page = document.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, null, Rect(0, 0, origWidth, origHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                        document.finishPage(page)
                        bitmap.recycle()
                    }
                    sourcePage.close()
                } else {
                    val bmpWidth = (origWidth * renderScale).toInt().coerceAtLeast(1)
                    val bmpHeight = (origHeight * renderScale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    sourcePage.close()

                    val normRotation = ((mod.rotation % 360) + 360) % 360
                    val isSwapped = normRotation == 90 || normRotation == 270
                    val outWidth = if (isSwapped) origHeight else origWidth
                    val outHeight = if (isSwapped) origWidth else origHeight

                    val pageInfo = PdfDocument.PageInfo.Builder(outWidth, outHeight, newPageNum++).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.save()
                    when (normRotation) {
                        90 -> {
                            canvas.translate(origHeight.toFloat(), 0f)
                            canvas.rotate(90f)
                        }
                        180 -> {
                            canvas.translate(origWidth.toFloat(), origHeight.toFloat())
                            canvas.rotate(180f)
                        }
                        270 -> {
                            canvas.translate(0f, origWidth.toFloat())
                            canvas.rotate(270f)
                        }
                    }
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, origWidth, origHeight), Paint(Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()

                    document.finishPage(page)
                    bitmap.recycle()
                }
            }

            FileOutputStream(outputFile).use { outStream ->
                document.writeTo(outStream)
            }
        } finally {
            document.close()
            renderer.close()
            pfd.close()
        }
    }

    fun extractPages(
        sourceFile: File,
        outputFile: File,
        selectedIndices: List<Int>,
        rotations: Map<Int, Int> = emptyMap(),
        renderScale: Float = 2.0f
    ): Result<Unit> {
        val mods = selectedIndices.map { idx ->
            PageModification(
                originalIndex = idx,
                rotation = rotations[idx] ?: 0,
                isDeleted = false
            )
        }
        return applyPageModifications(sourceFile, outputFile, mods, renderScale)
    }

    fun mergePdfs(
        pdfFiles: List<File>,
        outputFile: File,
        renderScale: Float = 2.0f
    ): Result<Unit> = runCatching {
        if (pdfFiles.isEmpty()) {
            throw IllegalArgumentException("No PDF files to merge")
        }

        val document = PdfDocument()
        var newPageNum = 1

        try {
            for (file in pdfFiles) {
                if (!file.exists()) continue
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                try {
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val width = page.width
                        val height = page.height
                        val bmpWidth = (width * renderScale).toInt().coerceAtLeast(1)
                        val bmpHeight = (height * renderScale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageNum++).create()
                        val docPage = document.startPage(pageInfo)
                        docPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                        document.finishPage(docPage)
                        bitmap.recycle()
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }
            }

            if (newPageNum == 1) {
                throw IllegalStateException("No pages found in provided PDF files")
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
    }
}
