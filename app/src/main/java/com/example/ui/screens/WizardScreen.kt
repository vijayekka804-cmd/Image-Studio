package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.HistoryEntity
import com.example.ui.components.CropCanvas
import com.example.utils.ImageMetadata
import com.example.utils.ImageProcessor
import com.example.utils.PdfProcessor
import com.example.viewmodel.ImageCompressorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    viewModel: ImageCompressorViewModel,
    toolType: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPdf = remember(toolType) {
        toolType.startsWith("PDF_") || toolType == "IMAGE_TO_PDF"
    }

    // Screen workflow steps
    // 1 = Explain & Select Files
    // 2 = Settings & Preview
    // 3 = Processing
    // 4 = Result Screen
    var currentStep by remember { mutableIntStateOf(1) }

    // Dynamic state variables based on toolType
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var originalMetadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var pdfPageCount by remember { mutableIntStateOf(0) }
    val pdfThumbnails = remember { mutableStateListOf<Bitmap>() }

    var isProcessingLocal by remember { mutableStateOf(false) }
    var progressLocal by remember { mutableStateOf(0f) }
    var statusMessageLocal by remember { mutableStateOf("") }

    var editedFile by remember { mutableStateOf<File?>(null) }
    var editedMetadata by remember { mutableStateOf<ImageMetadata?>(null) }
    var downloadCompleted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Settings Parameters:
    // Image To PDF Settings
    var pageSize by remember { mutableStateOf("A4") } // "A4", "LETTER", "ORIGINAL"
    var isPortrait by remember { mutableStateOf(true) }
    var marginPoints by remember { mutableStateOf("18") } // "0" = None, "18" = Small, "36" = Medium, "54" = Large
    var pdfImageQuality by remember { mutableFloatStateOf(85f) }
    var imageFit by remember { mutableStateOf("Fit to Page") } // "Fit to Page", "Fill Page"

    // Image Resize Settings
    var resizeWidth by remember { mutableStateOf("1080") }
    var resizeHeight by remember { mutableStateOf("1080") }
    var resizeUnit by remember { mutableStateOf("px") } // "px", "cm", "in"
    var maintainAspectRatio by remember { mutableStateOf(true) }

    // Advanced Compress Image & Batch Settings
    var compressResizeMode by remember { mutableStateOf("ORIGINAL") } // "ORIGINAL", "PRESET", "CUSTOM"
    var compressPresetPct by remember { mutableStateOf("50%") } // "25%", "50%", "75%", "100%", "Custom"
    var compressCustomPresetVal by remember { mutableFloatStateOf(50f) }
    var compressWidthInput by remember { mutableStateOf("") }
    var compressHeightInput by remember { mutableStateOf("") }
    var compressMaintainAspectRatio by remember { mutableStateOf(true) }
    var compressFormat by remember { mutableStateOf("WEBP") } // "JPG", "PNG", "WEBP"
    var compressQualityMode by remember { mutableStateOf("MEDIUM") } // "LOW", "MEDIUM", "HIGH", "CUSTOM"
    var compressQualitySlider by remember { mutableFloatStateOf(80f) }
    var compressTargetEnabled by remember { mutableStateOf(false) }
    var compressTargetInput by remember { mutableStateOf("200") }
    var compressTargetUnit by remember { mutableStateOf("KB") } // "KB", "MB"

    // Batch result stats state
    val batchResultFiles = remember { mutableStateListOf<File>() }
    var batchOriginalTotalSize by remember { mutableLongStateOf(0L) }
    var batchNewTotalSize by remember { mutableLongStateOf(0L) }
    var batchSuccessCount by remember { mutableIntStateOf(0) }
    var batchFailedCount by remember { mutableIntStateOf(0) }

    // PDF Custom Settings
    var pdfTargetInput by remember { mutableStateOf("500") }
    var pdfTargetUnit by remember { mutableStateOf("KB") }

    // Image Converter Settings
    var exportFormat by remember { mutableStateOf("WEBP") } // "JPG", "PNG", "WEBP"
    var exportQuality by remember { mutableFloatStateOf(85f) }

    // Crop Settings
    var cropAspectRatio by remember { mutableStateOf("Free") } // "Free", "1:1", "4:3", "16:9"
    var cropRectNormalized by remember { mutableStateOf(android.graphics.RectF(0.1f, 0.1f, 0.9f, 0.9f)) }

    // Rotate & Flip Settings
    var rotateDegrees by remember { mutableIntStateOf(90) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    // PDF to Image Settings
    var pdfExportImageFormat by remember { mutableStateOf("JPG") }
    var pdfExportQuality by remember { mutableFloatStateOf(85f) }
    val pdfSelectedPagesToConvert = remember { mutableStateListOf<Int>() }

    // PDF Compressor Settings
    var pdfCompressMode by remember { mutableStateOf("BALANCED") } // "MAX", "BALANCED", "HIGH", "CUSTOM"

    // PDF Split/Extract Settings
    var pdfSplitMethod by remember { mutableStateOf("Selected Pages") }
    val pdfSelectedPagesToKeep = remember { mutableStateListOf<Int>() }

    // PDF Rotate Settings
    var pdfRotateDegrees by remember { mutableIntStateOf(90) }
    val pdfSelectedPagesToRotate = remember { mutableStateListOf<Int>() }

    // PDF Reorder Settings
    val pdfPageOrder = remember { mutableStateListOf<Int>() }

    // Custom output name
    var customName by remember { mutableStateOf("output_file") }

    // File pickers launchers
    val pickSingleImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            currentStep = 2
            isProcessingLocal = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val meta = ImageProcessor.getMetadata(context, uri)
                    withContext(Dispatchers.Main) {
                        originalMetadata = meta
                        if (meta != null) {
                            resizeWidth = meta.width.toString()
                            resizeHeight = meta.height.toString()
                            customName = "resized_" + meta.fileName.substringBeforeLast(".")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isProcessingLocal = false }
                }
            }
        }
    }

    val pickMultipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            currentStep = 2
            customName = "batch_optimized_" + System.currentTimeMillis()
        }
    }

    val pickSinglePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            currentStep = 2
            isProcessingLocal = true
            statusMessageLocal = "Loading PDF details..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val pages = PdfProcessor.getPageCount(context, uri)
                    val thumbs = PdfProcessor.getPdfThumbnails(context, uri, maxPages = 30)
                    withContext(Dispatchers.Main) {
                        pdfPageCount = pages
                        pdfThumbnails.clear()
                        pdfThumbnails.addAll(thumbs)
                        pdfPageOrder.clear()
                        for (i in 0 until pages) {
                            pdfPageOrder.add(i)
                        }
                        val fileName = uri.lastPathSegment ?: "document"
                        customName = "processed_" + fileName.substringBeforeLast(".").substringAfterLast("/")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isProcessingLocal = false }
                }
            }
        }
    }

    val pickMultiplePdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            currentStep = 2
            customName = "merged_documents"
        }
    }

    val toolTitle = when (toolType) {
        "IMAGE_TO_PDF" -> "Image to PDF"
        "IMAGE_RESIZE" -> "Resize Image"
        "IMAGE_COMPRESS" -> "Compress Image"
        "IMAGE_CONVERT" -> "Convert Format"
        "IMAGE_CROP" -> "Crop Image"
        "IMAGE_ROTATE" -> "Rotate & Flip"
        "BATCH_COMPRESS" -> "Batch Compressor"
        "PDF_TO_IMAGE" -> "PDF to Image"
        "PDF_COMPRESS" -> "Compress PDF"
        "PDF_MERGE" -> "Merge PDFs"
        "PDF_SPLIT" -> "Split PDF"
        "PDF_EXTRACT" -> "Extract Pages"
        "PDF_ROTATE" -> "Rotate PDF"
        "PDF_REORDER" -> "Reorder Pages"
        else -> "Tool"
    }

    val toolIcon = when (toolType) {
        "IMAGE_TO_PDF" -> Icons.Default.PictureAsPdf
        "IMAGE_RESIZE" -> Icons.Default.AspectRatio
        "IMAGE_COMPRESS" -> Icons.Default.Compress
        "IMAGE_CONVERT" -> Icons.Default.Transform
        "IMAGE_CROP" -> Icons.Default.Crop
        "IMAGE_ROTATE" -> Icons.Default.RotateRight
        "BATCH_COMPRESS" -> Icons.Default.PhotoSizeSelectActual
        "PDF_TO_IMAGE" -> Icons.Default.Collections
        "PDF_COMPRESS" -> Icons.Default.Compress
        "PDF_MERGE" -> Icons.Default.MergeType
        "PDF_SPLIT" -> Icons.Default.CallSplit
        "PDF_EXTRACT" -> Icons.Default.ContentCopy
        "PDF_ROTATE" -> Icons.Default.RotateRight
        "PDF_REORDER" -> Icons.Default.Menu
        else -> Icons.Default.Build
    }

    val toolDescription = when (toolType) {
        "IMAGE_TO_PDF" -> "Convert multiple image formats into a single PDF document perfectly styled with margins and layouts."
        "IMAGE_RESIZE" -> "Change the width and height dimensions of an image. Keep aspect ratios or set custom resolutions."
        "IMAGE_COMPRESS" -> "Advanced control over image footprint: adjust dimension downscaling, convert format, and dial in output target budget sizes."
        "IMAGE_CONVERT" -> "Convert images to JPG, PNG, or WEBP while maintaining maximum original fidelity."
        "IMAGE_CROP" -> "Crop out unwanted sections using standard aspect ratios (1:1, 16:9, etc.) or freeform selection."
        "IMAGE_ROTATE" -> "Rotate your image by 90/180/270 degrees or flip it vertically and horizontally."
        "BATCH_COMPRESS" -> "Apply powerful bulk operations across multiple photos. Compress, convert, resize, or target custom files sizes in parallel."
        "PDF_TO_IMAGE" -> "Extract individual PDF pages and export them as crisp JPG or PNG images."
        "PDF_COMPRESS" -> "Compress your PDF documents iteratively to achieve significant space savings. Optionally set custom target sizes."
        "PDF_MERGE" -> "Combine multiple separate PDF files into a single master document easily."
        "PDF_SPLIT" -> "Split a large document into separate individual PDF files based on your selected pages."
        "PDF_EXTRACT" -> "Extract specific selected pages from a document and construct a new PDF out of them."
        "PDF_ROTATE" -> "Rotate selected pages or all pages of a PDF document by 90, 180, or 270 degrees."
        "PDF_REORDER" -> "Reorganize the sequence order of pages in a PDF document visually."
        else -> "Perform high-quality offline image and PDF operations."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(toolTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentStep) {
                1 -> {
                    // STEP 1: EXPLAIN & SELECT FILES
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(48.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = toolIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "Step 1: Introduction",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = toolTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = toolDescription,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = {
                                when (toolType) {
                                    "IMAGE_TO_PDF", "BATCH_COMPRESS" -> {
                                        pickMultipleImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    "PDF_MERGE" -> {
                                        pickMultiplePdfsLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    "PDF_COMPRESS", "PDF_TO_IMAGE", "PDF_SPLIT", "PDF_EXTRACT", "PDF_ROTATE", "PDF_REORDER" -> {
                                        pickSinglePdfLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    else -> {
                                        pickSingleImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(56.dp)
                                .testTag("select_files_button"),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (toolType == "IMAGE_TO_PDF" || toolType == "BATCH_COMPRESS") "Select Images"
                                       else if (toolType == "PDF_MERGE") "Select PDFs"
                                       else "Select File",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                2 -> {
                    // STEP 2: PREVIEW & SETTINGS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item {
                                // Dynamic Source Files Overview Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Selected Files Summary",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (selectedUri != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = originalMetadata?.fileName ?: selectedUri!!.lastPathSegment ?: "File Selected",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!isPdf && originalMetadata != null) {
                                                        Text(
                                                            "${originalMetadata!!.resolution} • ${Formatter.formatFileSize(context, originalMetadata!!.fileSize)}",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    } else if (isPdf && pdfPageCount > 0) {
                                                        Text(
                                                            "$pdfPageCount pages loaded",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        } else if (selectedUris.isNotEmpty()) {
                                            Text(
                                                "${selectedUris.size} files loaded successfully",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic Tool Specific Settings
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Configure Options",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))

                                        when (toolType) {
                                            "IMAGE_COMPRESS" -> {
                                                // Format Selection
                                                Text("Output Format", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("WEBP", "JPG", "PNG").forEach { fmt ->
                                                        FilterChip(
                                                            selected = compressFormat == fmt,
                                                            onClick = { compressFormat = fmt },
                                                            label = { Text(fmt) }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))
                                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Dimensions downscale modes
                                                Text("Resize Option", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf(
                                                        Triple("ORIGINAL", "Keep Original", Icons.Default.Image),
                                                        Triple("PRESET", "By Scale %", Icons.Default.Percent),
                                                        Triple("CUSTOM", "Custom Size", Icons.Default.AspectRatio)
                                                    ).forEach { (mode, label, icon) ->
                                                        FilterChip(
                                                            selected = compressResizeMode == mode,
                                                            onClick = { compressResizeMode = mode },
                                                            label = { Text(label) },
                                                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                        )
                                                    }
                                                }

                                                if (compressResizeMode == "PRESET") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text("Select scale percentage: $compressPresetPct", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        listOf("25%", "50%", "75%", "90%").forEach { pct ->
                                                            FilterChip(
                                                                selected = compressPresetPct == pct,
                                                                onClick = {
                                                                    compressPresetPct = pct
                                                                    compressCustomPresetVal = pct.replace("%", "").toFloat()
                                                                },
                                                                label = { Text(pct) }
                                                            )
                                                        }
                                                    }
                                                } else if (compressResizeMode == "CUSTOM") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        OutlinedTextField(
                                                            value = compressWidthInput,
                                                            onValueChange = { compressWidthInput = it },
                                                            label = { Text("Width (px)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedTextField(
                                                            value = compressHeightInput,
                                                            onValueChange = { compressHeightInput = it },
                                                            label = { Text("Height (px)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                if (compressFormat != "PNG") {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    // Image quality controls
                                                    Text("Quality Preset", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        listOf("LOW" to 35f, "MEDIUM" to 70f, "HIGH" to 85f, "CUSTOM" to compressQualitySlider).forEach { (mode, qValue) ->
                                                            FilterChip(
                                                                selected = compressQualityMode == mode,
                                                                onClick = {
                                                                    compressQualityMode = mode
                                                                    compressQualitySlider = qValue
                                                                },
                                                                label = { Text(mode) }
                                                            )
                                                        }
                                                    }

                                                    if (compressQualityMode == "CUSTOM") {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text("Quality: ${compressQualitySlider.toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Slider(
                                                            value = compressQualitySlider,
                                                            onValueChange = { compressQualitySlider = it },
                                                            valueRange = 10f..100f
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    // TARGET FILE SIZE LIMIT BUDGETS
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = compressTargetEnabled,
                                                            onCheckedChange = { compressTargetEnabled = it }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Text("Compress to target file size", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("Applies iterative search to fit exact size limit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }

                                                    if (compressTargetEnabled) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            OutlinedTextField(
                                                                value = compressTargetInput,
                                                                onValueChange = { compressTargetInput = it },
                                                                label = { Text("Target Size") },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                listOf("KB", "MB").forEach { unit ->
                                                                    FilterChip(
                                                                        selected = compressTargetUnit == unit,
                                                                        onClick = { compressTargetUnit = unit },
                                                                        label = { Text(unit) }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            "BATCH_COMPRESS" -> {
                                                // Batch configuration (Same advanced options as compress image)
                                                Text("Batch Format", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("WEBP", "JPG", "PNG").forEach { fmt ->
                                                        FilterChip(
                                                            selected = compressFormat == fmt,
                                                            onClick = { compressFormat = fmt },
                                                            label = { Text(fmt) }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))
                                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                Spacer(modifier = Modifier.height(16.dp))

                                                Text("Batch Downscaling", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf(
                                                        Triple("ORIGINAL", "Keep Original", Icons.Default.Image),
                                                        Triple("PRESET", "Preset scale", Icons.Default.Percent),
                                                        Triple("CUSTOM", "Custom dimensions", Icons.Default.AspectRatio)
                                                    ).forEach { (mode, label, icon) ->
                                                        FilterChip(
                                                            selected = compressResizeMode == mode,
                                                            onClick = { compressResizeMode = mode },
                                                            label = { Text(label) },
                                                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                        )
                                                    }
                                                }

                                                if (compressResizeMode == "PRESET") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        listOf("25%", "50%", "75%", "90%").forEach { pct ->
                                                            FilterChip(
                                                                selected = compressPresetPct == pct,
                                                                onClick = {
                                                                    compressPresetPct = pct
                                                                    compressCustomPresetVal = pct.replace("%", "").toFloat()
                                                                },
                                                                label = { Text(pct) }
                                                            )
                                                        }
                                                    }
                                                } else if (compressResizeMode == "CUSTOM") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        OutlinedTextField(
                                                            value = compressWidthInput,
                                                            onValueChange = { compressWidthInput = it },
                                                            label = { Text("Width (px)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedTextField(
                                                            value = compressHeightInput,
                                                            onValueChange = { compressHeightInput = it },
                                                            label = { Text("Height (px)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                if (compressFormat != "PNG") {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    Text("Quality Level", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        listOf("LOW" to 35f, "MEDIUM" to 70f, "HIGH" to 85f, "CUSTOM" to compressQualitySlider).forEach { (mode, qVal) ->
                                                            FilterChip(
                                                                selected = compressQualityMode == mode,
                                                                onClick = {
                                                                    compressQualityMode = mode
                                                                    compressQualitySlider = qVal
                                                                },
                                                                label = { Text(mode) }
                                                            )
                                                        }
                                                    }

                                                    if (compressQualityMode == "CUSTOM") {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Slider(
                                                            value = compressQualitySlider,
                                                            onValueChange = { compressQualitySlider = it },
                                                            valueRange = 10f..100f
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = compressTargetEnabled,
                                                            onCheckedChange = { compressTargetEnabled = it }
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Text("Compress images to target file size", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("Applies search limit per each bulk image", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }

                                                    if (compressTargetEnabled) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            OutlinedTextField(
                                                                value = compressTargetInput,
                                                                onValueChange = { compressTargetInput = it },
                                                                label = { Text("Target Size") },
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                listOf("KB", "MB").forEach { unit ->
                                                                    FilterChip(
                                                                        selected = compressTargetUnit == unit,
                                                                        onClick = { compressTargetUnit = unit },
                                                                        label = { Text(unit) }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            "PDF_COMPRESS" -> {
                                                Text("Compression Options", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("MAX", "BALANCED", "HIGH", "CUSTOM").forEach { mode ->
                                                        FilterChip(
                                                            selected = pdfCompressMode == mode,
                                                            onClick = { pdfCompressMode = mode },
                                                            label = { Text(mode) }
                                                        )
                                                    }
                                                }

                                                if (pdfCompressMode == "CUSTOM") {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    Text("Target PDF File Size", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        OutlinedTextField(
                                                            value = pdfTargetInput,
                                                            onValueChange = { pdfTargetInput = it },
                                                            label = { Text("Max Target size limit") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            listOf("KB", "MB").forEach { unit ->
                                                                    FilterChip(
                                                                        selected = pdfTargetUnit == unit,
                                                                        onClick = { pdfTargetUnit = unit },
                                                                        label = { Text(unit) }
                                                                    )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            "IMAGE_TO_PDF" -> {
                                                Text("Page Setup Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("A4", "LETTER", "ORIGINAL").forEach { size ->
                                                        FilterChip(
                                                            selected = pageSize == size,
                                                            onClick = { pageSize = size },
                                                            label = { Text(size) }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Orientation", modifier = Modifier.weight(1f))
                                                    Switch(
                                                        checked = isPortrait,
                                                        onCheckedChange = { isPortrait = it }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(if (isPortrait) "Portrait" else "Landscape")
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Page Margin: $marginPoints pt", fontSize = 12.sp)
                                                Slider(
                                                    value = marginPoints.toFloatOrNull() ?: 18f,
                                                    onValueChange = { marginPoints = it.toInt().toString() },
                                                    valueRange = 0f..54f,
                                                    steps = 3
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Compression quality: ${pdfImageQuality.toInt()}%", fontSize = 12.sp)
                                                Slider(
                                                    value = pdfImageQuality,
                                                    onValueChange = { pdfImageQuality = it },
                                                    valueRange = 30f..100f
                                                )
                                            }

                                            "IMAGE_RESIZE" -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    OutlinedTextField(
                                                        value = resizeWidth,
                                                        onValueChange = { resizeWidth = it },
                                                        label = { Text("Width") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = resizeHeight,
                                                        onValueChange = { resizeHeight = it },
                                                        label = { Text("Height") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = maintainAspectRatio, onCheckedChange = { maintainAspectRatio = it })
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Maintain Aspect Ratio")
                                                }
                                            }

                                            "IMAGE_CONVERT" -> {
                                                Text("Target Format", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                                                        FilterChip(
                                                            selected = exportFormat == fmt,
                                                            onClick = { exportFormat = fmt },
                                                            label = { Text(fmt) }
                                                        )
                                                    }
                                                }
                                                if (exportFormat != "PNG") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text("Quality: ${exportQuality.toInt()}%", fontSize = 12.sp)
                                                    Slider(
                                                        value = exportQuality,
                                                        onValueChange = { exportQuality = it },
                                                        valueRange = 10f..100f
                                                    )
                                                }
                                            }

                                            "IMAGE_CROP" -> {
                                                Text("Interactive Crop Aspect Ratio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("Free", "1:1", "4:3", "16:9").forEach { ratio ->
                                                        FilterChip(
                                                            selected = cropAspectRatio == ratio,
                                                            onClick = { cropAspectRatio = ratio },
                                                            label = { Text(ratio) }
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))

                                                val cropBitmap = remember(selectedUri) {
                                                    selectedUri?.let { ImageProcessor.loadBitmap(context, it, 1024) }
                                                }
                                                if (cropBitmap != null) {
                                                    CropCanvas(
                                                        bitmap = cropBitmap,
                                                        cropRect = cropRectNormalized,
                                                        onCropRectChanged = { cropRectNormalized = it },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(300.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(200.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(Color.Black.copy(alpha = 0.1f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator()
                                                    }
                                                }
                                            }

                                            "IMAGE_ROTATE" -> {
                                                Text("Rotate Degrees", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf(90, 180, 270).forEach { deg ->
                                                        FilterChip(
                                                            selected = rotateDegrees == deg,
                                                            onClick = { rotateDegrees = deg },
                                                            label = { Text("${deg}°") }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))
                                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = flipHorizontal, onCheckedChange = { flipHorizontal = it })
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Flip Horizontally")
                                                    Spacer(modifier = Modifier.width(24.dp))
                                                    Checkbox(checked = flipVertical, onCheckedChange = { flipVertical = it })
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Flip Vertically")
                                                }
                                            }

                                            "PDF_TO_IMAGE" -> {
                                                Text("Export Format", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf("JPG", "PNG").forEach { format ->
                                                        FilterChip(
                                                            selected = pdfExportImageFormat == format,
                                                            onClick = { pdfExportImageFormat = format },
                                                            label = { Text(format) }
                                                        )
                                                    }
                                                }

                                                if (pdfExportImageFormat != "PNG") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text("Export Quality: ${pdfExportQuality.toInt()}%", fontSize = 12.sp)
                                                    Slider(
                                                        value = pdfExportQuality,
                                                        onValueChange = { pdfExportQuality = it },
                                                        valueRange = 10f..100f
                                                    )
                                                }
                                            }

                                            "PDF_MERGE" -> {
                                                Text("Combines selected PDF documents sequentially.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            "PDF_SPLIT", "PDF_EXTRACT" -> {
                                                Text("Selected pages to save / extract", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = pdfTargetInput,
                                                    onValueChange = { pdfTargetInput = it },
                                                    label = { Text("Comma-separated pages (e.g. 1, 3, 5-8)") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            "PDF_ROTATE" -> {
                                                Text("Rotate PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf(90, 180, 270).forEach { deg ->
                                                        FilterChip(
                                                            selected = pdfRotateDegrees == deg,
                                                            onClick = { pdfRotateDegrees = deg },
                                                            label = { Text("${deg}°") }
                                                        )
                                                    }
                                                }
                                            }

                                            "PDF_REORDER" -> {
                                                Text("Reorganizes PDF document order sequences.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                // Custom output file name Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Output Settings",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = customName,
                                            onValueChange = { customName = it },
                                            label = { Text("File Name Prefix") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Continue button with safe padding
                        Button(
                            onClick = {
                                isProcessingLocal = true
                                errorMessage = null
                                downloadCompleted = false
                                currentStep = 3
                                statusMessageLocal = "Processing files..."

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        when (toolType) {
                                            "IMAGE_COMPRESS" -> {
                                                val origBitmap = ImageProcessor.loadBitmap(context, selectedUri!!, 4096) ?: throw Exception("Failed to load image")
                                                val targetWidth: Int?
                                                val targetHeight: Int?
                                                
                                                if (compressResizeMode == "PRESET") {
                                                    val pct = compressCustomPresetVal / 100f
                                                    targetWidth = (origBitmap.width * pct).toInt().coerceAtLeast(1)
                                                    targetHeight = (origBitmap.height * pct).toInt().coerceAtLeast(1)
                                                } else if (compressResizeMode == "CUSTOM") {
                                                    targetWidth = compressWidthInput.toIntOrNull() ?: origBitmap.width
                                                    targetHeight = compressHeightInput.toIntOrNull() ?: origBitmap.height
                                                } else {
                                                    targetWidth = null
                                                    targetHeight = null
                                                }

                                                val targetBytes = if (compressTargetEnabled) {
                                                    val sizeLimit = compressTargetInput.toDoubleOrNull() ?: 200.0
                                                    val mult = if (compressTargetUnit == "MB") 1024L * 1024L else 1024L
                                                    (sizeLimit * mult).toLong()
                                                } else {
                                                    null
                                                }

                                                val resultFile = ImageProcessor.processImageAdvanced(
                                                    context = context,
                                                    uri = selectedUri!!,
                                                    targetWidth = targetWidth,
                                                    targetHeight = targetHeight,
                                                    formatStr = compressFormat,
                                                    quality = compressQualitySlider.toInt(),
                                                    targetSizeInBytes = targetBytes
                                                )
                                                
                                                withContext(Dispatchers.Main) {
                                                    editedFile = resultFile
                                                    editedMetadata = ImageProcessor.getMetadata(context, Uri.fromFile(resultFile))
                                                    currentStep = 4
                                                }
                                            }

                                            "BATCH_COMPRESS" -> {
                                                val results = mutableListOf<File>()
                                                var origTotal = 0L
                                                var succ = 0
                                                var fail = 0

                                                for ((idx, uri) in selectedUris.withIndex()) {
                                                    withContext(Dispatchers.Main) {
                                                        statusMessageLocal = "Compressing bulk files (${idx + 1}/${selectedUris.size})..."
                                                    }

                                                    val fdSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                                                    origTotal += fdSize

                                                    try {
                                                        val origBitmap = ImageProcessor.loadBitmap(context, uri, 4096) ?: throw Exception("Load failed")
                                                        val targetWidth: Int?
                                                        val targetHeight: Int?
                                                        
                                                        if (compressResizeMode == "PRESET") {
                                                            val pct = compressCustomPresetVal / 100f
                                                            targetWidth = (origBitmap.width * pct).toInt().coerceAtLeast(1)
                                                            targetHeight = (origBitmap.height * pct).toInt().coerceAtLeast(1)
                                                        } else if (compressResizeMode == "CUSTOM") {
                                                            targetWidth = compressWidthInput.toIntOrNull() ?: origBitmap.width
                                                            targetHeight = compressHeightInput.toIntOrNull() ?: origBitmap.height
                                                        } else {
                                                            targetWidth = null
                                                            targetHeight = null
                                                        }

                                                        val targetBytes = if (compressTargetEnabled) {
                                                            val sizeLimit = compressTargetInput.toDoubleOrNull() ?: 200.0
                                                            val mult = if (compressTargetUnit == "MB") 1024L * 1024L else 1024L
                                                            (sizeLimit * mult).toLong()
                                                        } else {
                                                            null
                                                        }

                                                        val out = ImageProcessor.processImageAdvanced(
                                                            context = context,
                                                            uri = uri,
                                                            targetWidth = targetWidth,
                                                            targetHeight = targetHeight,
                                                            formatStr = compressFormat,
                                                            quality = compressQualitySlider.toInt(),
                                                            targetSizeInBytes = targetBytes
                                                        )
                                                        results.add(out)
                                                        succ++
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        fail++
                                                    }
                                                }

                                                val totalNew = results.sumOf { it.length() }
                                                withContext(Dispatchers.Main) {
                                                    batchResultFiles.clear()
                                                    batchResultFiles.addAll(results)
                                                    batchOriginalTotalSize = origTotal
                                                    batchNewTotalSize = totalNew
                                                    batchSuccessCount = succ
                                                    batchFailedCount = fail
                                                    currentStep = 4
                                                }
                                            }

                                            "PDF_COMPRESS" -> {
                                                val out = if (pdfCompressMode == "CUSTOM") {
                                                    val limitVal = pdfTargetInput.toDoubleOrNull() ?: 500.0
                                                    val mult = if (pdfTargetUnit == "MB") 1024L * 1024L else 1024L
                                                    val targetBytes = (limitVal * mult).toLong()
                                                    PdfProcessor.compressPdfToTargetSize(context, selectedUri!!, targetBytes)
                                                } else {
                                                    PdfProcessor.compressPdf(context, selectedUri!!, pdfCompressMode)
                                                }

                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "IMAGE_TO_PDF" -> {
                                                val margin = marginPoints.toIntOrNull() ?: 18
                                                val pdfFile = PdfProcessor.imagesToPdf(
                                                    context, selectedUris, pageSize, isPortrait, margin, pdfImageQuality.toInt()
                                                )
                                                withContext(Dispatchers.Main) {
                                                    editedFile = pdfFile
                                                    currentStep = 4
                                                }
                                            }

                                            "IMAGE_RESIZE" -> {
                                                val w = resizeWidth.toIntOrNull() ?: 1080
                                                val h = resizeHeight.toIntOrNull() ?: 1080
                                                val format = originalMetadata?.format ?: "JPG"
                                                val out = ImageProcessor.resizeImage(context, selectedUri!!, w, h, format)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    editedMetadata = ImageProcessor.getMetadata(context, Uri.fromFile(out))
                                                    currentStep = 4
                                                }
                                            }

                                            "IMAGE_CONVERT" -> {
                                                val out = ImageProcessor.convertImageFormat(context, selectedUri!!, exportFormat)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "IMAGE_CROP" -> {
                                                val format = originalMetadata?.format ?: "JPG"
                                                val out = ImageProcessor.cropImage(context, selectedUri!!, cropRectNormalized, format)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "IMAGE_ROTATE" -> {
                                                val format = originalMetadata?.format ?: "JPG"
                                                val out = ImageProcessor.rotateAndFlip(context, selectedUri!!, rotateDegrees, flipHorizontal, flipVertical, format)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "PDF_TO_IMAGE" -> {
                                                val files = PdfProcessor.pdfToImages(context, selectedUri!!, pdfSelectedPagesToConvert, pdfExportImageFormat, pdfExportQuality.toInt())
                                                withContext(Dispatchers.Main) {
                                                    batchResultFiles.clear()
                                                    batchResultFiles.addAll(files)
                                                    currentStep = 4
                                                }
                                            }

                                            "PDF_MERGE" -> {
                                                val out = PdfProcessor.mergePdfs(context, selectedUris)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "PDF_SPLIT", "PDF_EXTRACT" -> {
                                                val pages = mutableListOf<Int>()
                                                pdfTargetInput.split(",").forEach { part ->
                                                    val trimmed = part.trim()
                                                    if (trimmed.contains("-")) {
                                                        val bounds = trimmed.split("-")
                                                        if (bounds.size == 2) {
                                                            val start = (bounds[0].toIntOrNull() ?: 1) - 1
                                                            val end = (bounds[1].toIntOrNull() ?: 1) - 1
                                                            for (p in start..end) {
                                                                if (p >= 0) pages.add(p)
                                                            }
                                                        }
                                                    } else {
                                                        val p = (trimmed.toIntOrNull() ?: 1) - 1
                                                        if (p >= 0) pages.add(p)
                                                    }
                                                }
                                                val out = PdfProcessor.splitPdf(context, selectedUri!!, pages)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "PDF_ROTATE" -> {
                                                val out = PdfProcessor.rotatePdf(context, selectedUri!!, pdfRotateDegrees, emptyList())
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }

                                            "PDF_REORDER" -> {
                                                val out = PdfProcessor.splitPdf(context, selectedUri!!, pdfPageOrder)
                                                withContext(Dispatchers.Main) {
                                                    editedFile = out
                                                    currentStep = 4
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            errorMessage = e.message ?: "An unexpected processing error occurred."
                                            currentStep = 4
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) { isProcessingLocal = false }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .height(52.dp)
                                .testTag("process_button"),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Text("Process", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                3 -> {
                    // STEP 3: PROCESSING / PROGRESS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = statusMessageLocal,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please keep the app open",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                4 -> {
                    // STEP 4: STANDARDIZED SUCCESS / RESULT SCREEN
                    errorMessage?.let { errorStr ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                        RoundedCornerShape(40.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Something went wrong", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorStr, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { currentStep = 2 },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                             ) {
                                Text("Try Again", fontWeight = FontWeight.Bold)
                             }
                             Spacer(modifier = Modifier.height(12.dp))
                             TextButton(onClick = onNavigateBack) {
                                 Text("Back to Home", fontWeight = FontWeight.Bold)
                             }
                        }
                    } ?: run {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(40.dp))
                                            .background(Color(0xFFE8F5E9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32), modifier = Modifier.size(50.dp))
                                    }
                                }

                                item {
                                    Text(
                                        text = "PROCESS COMPLETE",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 2.sp
                                        ),
                                        color = Color(0xFF2E7D32)
                                    )
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("File Information", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                            if (toolType == "BATCH_COMPRESS") {
                                                TextRow("Output Directory", "/Picture/ImageCompressor")
                                                TextRow("Success count", "$batchSuccessCount files")
                                                if (batchFailedCount > 0) {
                                                    TextRow("Failed count", "$batchFailedCount files")
                                                }
                                                val origSize = batchOriginalTotalSize
                                                val finalSize = batchNewTotalSize
                                                if (origSize > 0) {
                                                    TextRow("Original Size", Formatter.formatFileSize(context, origSize))
                                                    TextRow("Optimized Size", Formatter.formatFileSize(context, finalSize))
                                                    val saved = origSize - finalSize
                                                    if (saved > 0) {
                                                        val savings = ((saved.toFloat() / origSize.toFloat()) * 100).toInt()
                                                        TextRow("Space Saved", Formatter.formatFileSize(context, saved))
                                                        TextRow("Savings %", "$savings% reduction")
                                                    }
                                                }
                                            } else {
                                                TextRow("File Name", editedFile?.name ?: customName)
                                                TextRow("File Type", if (isPdf) "PDF Document" else "Image File")

                                                val isImageResize = toolType == "IMAGE_RESIZE" || toolType == "IMAGE_COMPRESS"
                                                val isPdfCompress = toolType == "PDF_COMPRESS"

                                                val origSize = if (isImageResize) (originalMetadata?.fileSize ?: 0L) else if (isPdfCompress) {
                                                    selectedUri?.let { context.contentResolver.openAssetFileDescriptor(it, "r")?.use { afd -> afd.length } } ?: 0L
                                                } else 0L
                                                val finalSize = editedFile?.length() ?: 0L

                                                if (origSize > 0) {
                                                    TextRow("Original Size", Formatter.formatFileSize(context, origSize))
                                                    TextRow("Optimized Size", Formatter.formatFileSize(context, finalSize))
                                                    val saved = origSize - finalSize
                                                    if (saved > 0) {
                                                        val savings = ((saved.toFloat() / origSize.toFloat()) * 100).toInt()
                                                        TextRow("Space Saved", Formatter.formatFileSize(context, saved))
                                                        TextRow("Savings %", "$savings% reduction")
                                                    }
                                                } else {
                                                    TextRow("File Size", Formatter.formatFileSize(context, finalSize))
                                                }

                                                if (toolType == "IMAGE_RESIZE" && originalMetadata != null && editedMetadata != null) {
                                                    TextRow("Original Resolution", originalMetadata!!.resolution)
                                                    TextRow("New Resolution", editedMetadata!!.resolution)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!downloadCompleted) {
                                    Button(
                                        onClick = {
                                            isProcessingLocal = true
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    var success = false
                                                    if (toolType == "BATCH_COMPRESS") {
                                                        var allSaved = true
                                                        for (file in batchResultFiles) {
                                                            val saved = ImageProcessor.saveImageToGallery(context, file, file.nameWithoutExtension, file.extension.uppercase())
                                                            if (saved == null) allSaved = false
                                                        }
                                                        success = allSaved
                                                    } else {
                                                        editedFile?.let { file ->
                                                            val savedUri = if (isPdf) {
                                                                PdfProcessor.savePdfToDownloads(context, file, customName)
                                                            } else {
                                                                ImageProcessor.saveImageToGallery(context, file, customName, file.extension.uppercase())
                                                            }
                                                            success = savedUri != null
                                                        }
                                                    }

                                                    withContext(Dispatchers.Main) {
                                                        if (success) {
                                                            downloadCompleted = true
                                                            android.widget.Toast.makeText(context, "Saved to device successfully.", android.widget.Toast.LENGTH_LONG).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Failed to save files.", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                } finally {
                                                    withContext(Dispatchers.Main) { isProcessingLocal = false }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("download_button"),
                                        shape = RoundedCornerShape(25.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (toolType == "BATCH_COMPRESS") "Save All to Gallery" else "Download / Save", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = onNavigateBack,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("back_to_home_button"),
                                        shape = RoundedCornerShape(25.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Back to Home", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val fileToShare = if (toolType == "BATCH_COMPRESS") batchResultFiles.firstOrNull() else editedFile
                                            fileToShare?.let { file ->
                                                try {
                                                    val shareIntent = android.content.Intent().apply {
                                                        action = android.content.Intent.ACTION_SEND
                                                        val authUri = androidx.core.content.FileProvider.getUriForFile(
                                                            context, "${context.packageName}.fileprovider", file
                                                        )
                                                        putExtra(android.content.Intent.EXTRA_STREAM, authUri)
                                                        type = if (isPdf) "application/pdf" else "image/*"
                                                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share File"))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share", fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val fileToOpen = if (toolType == "BATCH_COMPRESS") batchResultFiles.firstOrNull() else editedFile
                                            fileToOpen?.let { file ->
                                                try {
                                                    val authUri = androidx.core.content.FileProvider.getUriForFile(
                                                        context, "${context.packageName}.fileprovider", file
                                                    )
                                                    val intent = android.content.Intent().apply {
                                                        action = android.content.Intent.ACTION_VIEW
                                                        setDataAndType(authUri, if (isPdf) "application/pdf" else "image/*")
                                                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "No compatible viewer app found!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
