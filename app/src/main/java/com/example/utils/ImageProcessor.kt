package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class ImageMetadata(
    val fileName: String,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val format: String,
    val resolution: String
)

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    fun getMetadata(context: Context, uri: Uri): ImageMetadata? {
        var fileName = "unknown_image"
        var fileSize = 0L
        
        // Query filename and size from resolver
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)
                val width = options.outWidth
                val height = options.outHeight
                val mimeType = options.outMimeType ?: "image/jpeg"
                val format = when {
                    mimeType.contains("png", true) -> "PNG"
                    mimeType.contains("webp", true) -> "WEBP"
                    else -> "JPG"
                }
                return ImageMetadata(
                    fileName = fileName,
                    fileSize = fileSize,
                    width = width,
                    height = height,
                    format = format,
                    resolution = "${width} x ${height}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading metadata", e)
        }
        return null
    }

    fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            var options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            var srcWidth = options.outWidth
            var srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            // Calculate inSampleSize
            var sampleSize = 1
            while (srcWidth / sampleSize > maxDim || srcHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }

            var bitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                bitmap = BitmapFactory.decodeStream(input, null, options)
            }
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap", e)
        }
        return null
    }

    private fun getOutputFormat(formatName: String): Bitmap.CompressFormat {
        return when (formatName.uppercase()) {
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun createTempFile(context: Context, extension: String): File {
        val outputDir = context.cacheDir
        return File.createTempFile("edited_${UUID.randomUUID()}", ".$extension", outputDir)
    }

    // FEATURE 1: COMPRESSION MODES
    fun compressLossless(context: Context, uri: Uri): File {
        val metadata = getMetadata(context, uri)
        val formatStr = metadata?.format ?: "JPG"
        val extension = formatStr.lowercase()
        val tempFile = createTempFile(context, extension)
        
        val bitmap = loadBitmap(context, uri, 3072) ?: throw Exception("Failed to load image")
        FileOutputStream(tempFile).use { out ->
            if (formatStr == "PNG") {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else if (formatStr == "WEBP") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
                }
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
        return tempFile
    }

    fun compressSmart(context: Context, uri: Uri): File {
        val metadata = getMetadata(context, uri)
        val formatStr = metadata?.format ?: "JPG"
        val extension = formatStr.lowercase()
        val tempFile = createTempFile(context, extension)
        
        val bitmap = loadBitmap(context, uri, 2048) ?: throw Exception("Failed to load image")
        FileOutputStream(tempFile).use { out ->
            if (formatStr == "PNG") {
                // PNG doesn't support quality loss directly, so we compress slightly or convert to lossy WEBP if PNG is large,
                // but let's compress with PNG (which is slow but lossless) or convert slightly.
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            } else {
                val format = getOutputFormat(formatStr)
                bitmap.compress(format, 75, out)
            }
        }
        return tempFile
    }

    fun compressMax(context: Context, uri: Uri): File {
        val metadata = getMetadata(context, uri)
        val formatStr = metadata?.format ?: "JPG"
        val extension = formatStr.lowercase()
        val tempFile = createTempFile(context, extension)
        
        // Downscale image dimensions to 50% for Max Compression
        val originalBitmap = loadBitmap(context, uri, 1500) ?: throw Exception("Failed to load image")
        val scaledWidth = (originalBitmap.width * 0.5).toInt().coerceAtLeast(1)
        val scaledHeight = (originalBitmap.height * 0.5).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
        
        FileOutputStream(tempFile).use { out ->
            val format = getOutputFormat(formatStr)
            bitmap.compress(format, 45, out)
        }
        return tempFile
    }

    // FEATURE 2: TARGET SIZE COMPRESSION
    fun compressToTargetSize(context: Context, uri: Uri, targetSizeKb: Int): File {
        val targetBytes = targetSizeKb * 1024L
        val originalBitmap = loadBitmap(context, uri, 2560) ?: throw Exception("Failed to load image")
        
        var quality = 90
        var scale = 1.0f
        var currentFile: File? = null
        
        // Iteratively try to downscale size & quality to hit the target
        for (i in 0..15) {
            val width = (originalBitmap.width * scale).toInt().coerceAtLeast(100)
            val height = (originalBitmap.height * scale).toInt().coerceAtLeast(100)
            
            val scaledBitmap = if (scale == 1.0f) originalBitmap else Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            
            val tempFile = createTempFile(context, "jpg")
            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            
            val currentSize = tempFile.length()
            currentFile = tempFile
            
            if (currentSize <= targetBytes) {
                // Perfect, fits the budget!
                break
            } else {
                // If still too big, decrease quality, then decrease scale
                if (quality > 30) {
                    quality -= 15
                } else {
                    scale *= 0.7f
                    quality = 80 // reset quality for smaller resolution
                }
            }
        }
        
        return currentFile ?: throw Exception("Failed to reach target compression size")
    }

    // FEATURE 3: INCREASE IMAGE SIZE
    fun increaseImageSize(context: Context, uri: Uri, targetSizeKb: Int): File {
        val metadata = getMetadata(context, uri)
        val formatStr = metadata?.format ?: "JPG"
        val extension = formatStr.lowercase()
        val tempFile = createTempFile(context, extension)
        
        // Load and write standard compressed image
        val bitmap = loadBitmap(context, uri, 2048) ?: throw Exception("Failed to load image")
        FileOutputStream(tempFile).use { out ->
            val format = getOutputFormat(formatStr)
            bitmap.compress(format, 95, out)
        }
        
        val currentSize = tempFile.length()
        val targetSizeInBytes = targetSizeKb * 1024L
        
        if (targetSizeInBytes > currentSize) {
            val paddingNeeded = (targetSizeInBytes - currentSize).toInt()
            if (paddingNeeded > 0) {
                val paddingBytes = ByteArray(paddingNeeded) // Defaults to all 0s
                FileOutputStream(tempFile, true).use { appendStream ->
                    appendStream.write(paddingBytes)
                }
            }
        }
        
        return tempFile
    }

    // FEATURE 4: IMAGE RESIZE
    fun resizeImage(context: Context, uri: Uri, width: Int, height: Int, formatStr: String): File {
        val extension = formatStr.lowercase()
        val tempFile = createTempFile(context, extension)
        
        val originalBitmap = loadBitmap(context, uri, 4096) ?: throw Exception("Failed to load image")
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
        
        FileOutputStream(tempFile).use { out ->
            val format = getOutputFormat(formatStr)
            resizedBitmap.compress(format, 90, out)
        }
        return tempFile
    }

    // FEATURE 5: IMAGE FORMAT CONVERTER
    fun convertImageFormat(context: Context, uri: Uri, outputFormat: String): File {
        val extension = outputFormat.lowercase()
        val tempFile = createTempFile(context, extension)
        
        val bitmap = loadBitmap(context, uri, 3072) ?: throw Exception("Failed to load image")
        FileOutputStream(tempFile).use { out ->
            val format = getOutputFormat(outputFormat)
            bitmap.compress(format, 90, out)
        }
        return tempFile
    }

    // FEATURE 6: CROP TOOL
    fun cropImage(context: Context, uri: Uri, cropRectNormalized: RectF, outputFormat: String = "JPG"): File {
        val extension = outputFormat.lowercase()
        val tempFile = createTempFile(context, extension)
        
        val originalBitmap = loadBitmap(context, uri, 3072) ?: throw Exception("Failed to load image")
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height
        
        // Map normalized coordinates (0.0 to 1.0) to actual pixel values
        val left = (cropRectNormalized.left * originalWidth).toInt().coerceIn(0, originalWidth - 1)
        val top = (cropRectNormalized.top * originalHeight).toInt().coerceIn(0, originalHeight - 1)
        val right = (cropRectNormalized.right * originalWidth).toInt().coerceIn(left + 1, originalWidth)
        val bottom = (cropRectNormalized.bottom * originalHeight).toInt().coerceIn(top + 1, originalHeight)
        
        val width = right - left
        val height = bottom - top
        
        val croppedBitmap = Bitmap.createBitmap(originalBitmap, left, top, width, height)
        
        FileOutputStream(tempFile).use { out ->
            val format = getOutputFormat(outputFormat)
            croppedBitmap.compress(format, 92, out)
        }
        return tempFile
    }

    // FEATURE 7: ROTATE & FLIP
    fun rotateAndFlip(
        context: Context, 
        uri: Uri, 
        rotationDegrees: Int, 
        flipHorizontal: Boolean, 
        flipVertical: Boolean,
        outputFormat: String = "JPG"
    ): File {
        val extension = outputFormat.lowercase()
        val tempFile = createTempFile(context, extension)
        
        val originalBitmap = loadBitmap(context, uri, 3072) ?: throw Exception("Failed to load image")
        
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) {
                postRotate(rotationDegrees.toFloat())
            }
            val scaleX = if (flipHorizontal) -1.0f else 1.0f
            val scaleY = if (flipVertical) -1.0f else 1.0f
            if (scaleX != 1.0f || scaleY != 1.0f) {
                postScale(scaleX, scaleY)
            }
        }
        
        val processedBitmap = Bitmap.createBitmap(
            originalBitmap, 
            0, 0, 
            originalBitmap.width, 
            originalBitmap.height, 
            matrix, 
            true
        )
        
        FileOutputStream(tempFile).use { out ->
            val format = getOutputFormat(outputFormat)
            processedBitmap.compress(format, 92, out)
        }
        return tempFile
    }

    // ADVANCED COMPRESSION/CONVERSION/RESIZE WORKFLOW
    fun processImageAdvanced(
        context: Context,
        uri: Uri,
        targetWidth: Int?, // if null, keep original
        targetHeight: Int?, // if null, keep original
        formatStr: String, // "JPG", "PNG", "WEBP"
        quality: Int, // 10..100
        targetSizeInBytes: Long? // optional budget size
    ): File {
        val originalBitmap = loadBitmap(context, uri, 4096) ?: throw Exception("Failed to load image")
        
        // Step 1: Resize
        val finalWidth = targetWidth ?: originalBitmap.width
        val finalHeight = targetHeight ?: originalBitmap.height
        val scaledBitmap = if (finalWidth == originalBitmap.width && finalHeight == originalBitmap.height) {
            originalBitmap
        } else {
            Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true)
        }
        
        val extension = formatStr.lowercase()
        val format = getOutputFormat(formatStr)
        
        // Step 2: Target File Size search or simple compression
        if (targetSizeInBytes != null && targetSizeInBytes > 0 && formatStr.uppercase() != "PNG") {
            // Iteratively compress scaled bitmap to hit the target
            var currentFile: File? = null
            var currentQuality = quality
            var currentScale = 1.0f
            
            for (i in 0..15) {
                val w = (finalWidth * currentScale).toInt().coerceAtLeast(100)
                val h = (finalHeight * currentScale).toInt().coerceAtLeast(100)
                val searchBitmap = if (currentScale == 1.0f) scaledBitmap else Bitmap.createScaledBitmap(scaledBitmap, w, h, true)
                
                val tempFile = createTempFile(context, extension)
                FileOutputStream(tempFile).use { out ->
                    searchBitmap.compress(format, currentQuality, out)
                }
                
                val currentSize = tempFile.length()
                currentFile = tempFile
                
                if (currentSize <= targetSizeInBytes) {
                    break
                } else {
                    if (currentQuality > 25) {
                        currentQuality -= 15
                    } else {
                        currentScale *= 0.75f
                        currentQuality = 80 // reset quality for smaller resolution
                    }
                }
            }
            return currentFile ?: throw Exception("Failed to compress image to target size")
        } else {
            // Standard format and quality compression
            val tempFile = createTempFile(context, extension)
            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(format, quality, out)
            }
            return tempFile
        }
    }

    // FEATURE 11: SAVE & DOWNLOAD TO DEVICE GALLERY via MediaStore
    fun saveImageToGallery(context: Context, file: File, desiredName: String, formatStr: String): Uri? {
        val mimeType = when (formatStr.uppercase()) {
            "PNG" -> "image/png"
            "WEBP" -> "image/webp"
            else -> "image/jpeg"
        }
        val finalFileName = if (desiredName.contains(".")) desiredName else "$desiredName.${formatStr.lowercase()}"
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ImageCompressor")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        
        var imageUri: Uri? = null
        try {
            imageUri = resolver.insert(collectionUri, contentValues)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to gallery", e)
            if (imageUri != null) {
                try {
                    resolver.delete(imageUri, null, null)
                } catch (de: Exception) {
                    // ignore
                }
            }
        }
        return null
    }
}
