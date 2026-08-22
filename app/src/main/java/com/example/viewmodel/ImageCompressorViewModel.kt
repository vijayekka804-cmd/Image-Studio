package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.utils.ImageMetadata
import com.example.utils.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageCompressorViewModel(
    application: Application,
    private val repository: HistoryRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    // Database Reactive Flow
    val historyList: StateFlow<List<HistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Editing Image State
    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _originalMetadata = MutableStateFlow<ImageMetadata?>(null)
    val originalMetadata: StateFlow<ImageMetadata?> = _originalMetadata.asStateFlow()

    private val _editedFile = MutableStateFlow<File?>(null)
    val editedFile: StateFlow<File?> = _editedFile.asStateFlow()

    private val _editedMetadata = MutableStateFlow<ImageMetadata?>(null)
    val editedMetadata: StateFlow<ImageMetadata?> = _editedMetadata.asStateFlow()

    // Processing UI States
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // BATCH MODE STATES
    private val _selectedUrisForBatch = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUrisForBatch: StateFlow<List<Uri>> = _selectedUrisForBatch.asStateFlow()

    private val _batchIsProcessing = MutableStateFlow(false)
    val batchIsProcessing: StateFlow<Boolean> = _batchIsProcessing.asStateFlow()

    private val _batchProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val batchProgress: StateFlow<Float> = _batchProgress.asStateFlow()

    private val _batchStatusText = MutableStateFlow("")
    val batchStatusText: StateFlow<String> = _batchStatusText.asStateFlow()

    private val _batchFinishedCount = MutableStateFlow(0)
    val batchFinishedCount: StateFlow<Int> = _batchFinishedCount.asStateFlow()

    private val _batchTotalCount = MutableStateFlow(0)
    val batchTotalCount: StateFlow<Int> = _batchTotalCount.asStateFlow()

    private val _batchOutputFiles = MutableStateFlow<List<File>>(emptyList())
    val batchOutputFiles: StateFlow<List<File>> = _batchOutputFiles.asStateFlow()

    // PDF MANIPULATION STATES
    private val _pdfSelectedUri = MutableStateFlow<Uri?>(null)
    val pdfSelectedUri: StateFlow<Uri?> = _pdfSelectedUri.asStateFlow()

    private val _pdfPageCount = MutableStateFlow(0)
    val pdfPageCount: StateFlow<Int> = _pdfPageCount.asStateFlow()

    private val _pdfThumbnails = MutableStateFlow<List<android.graphics.Bitmap>>(emptyList())
    val pdfThumbnails: StateFlow<List<android.graphics.Bitmap>> = _pdfThumbnails.asStateFlow()

    private val _pdfIsProcessing = MutableStateFlow(false)
    val pdfIsProcessing: StateFlow<Boolean> = _pdfIsProcessing.asStateFlow()

    private val _pdfStatusMessage = MutableStateFlow<String?>(null)
    val pdfStatusMessage: StateFlow<String?> = _pdfStatusMessage.asStateFlow()

    private val _pdfEditedFile = MutableStateFlow<File?>(null)
    val pdfEditedFile: StateFlow<File?> = _pdfEditedFile.asStateFlow()

    fun selectImageForEditing(uri: Uri) {
        _selectedUri.value = uri
        _editedFile.value = null
        _editedMetadata.value = null
        _statusMessage.value = null
        
        viewModelScope.launch(Dispatchers.IO) {
            val meta = ImageProcessor.getMetadata(context, uri)
            _originalMetadata.value = meta
        }
    }

    fun selectMultipleImagesForBatch(uris: List<Uri>) {
        _selectedUrisForBatch.value = uris
        _batchOutputFiles.value = emptyList()
        _batchProgress.value = 0f
        _batchIsProcessing.value = false
        _batchFinishedCount.value = 0
        _batchTotalCount.value = uris.size
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // WORKER FUNCTIONS (Offline computations in IO Dispatcher)

    fun applyCompression(mode: Int) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Compressing image..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = when (mode) {
                    1 -> ImageProcessor.compressLossless(context, uri)
                    2 -> ImageProcessor.compressSmart(context, uri)
                    else -> ImageProcessor.compressMax(context, uri)
                }
                updateEditedFile(file)
                _statusMessage.value = "Compression completed!"
            } catch (e: Exception) {
                Log.e("ViewModel", "Compression error", e)
                _statusMessage.value = "Compression failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyTargetSizeCompression(targetKb: Int) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Compressing to target size ${targetKb}KB..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ImageProcessor.compressToTargetSize(context, uri, targetKb)
                updateEditedFile(file)
                _statusMessage.value = "Compressed to match target size!"
            } catch (e: Exception) {
                Log.e("ViewModel", "Target compression error", e)
                _statusMessage.value = "Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyIncreaseSize(targetKb: Int) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Padding image file to ${targetKb}KB..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ImageProcessor.increaseImageSize(context, uri, targetKb)
                updateEditedFile(file)
                _statusMessage.value = "Artificially increased file size to ${targetKb}KB"
            } catch (e: Exception) {
                Log.e("ViewModel", "Increase size error", e)
                _statusMessage.value = "Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyResize(width: Int, height: Int, format: String) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Resizing image to ${width}x${height}..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ImageProcessor.resizeImage(context, uri, width, height, format)
                updateEditedFile(file)
                _statusMessage.value = "Resize completed!"
            } catch (e: Exception) {
                Log.e("ViewModel", "Resize error", e)
                _statusMessage.value = "Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyFormatConversion(format: String) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Converting format to $format..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ImageProcessor.convertImageFormat(context, uri, format)
                updateEditedFile(file)
                _statusMessage.value = "Converted to $format format!"
            } catch (e: Exception) {
                Log.e("ViewModel", "Convert error", e)
                _statusMessage.value = "Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyCrop(rectNormalized: android.graphics.RectF, format: String) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Cropping image..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ImageProcessor.cropImage(context, uri, rectNormalized, format)
                updateEditedFile(file)
                _statusMessage.value = "Crop completed!"
            } catch (e: Exception) {
                Log.e("ViewModel", "Crop error", e)
                _statusMessage.value = "Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyRotationAndFlip(rotationDegrees: Int, flipHorizontal: Boolean, flipVertical: Boolean, format: String) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Processing rotation..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ImageProcessor.rotateAndFlip(context, uri, rotationDegrees, flipHorizontal, flipVertical, format)
                updateEditedFile(file)
                _statusMessage.value = "Rotated / Flipped successfully!"
            } catch (e: Exception) {
                Log.e("ViewModel", "Rotate error", e)
                _statusMessage.value = "Failed: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // MULTI-STEP UNIFIED PIPELINE FOR OFFLINE SPEED & MEMORY OPTIMIZATION
    fun applyPipeline(
        compressionMode: Int, // 1=Lossless, 2=Smart, 3=Max
        useTargetKb: Boolean,
        targetKbVal: Int,
        targetWidth: Int,
        targetHeight: Int,
        exportFormatStr: String,
        enableCrop: Boolean,
        cropRectNormalized: android.graphics.RectF,
        rotateDeg: Int,
        flipH: Boolean,
        flipV: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uri = _selectedUri.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Starting image pipeline processing..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var currentUri = uri
                val intermediateFiles = mutableListOf<File>()
                
                // Step A: Cropping
                if (enableCrop) {
                    _statusMessage.value = "Applying crop..."
                    val croppedFile = ImageProcessor.cropImage(context, currentUri, cropRectNormalized, "PNG") // PNG lossless intermediate
                    intermediateFiles.add(croppedFile)
                    currentUri = Uri.fromFile(croppedFile)
                }
                
                // Step B: Rotation & Flip
                if (rotateDeg != 0 || flipH || flipV) {
                    _statusMessage.value = "Applying rotation & flip..."
                    val rotatedFile = ImageProcessor.rotateAndFlip(context, currentUri, rotateDeg, flipH, flipV, "PNG")
                    intermediateFiles.add(rotatedFile)
                    currentUri = Uri.fromFile(rotatedFile)
                }
                
                // Step C: Resizing (if dimensions changed)
                val meta = ImageProcessor.getMetadata(context, currentUri)
                val currentWidth = meta?.width ?: 0
                val currentHeight = meta?.height ?: 0
                if (targetWidth > 0 && targetHeight > 0 && (targetWidth != currentWidth || targetHeight != currentHeight)) {
                    _statusMessage.value = "Resizing image to ${targetWidth}x${targetHeight}..."
                    val resizedFile = ImageProcessor.resizeImage(context, currentUri, targetWidth, targetHeight, "PNG")
                    intermediateFiles.add(resizedFile)
                    currentUri = Uri.fromFile(resizedFile)
                }
                
                // Step D: Final Format Conversion & Compression
                _statusMessage.value = "Applying final compression & formatting..."
                val finalFile = if (useTargetKb) {
                    ImageProcessor.compressToTargetSize(context, currentUri, targetKbVal)
                } else {
                    when (compressionMode) {
                        1 -> ImageProcessor.compressLossless(context, currentUri)
                        2 -> ImageProcessor.compressSmart(context, currentUri)
                        else -> ImageProcessor.compressMax(context, currentUri)
                    }
                }
                
                // Ensure output is in the desired format
                val finalFormatMeta = ImageProcessor.getMetadata(context, Uri.fromFile(finalFile))
                val currentFinalFormat = finalFormatMeta?.format ?: "JPG"
                val finalProcessedFile = if (currentFinalFormat != exportFormatStr) {
                    val convertedFile = ImageProcessor.convertImageFormat(context, Uri.fromFile(finalFile), exportFormatStr)
                    convertedFile
                } else {
                    finalFile
                }
                
                // Cleanup intermediate temp files to optimize storage
                for (file in intermediateFiles) {
                    if (file.exists() && file != finalProcessedFile) {
                        file.delete()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    updateEditedFile(finalProcessedFile)
                    _statusMessage.value = "Processing completed!"
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Pipeline processing failed", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed: ${e.message}"
                    onFailure(e.message ?: "Unknown error")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isProcessing.value = false
                }
            }
        }
    }

    // BATCH RUNNER
    fun runBatchProcessing(mode: String, param1: Any?, param2: Any?) {
        val uris = _selectedUrisForBatch.value
        if (uris.isEmpty()) return
        
        _batchIsProcessing.value = true
        _batchProgress.value = 0f
        _batchFinishedCount.value = 0
        _batchOutputFiles.value = emptyList()
        
        viewModelScope.launch(Dispatchers.IO) {
            val outputs = mutableListOf<File>()
            for (index in uris.indices) {
                val uri = uris[index]
                _batchStatusText.value = "Processing image ${index + 1} of ${uris.size}..."
                
                try {
                    val processedFile = when (mode) {
                        "COMPRESS_SMART" -> ImageProcessor.compressSmart(context, uri)
                        "COMPRESS_MAX" -> ImageProcessor.compressMax(context, uri)
                        "COMPRESS_LOSSLESS" -> ImageProcessor.compressLossless(context, uri)
                        "TARGET_SIZE" -> {
                            val kb = (param1 as? Int) ?: 50
                            ImageProcessor.compressToTargetSize(context, uri, kb)
                        }
                        "CONVERT" -> {
                            val fmt = (param1 as? String) ?: "JPG"
                            ImageProcessor.convertImageFormat(context, uri, fmt)
                        }
                        "RESIZE" -> {
                            val w = (param1 as? Int) ?: 800
                            val h = (param2 as? Int) ?: 800
                            ImageProcessor.resizeImage(context, uri, w, h, "JPG")
                        }
                        else -> ImageProcessor.compressSmart(context, uri)
                    }
                    outputs.add(processedFile)
                } catch (e: Exception) {
                    Log.e("ViewModel", "Batch file processing failed", e)
                }
                
                withContext(Dispatchers.Main) {
                    _batchFinishedCount.value = index + 1
                    _batchProgress.value = (index + 1).toFloat() / uris.size.toFloat()
                }
            }
            
            _batchOutputFiles.value = outputs
            _batchStatusText.value = "Batch processing completed! ${outputs.size} files processed."
            _batchIsProcessing.value = false
        }
    }

    // SAVE FILE AND RE-RECORD IN THE LOCAL SQLITE HISTORY
    fun saveEditedImageToGallery(customName: String, operationType: String, onComplete: (String?) -> Unit) {
        val file = _editedFile.value ?: return
        val metadata = _editedMetadata.value ?: return
        _isProcessing.value = true
        _statusMessage.value = "Saving image to public gallery..."
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedUri = ImageProcessor.saveImageToGallery(context, file, customName, metadata.format)
                if (savedUri != null) {
                    // Save to SQLite via Room
                    val originalUriStr = _selectedUri.value?.toString() ?: ""
                    val origSize = _originalMetadata.value?.fileSize ?: 0L
                    
                    val targetFileName = customName.let { if (it.contains(".")) it else "$it.${metadata.format.lowercase()}" }
                    val historyRecord = HistoryEntity(
                        originalUri = originalUriStr,
                        editedUri = savedUri.toString(),
                        fileName = targetFileName,
                        originalSize = origSize,
                        editedSize = metadata.fileSize,
                        format = metadata.format,
                        width = metadata.width,
                        height = metadata.height,
                        operation = operationType
                    )
                    repository.insert(historyRecord)
                    _statusMessage.value = "Saved successfully to Photos/ImageCompressor!"
                    
                    val finalPath = "Pictures/ImageCompressor/$targetFileName"
                    withContext(Dispatchers.Main) {
                        onComplete(finalPath)
                    }
                } else {
                    _statusMessage.value = "Failed to save file"
                    withContext(Dispatchers.Main) {
                        onComplete(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Save error", e)
                _statusMessage.value = "Failed to save: ${e.message}"
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun saveAllBatchToGallery() {
        val files = _batchOutputFiles.value
        if (files.isEmpty()) return
        _batchIsProcessing.value = true
        _batchStatusText.value = "Saving all files to gallery..."
        
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            for (i in files.indices) {
                val file = files[i]
                val format = if (file.name.contains("png", true)) "PNG" else if (file.name.contains("webp", true)) "WEBP" else "JPG"
                val name = "batch_img_${System.currentTimeMillis()}_$i"
                val savedUri = ImageProcessor.saveImageToGallery(context, file, name, format)
                if (savedUri != null) {
                    successCount++
                    // Also insert batch files into history
                    val record = HistoryEntity(
                        originalUri = "",
                        editedUri = savedUri.toString(),
                        fileName = "$name.${format.lowercase()}",
                        originalSize = 0L,
                        editedSize = file.length(),
                        format = format,
                        width = 0,
                        height = 0,
                        operation = "Batch Processed"
                    )
                    repository.insert(record)
                }
            }
            _batchStatusText.value = "Saved $successCount files to Gallery successfully!"
            _batchIsProcessing.value = false
        }
    }

    fun selectPdf(uri: Uri) {
        _pdfSelectedUri.value = uri
        _pdfEditedFile.value = null
        _pdfStatusMessage.value = null
        _pdfPageCount.value = 0
        _pdfThumbnails.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            val count = com.example.utils.PdfProcessor.getPageCount(context, uri)
            _pdfPageCount.value = count
            
            // Render thumbnails for previews
            _pdfStatusMessage.value = "Loading page previews..."
            val thumbs = com.example.utils.PdfProcessor.getPdfThumbnails(context, uri)
            _pdfThumbnails.value = thumbs
            _pdfStatusMessage.value = null
        }
    }

    fun runImageToPdf(
        imageUris: List<Uri>,
        pageSize: String,
        isPortrait: Boolean,
        margin: Int,
        quality: Int,
        customName: String,
        onComplete: (File?) -> Unit
    ) {
        _pdfIsProcessing.value = true
        _pdfStatusMessage.value = "Generating local PDF..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pdfFile = com.example.utils.PdfProcessor.imagesToPdf(
                    context, imageUris, pageSize, isPortrait, margin, quality
                )
                
                // Save to downloads and history
                val savedUri = com.example.utils.PdfProcessor.savePdfToDownloads(context, pdfFile, customName)
                if (savedUri != null) {
                    val record = HistoryEntity(
                        originalUri = "",
                        editedUri = savedUri.toString(),
                        fileName = if (customName.endsWith(".pdf", true)) customName else "$customName.pdf",
                        originalSize = imageUris.size.toLong(),
                        editedSize = pdfFile.length(),
                        format = "PDF",
                        width = 0,
                        height = 0,
                        operation = "Image to PDF"
                    )
                    repository.insert(record)
                }
                
                withContext(Dispatchers.Main) {
                    _pdfEditedFile.value = pdfFile
                    _pdfStatusMessage.value = "PDF generated successfully!"
                    onComplete(pdfFile)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Image to PDF failed", e)
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Failed: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _pdfIsProcessing.value = false }
            }
        }
    }

    fun runPdfToImages(
        pdfUri: Uri,
        selectedPages: List<Int>,
        format: String,
        quality: Int,
        onComplete: (List<File>) -> Unit
    ) {
        _pdfIsProcessing.value = true
        _pdfStatusMessage.value = "Converting pages to images..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = com.example.utils.PdfProcessor.pdfToImages(
                    context, pdfUri, selectedPages, format, quality
                )
                
                // Save each to gallery and insert into history
                for (i in files.indices) {
                    val file = files[i]
                    val name = "pdf_page_${selectedPages.getOrNull(i)?.plus(1) ?: (i + 1)}_${System.currentTimeMillis()}"
                    val savedUri = ImageProcessor.saveImageToGallery(context, file, name, format)
                    if (savedUri != null) {
                        val record = HistoryEntity(
                            originalUri = pdfUri.toString(),
                            editedUri = savedUri.toString(),
                            fileName = "$name.${format.lowercase()}",
                            originalSize = 0L,
                            editedSize = file.length(),
                            format = format,
                            width = 0,
                            height = 0,
                            operation = "PDF to Image"
                        )
                        repository.insert(record)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Successfully converted ${files.size} pages!"
                    onComplete(files)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "PDF to Image failed", e)
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Failed: ${e.message}"
                    onComplete(emptyList())
                }
            } finally {
                withContext(Dispatchers.Main) { _pdfIsProcessing.value = false }
            }
        }
    }

    fun runPdfCompression(
        pdfUri: Uri,
        compressionMode: String,
        customName: String,
        onComplete: (File?) -> Unit
    ) {
        _pdfIsProcessing.value = true
        _pdfStatusMessage.value = "Optimizing PDF..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val compressedFile = com.example.utils.PdfProcessor.compressPdf(
                    context, pdfUri, compressionMode
                )
                
                val savedUri = com.example.utils.PdfProcessor.savePdfToDownloads(context, compressedFile, customName)
                if (savedUri != null) {
                    val origSize = context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd -> pfd.statSize } ?: 0L
                    val record = HistoryEntity(
                        originalUri = pdfUri.toString(),
                        editedUri = savedUri.toString(),
                        fileName = if (customName.endsWith(".pdf", true)) customName else "$customName.pdf",
                        originalSize = origSize,
                        editedSize = compressedFile.length(),
                        format = "PDF",
                        width = 0,
                        height = 0,
                        operation = "Compress PDF"
                    )
                    repository.insert(record)
                }
                
                withContext(Dispatchers.Main) {
                    _pdfEditedFile.value = compressedFile
                    _pdfStatusMessage.value = "PDF compressed successfully!"
                    onComplete(compressedFile)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "PDF Compression failed", e)
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Failed: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _pdfIsProcessing.value = false }
            }
        }
    }

    fun runPdfMerge(
        pdfUris: List<Uri>,
        customName: String,
        onComplete: (File?) -> Unit
    ) {
        _pdfIsProcessing.value = true
        _pdfStatusMessage.value = "Merging PDF files..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mergedFile = com.example.utils.PdfProcessor.mergePdfs(context, pdfUris)
                
                val savedUri = com.example.utils.PdfProcessor.savePdfToDownloads(context, mergedFile, customName)
                if (savedUri != null) {
                    val record = HistoryEntity(
                        originalUri = "",
                        editedUri = savedUri.toString(),
                        fileName = if (customName.endsWith(".pdf", true)) customName else "$customName.pdf",
                        originalSize = pdfUris.size.toLong(),
                        editedSize = mergedFile.length(),
                        format = "PDF",
                        width = 0,
                        height = 0,
                        operation = "Merge PDF"
                    )
                    repository.insert(record)
                }
                
                withContext(Dispatchers.Main) {
                    _pdfEditedFile.value = mergedFile
                    _pdfStatusMessage.value = "PDFs merged successfully!"
                    onComplete(mergedFile)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "PDF Merge failed", e)
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Failed: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _pdfIsProcessing.value = false }
            }
        }
    }

    fun runPdfSplit(
        pdfUri: Uri,
        keepPages: List<Int>,
        customName: String,
        onComplete: (File?) -> Unit
    ) {
        _pdfIsProcessing.value = true
        _pdfStatusMessage.value = "Splitting PDF..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val splitFile = com.example.utils.PdfProcessor.splitPdf(context, pdfUri, keepPages)
                
                val savedUri = com.example.utils.PdfProcessor.savePdfToDownloads(context, splitFile, customName)
                if (savedUri != null) {
                    val record = HistoryEntity(
                        originalUri = pdfUri.toString(),
                        editedUri = savedUri.toString(),
                        fileName = if (customName.endsWith(".pdf", true)) customName else "$customName.pdf",
                        originalSize = 0L,
                        editedSize = splitFile.length(),
                        format = "PDF",
                        width = 0,
                        height = 0,
                        operation = "Split PDF"
                    )
                    repository.insert(record)
                }
                
                withContext(Dispatchers.Main) {
                    _pdfEditedFile.value = splitFile
                    _pdfStatusMessage.value = "PDF split successfully!"
                    onComplete(splitFile)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "PDF Split failed", e)
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Failed: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _pdfIsProcessing.value = false }
            }
        }
    }

    fun runPdfRotation(
        pdfUri: Uri,
        rotationAngle: Int,
        selectedPages: List<Int>,
        customName: String,
        onComplete: (File?) -> Unit
    ) {
        _pdfIsProcessing.value = true
        _pdfStatusMessage.value = "Rotating PDF..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rotatedFile = com.example.utils.PdfProcessor.rotatePdf(
                    context, pdfUri, rotationAngle, selectedPages
                )
                
                val savedUri = com.example.utils.PdfProcessor.savePdfToDownloads(context, rotatedFile, customName)
                if (savedUri != null) {
                    val record = HistoryEntity(
                        originalUri = pdfUri.toString(),
                        editedUri = savedUri.toString(),
                        fileName = if (customName.endsWith(".pdf", true)) customName else "$customName.pdf",
                        originalSize = 0L,
                        editedSize = rotatedFile.length(),
                        format = "PDF",
                        width = 0,
                        height = 0,
                        operation = "Rotate PDF"
                    )
                    repository.insert(record)
                }
                
                withContext(Dispatchers.Main) {
                    _pdfEditedFile.value = rotatedFile
                    _pdfStatusMessage.value = "PDF rotated successfully!"
                    onComplete(rotatedFile)
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "PDF Rotation failed", e)
                withContext(Dispatchers.Main) {
                    _pdfStatusMessage.value = "Failed: ${e.message}"
                    onComplete(null)
                }
            } finally {
                withContext(Dispatchers.Main) { _pdfIsProcessing.value = false }
            }
        }
    }

    fun deleteHistoryItem(item: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(item.id)
            // Optionally delete the file if possible (though MediaStore restricts deletions of other files without prompt,
            // we delete the database entry which is safe and offline).
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    private fun updateEditedFile(file: File) {
        _editedFile.value = file
        // Parse metadata of edited file
        val size = file.length()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val format = when {
            options.outMimeType?.contains("png", true) == true -> "PNG"
            options.outMimeType?.contains("webp", true) == true -> "WEBP"
            else -> "JPG"
        }
        _editedMetadata.value = ImageMetadata(
            fileName = file.name,
            fileSize = size,
            width = options.outWidth,
            height = options.outHeight,
            format = format,
            resolution = "${options.outWidth} x ${options.outHeight}"
        )
    }
}

class ImageCompressorViewModelFactory(
    private val application: Application,
    private val repository: HistoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageCompressorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImageCompressorViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
