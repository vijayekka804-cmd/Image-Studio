package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object PdfProcessor {
    private const val TAG = "PdfProcessor"

    // Page sizes in PostScript points (72 points = 1 inch)
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val LETTER_WIDTH = 612
    private const val LETTER_HEIGHT = 792

    /**
     * Converts a list of image Uris to a PDF file locally.
     */
    fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        pageSize: String, // "A4", "LETTER", "ORIGINAL"
        isPortrait: Boolean,
        margin: Int, // in points
        quality: Int // 0-100
    ): File {
        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "pdf_${UUID.randomUUID()}.pdf")

        try {
            for (uri in imageUris) {
                val bitmap = ImageProcessor.loadBitmap(context, uri, 1500) ?: continue

                // Determine target page width and height
                val (pageWidth, pageHeight) = when (pageSize.uppercase()) {
                    "A4" -> if (isPortrait) Pair(A4_WIDTH, A4_HEIGHT) else Pair(A4_HEIGHT, A4_WIDTH)
                    "LETTER" -> if (isPortrait) Pair(LETTER_WIDTH, LETTER_HEIGHT) else Pair(LETTER_HEIGHT, LETTER_WIDTH)
                    else -> { // ORIGINAL
                        if (isPortrait) Pair(bitmap.width, bitmap.height) else Pair(bitmap.height, bitmap.width)
                    }
                }

                // Create page info
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Calculate image dimensions with margin
                val maxImgWidth = pageWidth - (margin * 2)
                val maxImgHeight = pageHeight - (margin * 2)

                val scaleX = maxImgWidth.toFloat() / bitmap.width
                val scaleY = maxImgHeight.toFloat() / bitmap.height
                val scale = minOf(scaleX, scaleY).coerceAtMost(1.0f)

                val imgWidth = (bitmap.width * scale).toInt()
                val imgHeight = (bitmap.height * scale).toInt()

                // Center image
                val left = margin + (maxImgWidth - imgWidth) / 2
                val top = margin + (maxImgHeight - imgHeight) / 2

                val scaledBitmap = if (scale == 1.0f) bitmap else Bitmap.createScaledBitmap(bitmap, imgWidth, imgHeight, true)

                val paint = Paint().apply { isFilterBitmap = true }
                canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), paint)

                pdfDocument.finishPage(page)

                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
            }

            FileOutputStream(tempFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        return tempFile
    }

    /**
     * Converts a list of image Uris to a PDF file locally, attempting to target a specific file size.
     */
    fun imagesToPdfWithTargetSize(
        context: Context,
        imageUris: List<Uri>,
        pageSize: String, // "A4", "LETTER", "ORIGINAL"
        isPortrait: Boolean,
        margin: Int, // in points
        targetSizeInBytes: Long
    ): File {
        var currentFile: File? = null
        val candidates = listOf(
            Pair(1500, 85),  // High quality
            Pair(1200, 75),  // Good quality
            Pair(1000, 65),  // Balanced
            Pair(800, 55),   // More compressed
            Pair(600, 45),   // Medium compressed
            Pair(400, 35)    // Maximum compressed
        )
        
        for (i in candidates.indices) {
            val (maxDimension, quality) = candidates[i]
            val tempFile = File(context.cacheDir, "pdf_target_temp_${UUID.randomUUID()}.pdf")
            val pdfDocument = PdfDocument()
            
            try {
                for (uri in imageUris) {
                    val bitmap = ImageProcessor.loadBitmap(context, uri, maxDimension) ?: continue
                    
                    // Compress image bitmap at candidate quality to reduce embedded asset size
                    val compressedStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, compressedStream)
                    val compressedBytes = compressedStream.toByteArray()
                    val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                    // Determine target page width and height
                    val (pageWidth, pageHeight) = when (pageSize.uppercase()) {
                        "A4" -> if (isPortrait) Pair(A4_WIDTH, A4_HEIGHT) else Pair(A4_HEIGHT, A4_WIDTH)
                        "LETTER" -> if (isPortrait) Pair(LETTER_WIDTH, LETTER_HEIGHT) else Pair(LETTER_HEIGHT, LETTER_WIDTH)
                        else -> { // ORIGINAL
                            if (isPortrait) Pair(compressedBitmap.width, compressedBitmap.height) else Pair(compressedBitmap.height, compressedBitmap.width)
                        }
                    }

                    // Create page info
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // Calculate image dimensions with margin
                    val maxImgWidth = pageWidth - (margin * 2)
                    val maxImgHeight = pageHeight - (margin * 2)

                    val scaleX = maxImgWidth.toFloat() / compressedBitmap.width
                    val scaleY = maxImgHeight.toFloat() / compressedBitmap.height
                    val scale = minOf(scaleX, scaleY).coerceAtMost(1.0f)

                    val imgWidth = (compressedBitmap.width * scale).toInt()
                    val imgHeight = (compressedBitmap.height * scale).toInt()

                    // Center image
                    val left = margin + (maxImgWidth - imgWidth) / 2
                    val top = margin + (maxImgHeight - imgHeight) / 2

                    val scaledBitmap = if (scale == 1.0f) compressedBitmap else Bitmap.createScaledBitmap(compressedBitmap, imgWidth, imgHeight, true)

                    val paint = Paint().apply { isFilterBitmap = true }
                    canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), paint)

                    pdfDocument.finishPage(page)

                    if (scaledBitmap != compressedBitmap) {
                        scaledBitmap.recycle()
                    }
                    compressedBitmap.recycle()
                    bitmap.recycle()
                }

                FileOutputStream(tempFile).use { out ->
                    pdfDocument.writeTo(out)
                }
                
                pdfDocument.close()
                val currentSize = tempFile.length()
                currentFile = tempFile
                
                if (currentSize <= targetSizeInBytes || i == candidates.size - 1) {
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
                pdfDocument.close()
            }
        }
        
        return currentFile ?: File(context.cacheDir, "pdf_empty.pdf").apply { createNewFile() }
    }

    /**
     * Converts selected pages of a PDF to independent image files.
     */
    fun pdfToImages(
        context: Context,
        pdfUri: Uri,
        selectedPages: List<Int>, // 0-indexed page list
        format: String, // "JPG" or "PNG"
        quality: Int
    ): List<File> {
        val outputFiles = mutableListOf<File>()
        val resolver = context.contentResolver

        resolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                val pagesToProcess = if (selectedPages.isEmpty()) {
                    (0 until renderer.pageCount).toList()
                } else {
                    selectedPages.filter { it in 0 until renderer.pageCount }
                }

                for (pageIndex in pagesToProcess) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        // Render at higher DPI by scaling up the viewport size (e.g. 2x scale)
                        val scale = 2f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // Clear to White background (PDF defaults to white, render fills transparent otherwise)
                        bitmap.eraseColor(android.graphics.Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val ext = format.lowercase()
                        val file = File(context.cacheDir, "pdf_page_${pageIndex + 1}_${UUID.randomUUID()}.$ext")
                        FileOutputStream(file).use { out ->
                            val compressFormat = if (format == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            bitmap.compress(compressFormat, quality, out)
                        }
                        outputFiles.add(file)
                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
        return outputFiles
    }

    /**
     * Compresses PDF locally by scaling down page resolutions and applying compressed image encoding.
     */
    fun compressPdf(
        context: Context,
        pdfUri: Uri,
        compressionMode: String // "MAX", "BALANCED", "HIGH"
    ): File {
        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.pdf")
        val resolver = context.contentResolver

        // Parameters based on mode
        val (scale, jpegQuality) = when (compressionMode.uppercase()) {
            "MAX" -> Pair(1.0f, 40)       // Low resolution, low quality
            "BALANCED" -> Pair(1.4f, 65)  // Mid resolution, medium quality
            else -> Pair(1.8f, 85)         // High resolution, high quality
        }

        resolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Compress bitmap as JPEG to reduce file footprint
                        val compressedStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, compressedStream)
                        val compressedBytes = compressedStream.toByteArray()
                        val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                        // Draw compressed image back onto page canvas
                        val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageIndex + 1).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        val canvas = newPage.canvas

                        val paint = Paint().apply { isFilterBitmap = true }
                        val dstRect = android.graphics.Rect(0, 0, page.width, page.height)
                        canvas.drawBitmap(compressedBitmap, null, dstRect, paint)

                        pdfDocument.finishPage(newPage)

                        bitmap.recycle()
                        compressedBitmap.recycle()
                    } finally {
                        page.close()
                    }
                }

                FileOutputStream(tempFile).use { out ->
                    pdfDocument.writeTo(out)
                }
            } finally {
                renderer.close()
                pdfDocument.close()
            }
        }
        return tempFile
    }

    /**
     * Compresses PDF locally while attempting to target a specific file size limit in bytes.
     */
    fun compressPdfToTargetSize(
        context: Context,
        pdfUri: Uri,
        targetSizeInBytes: Long
    ): File {
        var currentFile: File? = null
        val candidates = listOf(
            Pair(1.8f, 85),  // High quality
            Pair(1.5f, 75),  // Good quality
            Pair(1.3f, 65),  // Balanced
            Pair(1.1f, 55),  // More compressed
            Pair(0.9f, 45),  // Medium compressed
            Pair(0.7f, 35)   // Maximum compressed
        )
        
        for (i in candidates.indices) {
            val (scale, quality) = candidates[i]
            val tempFile = File(context.cacheDir, "compressed_target_temp_${UUID.randomUUID()}.pdf")
            val pdfDocument = PdfDocument()
            val resolver = context.contentResolver
            
            try {
                resolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    try {
                        for (pageIndex in 0 until renderer.pageCount) {
                            val page = renderer.openPage(pageIndex)
                            try {
                                val width = (page.width * scale).toInt().coerceAtLeast(100)
                                val height = (page.height * scale).toInt().coerceAtLeast(100)
                                
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                
                                val compressedStream = java.io.ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, compressedStream)
                                val compressedBytes = compressedStream.toByteArray()
                                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                                
                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageIndex + 1).create()
                                val newPage = pdfDocument.startPage(pageInfo)
                                val canvas = newPage.canvas
                                
                                val paint = Paint().apply { isFilterBitmap = true }
                                val dstRect = android.graphics.Rect(0, 0, page.width, page.height)
                                canvas.drawBitmap(compressedBitmap, null, dstRect, paint)
                                
                                pdfDocument.finishPage(newPage)
                                bitmap.recycle()
                                compressedBitmap.recycle()
                            } finally {
                                page.close()
                            }
                        }
                        
                        FileOutputStream(tempFile).use { out ->
                            pdfDocument.writeTo(out)
                        }
                    } finally {
                        renderer.close()
                        pdfDocument.close()
                    }
                }
                
                val currentSize = tempFile.length()
                currentFile = tempFile
                
                if (currentSize <= targetSizeInBytes || i == candidates.size - 1) {
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return currentFile ?: throw Exception("Failed to compress PDF to target size")
    }

    /**
     * Merges a list of PDF files locally.
     */
    fun mergePdfs(context: Context, pdfUris: List<Uri>): File {
        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "merged_${UUID.randomUUID()}.pdf")
        val resolver = context.contentResolver

        var currentPageIndex = 0
        try {
            for (uri in pdfUris) {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    try {
                        for (pageIndex in 0 until renderer.pageCount) {
                            val page = renderer.openPage(pageIndex)
                            try {
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, currentPageIndex + 1).create()
                                val newPage = pdfDocument.startPage(pageInfo)
                                val canvas = newPage.canvas

                                canvas.drawBitmap(bitmap, 0f, 0f, Paint().apply { isFilterBitmap = true })
                                pdfDocument.finishPage(newPage)

                                bitmap.recycle()
                                currentPageIndex++
                            } finally {
                                page.close()
                            }
                        }
                    } finally {
                        renderer.close()
                    }
                }
            }

            FileOutputStream(tempFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        return tempFile
    }

    /**
     * Splits a PDF file, keeping only selected pages.
     */
    fun splitPdf(context: Context, pdfUri: Uri, keepPages: List<Int>): File {
        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "split_${UUID.randomUUID()}.pdf")
        val resolver = context.contentResolver

        resolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                var newPageIndex = 0
                for (pageIndex in keepPages) {
                    if (pageIndex !in 0 until renderer.pageCount) continue
                    val page = renderer.openPage(pageIndex)
                    try {
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, newPageIndex + 1).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        val canvas = newPage.canvas

                        canvas.drawBitmap(bitmap, 0f, 0f, Paint().apply { isFilterBitmap = true })
                        pdfDocument.finishPage(newPage)

                        bitmap.recycle()
                        newPageIndex++
                    } finally {
                        page.close()
                    }
                }

                FileOutputStream(tempFile).use { out ->
                    pdfDocument.writeTo(out)
                }
            } finally {
                renderer.close()
                pdfDocument.close()
            }
        }
        return tempFile
    }

    /**
     * Rotates pages of a PDF file locally.
     */
    fun rotatePdf(
        context: Context,
        pdfUri: Uri,
        rotationAngle: Int, // 90, 180, 270
        selectedPages: List<Int> // empty means all
    ): File {
        val pdfDocument = PdfDocument()
        val tempFile = File(context.cacheDir, "rotated_${UUID.randomUUID()}.pdf")
        val resolver = context.contentResolver

        resolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        val shouldRotate = selectedPages.isEmpty() || pageIndex in selectedPages

                        // Calculate dimension changes if rotated by 90 or 270 degrees
                        val isSwapDim = shouldRotate && (rotationAngle == 90 || rotationAngle == 270)
                        val pageW = if (isSwapDim) page.height else page.width
                        val pageH = if (isSwapDim) page.width else page.height

                        // Render original page to bitmap (use high resolution scale for crisp output)
                        val scale = 1.5f
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // If rotating, apply matrix to bitmap
                        val finalBitmap = if (shouldRotate) {
                            val matrix = Matrix().apply { postRotate(rotationAngle.toFloat()) }
                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        } else {
                            bitmap
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        val canvas = newPage.canvas

                        val dstRect = android.graphics.Rect(0, 0, pageW, pageH)
                        canvas.drawBitmap(finalBitmap, null, dstRect, Paint().apply { isFilterBitmap = true })
                        pdfDocument.finishPage(newPage)

                        if (finalBitmap != bitmap) {
                            finalBitmap.recycle()
                        }
                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                }

                FileOutputStream(tempFile).use { out ->
                    pdfDocument.writeTo(out)
                }
            } finally {
                renderer.close()
                pdfDocument.close()
            }
        }
        return tempFile
    }

    /**
     * Gets thumbnails (Bitmaps) of all pages in a PDF for previewing.
     */
    fun getPdfThumbnails(context: Context, pdfUri: Uri, maxPages: Int = 100): List<Bitmap> {
        val thumbnails = mutableListOf<Bitmap>()
        val resolver = context.contentResolver

        try {
            resolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    val count = minOf(renderer.pageCount, maxPages)
                    for (i in 0 until count) {
                        val page = renderer.openPage(i)
                        try {
                            // Low-res thumbnails (DPI scale ~0.4)
                            val scale = 0.4f
                            val width = (page.width * scale).toInt().coerceAtLeast(120)
                            val height = (page.height * scale).toInt().coerceAtLeast(160)

                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            thumbnails.add(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error generating page thumbnail: $i", e)
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load PDF thumbnails", e)
        }
        return thumbnails
    }

    /**
     * Gets total pages in a PDF.
     */
    fun getPageCount(context: Context, pdfUri: Uri): Int {
        var count = 0
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                count = renderer.pageCount
                renderer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page count", e)
        }
        return count
    }

    /**
     * Helper to save processed PDF to the public downloads folder via MediaStore.
     */
    fun savePdfToDownloads(context: Context, file: File, desiredName: String): Uri? {
        val finalFileName = if (desiredName.contains(".")) desiredName else "$desiredName.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ImageStudio")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.getContentUri("external")
        }

        var pdfUri: Uri? = null
        try {
            pdfUri = resolver.insert(collectionUri, contentValues)
            if (pdfUri != null) {
                resolver.openOutputStream(pdfUri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(pdfUri, contentValues, null, null)
                }
                return pdfUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF to Downloads", e)
            if (pdfUri != null) {
                try {
                    resolver.delete(pdfUri, null, null)
                } catch (de: Exception) {
                    // ignore
                }
            }
        }
        return null
    }
}
