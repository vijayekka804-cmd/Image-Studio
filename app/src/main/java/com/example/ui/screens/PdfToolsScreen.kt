package com.example.ui.screens

import android.net.Uri
import android.text.format.Formatter
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.viewmodel.ImageCompressorViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: ImageCompressorViewModel,
    toolMode: String, // "IMAGE_TO_PDF", "PDF_TO_IMAGE", "COMPRESS", "MERGE", "SPLIT", "ROTATE", "REORDER", "EXTRACT"
    initialImageUris: List<Uri> = emptyList(),
    initialPdfUris: List<Uri> = emptyList(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    // ViewModel states
    val isProcessing by viewModel.pdfIsProcessing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.pdfStatusMessage.collectAsStateWithLifecycle()
    val pageCount by viewModel.pdfPageCount.collectAsStateWithLifecycle()
    val thumbnails by viewModel.pdfThumbnails.collectAsStateWithLifecycle()
    val pdfSelectedUri by viewModel.pdfSelectedUri.collectAsStateWithLifecycle()
    val editedFile by viewModel.pdfEditedFile.collectAsStateWithLifecycle()

    // Screen workflow steps:
    // 1 = Config/Operation setup
    // 2 = Success / Done state
    var currentStep by remember { mutableIntStateOf(1) }

    // Parameters for Image to PDF
    val selectedImages = remember { mutableStateListOf<Uri>().apply { addAll(initialImageUris) } }
    var pageSize by remember { mutableStateOf("A4") } // "A4", "LETTER", "ORIGINAL"
    var isPortrait by remember { mutableStateOf(true) }
    var marginPoints by remember { mutableStateOf("18") } // in points (1/4 inch roughly)
    var imageQuality by remember { mutableFloatStateOf(85f) }

    // Parameters for PDF to Image
    val selectedPagesToConvert = remember { mutableStateListOf<Int>() }
    var exportImageFormat by remember { mutableStateOf("JPG") }
    var conversionQuality by remember { mutableFloatStateOf(85f) }

    // Parameters for Compress
    var compressionMode by remember { mutableStateOf("BALANCED") } // "MAX", "BALANCED", "HIGH"

    // Parameters for Merge
    val pdfsToMerge = remember { mutableStateListOf<Uri>().apply { addAll(initialPdfUris) } }

    // Parameters for Split & Extract
    val selectedPagesToKeep = remember { mutableStateListOf<Int>() }

    // Parameters for Rotate
    var rotationAngle by remember { mutableIntStateOf(90) }
    val selectedPagesToRotate = remember { mutableStateListOf<Int>() }

    // Custom output name
    var customName by remember { mutableStateOf("image_studio_output") }

    // Add multiple images picker for Image To PDF adding
    val pickMoreImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.addAll(uris)
        }
    }

    // Add multiple PDFs picker for Merge adding
    val pickMorePdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pdfsToMerge.addAll(uris)
        }
    }

    // Dynamic initializations
    LaunchedEffect(toolMode, initialImageUris, initialPdfUris) {
        customName = when (toolMode) {
            "IMAGE_TO_PDF" -> "converted_images"
            "PDF_TO_IMAGE" -> "extracted_pages"
            "COMPRESS" -> "compressed_document"
            "MERGE" -> "merged_documents"
            "SPLIT" -> "split_document"
            "ROTATE" -> "rotated_document"
            "REORDER" -> "reordered_document"
            "EXTRACT" -> "extracted_document"
            else -> "output"
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        val titleText = when (toolMode) {
                            "IMAGE_TO_PDF" -> "Image to PDF"
                            "PDF_TO_IMAGE" -> "PDF to Image"
                            "COMPRESS" -> "Compress PDF"
                            "MERGE" -> "Merge PDFs"
                            "SPLIT" -> "Split PDF"
                            "ROTATE" -> "Rotate PDF"
                            "REORDER" -> "Reorder PDF"
                            "EXTRACT" -> "Extract Pages"
                            else -> "PDF Utility"
                        }
                        Text(titleText, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 1.dp)
            }
        },
        bottomBar = {
            AdBannerView(isSecondary = true)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isProcessing) {
                // Circular loading progress
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = statusMessage ?: "Processing, please wait...",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (currentStep == 2) {
                // Processing Success / Save state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(48.dp))
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Operation Completed!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your output file has been saved in the ImageStudio downloads folder on your device.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    editedFile?.let { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = file.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Size: " + Formatter.formatFileSize(context, file.length()),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back to Home", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Step 1: Config Form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title / Config sections based on toolMode
                        when (toolMode) {
                            "IMAGE_TO_PDF" -> {
                                item {
                                    Text("Image Source Files (${selectedImages.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(80.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(selectedImages) { index, uri ->
                                            Box(modifier = Modifier.size(80.dp)) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                IconButton(
                                                    onClick = { selectedImages.removeAt(index) },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(24.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                    .clickable {
                                                        pickMoreImagesLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add More", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    Text("Page Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Page sizes chips
                                    Text("PAGE SIZE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
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

                                    // Orientation Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Portrait Orientation", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Switch(checked = isPortrait, onCheckedChange = { isPortrait = it })
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Margin field
                                    OutlinedTextField(
                                        value = marginPoints,
                                        onValueChange = { marginPoints = it },
                                        label = { Text("Margin (points)") },
                                        placeholder = { Text("e.g., 18") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quality Slider
                                    Text("Image Quality: ${imageQuality.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = imageQuality,
                                        onValueChange = { imageQuality = it },
                                        valueRange = 10f..100f
                                    )
                                }
                            }

                            "PDF_TO_IMAGE" -> {
                                item {
                                    Text("Convert PDF to High-Fidelity Images", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Select which pages to convert. Tap on preview thumbnails to toggle pages, or leave empty to convert all.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                item {
                                    Text("PAGES PREVIEW (${pageCount} pages available)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (thumbnails.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Generating preview thumbnails...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(100.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            itemsIndexed(thumbnails) { index, bitmap ->
                                                val isSelected = selectedPagesToConvert.contains(index)
                                                Box(
                                                    modifier = Modifier
                                                        .size(100.dp, 130.dp)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            if (isSelected) selectedPagesToConvert.remove(index)
                                                            else selectedPagesToConvert.add(index)
                                                        }
                                                ) {
                                                    Image(
                                                        bitmap = bitmap.asImageBitmap(),
                                                        contentDescription = "Page ${index + 1}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .fillMaxWidth()
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                            .padding(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Page ${index + 1}",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Divider()
                                    Text("Image Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Formats
                                    Text("EXPORT FORMAT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("JPG", "PNG").forEach { fmt ->
                                            FilterChip(
                                                selected = exportImageFormat == fmt,
                                                onClick = { exportImageFormat = fmt },
                                                label = { Text(fmt) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Quality Slider
                                    Text("Encoding Quality: ${conversionQuality.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(
                                        value = conversionQuality,
                                        onValueChange = { conversionQuality = it },
                                        valueRange = 10f..100f
                                    )
                                }
                            }

                            "COMPRESS" -> {
                                item {
                                    Text("Optimize PDF Size", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Choose a compression level. Heavy documents with embedded pictures will show significant savings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            Triple("MAX", "Max Compression", "Lowest page resolution and image quality, highly efficient size reduction."),
                                            Triple("BALANCED", "Balanced Compression", "Good screen clarity and optimized file size footprint."),
                                            Triple("HIGH", "High Quality Compression", "Crisp text and layout clarity with subtle compression.")
                                        ).forEach { (mode, name, desc) ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (compressionMode == mode) 0.6f else 0.2f)),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (compressionMode == mode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                                ),
                                                onClick = { compressionMode = mode }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                                ) {
                                                    RadioButton(selected = compressionMode == mode, onClick = { compressionMode = mode })
                                                    Column {
                                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "MERGE" -> {
                                item {
                                    Text("PDF Files to Merge (${pdfsToMerge.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                itemsIndexed(pdfsToMerge) { index, uri ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Column {
                                                    val docName = remember(uri) { uri.lastPathSegment ?: "document_${index + 1}.pdf" }
                                                    Text(docName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            IconButton(onClick = { pdfsToMerge.removeAt(index) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedButton(
                                        onClick = {
                                            pickMorePdfsLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add More PDFs")
                                    }
                                }
                            }

                            "SPLIT", "EXTRACT" -> {
                                item {
                                    val actName = if (toolMode == "SPLIT") "Split PDF pages" else "Extract selected pages"
                                    Text(actName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tap on individual pages to select them. Only selected pages will be included in the final exported PDF.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                item {
                                    Text("PAGES PREVIEW", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (thumbnails.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Generating preview thumbnails...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(100.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 280.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            itemsIndexed(thumbnails) { index, bitmap ->
                                                val isSelected = selectedPagesToKeep.contains(index)
                                                Box(
                                                    modifier = Modifier
                                                        .size(100.dp, 130.dp)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            if (isSelected) selectedPagesToKeep.remove(index)
                                                            else selectedPagesToKeep.add(index)
                                                        }
                                                ) {
                                                    Image(
                                                        bitmap = bitmap.asImageBitmap(),
                                                        contentDescription = "Page ${index + 1}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .fillMaxWidth()
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                            .padding(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Page ${index + 1}",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "ROTATE" -> {
                                item {
                                    Text("Rotate PDF Page Orientations", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Select rotation degrees and target pages.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                item {
                                    Text("ROTATION ANGLE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(90, 180, 270).forEach { degrees ->
                                            FilterChip(
                                                selected = rotationAngle == degrees,
                                                onClick = { rotationAngle = degrees },
                                                label = { Text("$degrees°") }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("SELECT PAGES (Leave empty to rotate all)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (thumbnails.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Generating preview thumbnails...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(100.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            itemsIndexed(thumbnails) { index, bitmap ->
                                                val isSelected = selectedPagesToRotate.contains(index)
                                                Box(
                                                    modifier = Modifier
                                                        .size(100.dp, 130.dp)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            if (isSelected) selectedPagesToRotate.remove(index)
                                                            else selectedPagesToRotate.add(index)
                                                        }
                                                ) {
                                                    Image(
                                                        bitmap = bitmap.asImageBitmap(),
                                                        contentDescription = "Page ${index + 1}",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .fillMaxWidth()
                                                            .background(Color.Black.copy(alpha = 0.5f))
                                                            .padding(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Page ${index + 1}",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Shared fields: Filename output
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Output Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Output Filename") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Bottom main execution button
                    Button(
                        onClick = {
                            when (toolMode) {
                                "IMAGE_TO_PDF" -> {
                                    if (selectedImages.isEmpty()) {
                                        android.widget.Toast.makeText(context, "Please select at least 1 image!", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val mPoints = marginPoints.toIntOrNull() ?: 18
                                    viewModel.runImageToPdf(
                                        imageUris = selectedImages,
                                        pageSize = pageSize,
                                        isPortrait = isPortrait,
                                        margin = mPoints,
                                        quality = imageQuality.toInt(),
                                        customName = customName,
                                        onComplete = { file ->
                                            if (file != null) currentStep = 2
                                        }
                                    )
                                }

                                "PDF_TO_IMAGE" -> {
                                    pdfSelectedUri?.let { uri ->
                                        viewModel.runPdfToImages(
                                            pdfUri = uri,
                                            selectedPages = selectedPagesToConvert,
                                            format = exportImageFormat,
                                            quality = conversionQuality.toInt(),
                                            onComplete = { files ->
                                                if (files.isNotEmpty()) currentStep = 2
                                            }
                                        )
                                    }
                                }

                                "COMPRESS" -> {
                                    pdfSelectedUri?.let { uri ->
                                        viewModel.runPdfCompression(
                                            pdfUri = uri,
                                            compressionMode = compressionMode,
                                            customName = customName,
                                            onComplete = { file ->
                                                if (file != null) currentStep = 2
                                            }
                                        )
                                    }
                                }

                                "MERGE" -> {
                                    if (pdfsToMerge.size < 2) {
                                        android.widget.Toast.makeText(context, "Please add at least 2 PDFs to merge!", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.runPdfMerge(
                                        pdfUris = pdfsToMerge,
                                        customName = customName,
                                        onComplete = { file ->
                                            if (file != null) currentStep = 2
                                        }
                                    )
                                }

                                "SPLIT", "EXTRACT" -> {
                                    pdfSelectedUri?.let { uri ->
                                        if (selectedPagesToKeep.isEmpty()) {
                                            android.widget.Toast.makeText(context, "Please select at least 1 page!", android.widget.Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        viewModel.runPdfSplit(
                                            pdfUri = uri,
                                            keepPages = selectedPagesToKeep,
                                            customName = customName,
                                            onComplete = { file ->
                                                if (file != null) currentStep = 2
                                            }
                                        )
                                    }
                                }

                                "ROTATE" -> {
                                    pdfSelectedUri?.let { uri ->
                                        viewModel.runPdfRotation(
                                            pdfUri = uri,
                                            rotationAngle = rotationAngle,
                                            selectedPages = selectedPagesToRotate,
                                            customName = customName,
                                            onComplete = { file ->
                                                if (file != null) currentStep = 2
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("run_pdf_operation_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Process & Save Locally", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
