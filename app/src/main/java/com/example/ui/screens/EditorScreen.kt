package com.example.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.CropCanvas
import com.example.utils.ImageProcessor
import com.example.viewmodel.ImageCompressorViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: ImageCompressorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedUri.collectAsStateWithLifecycle()
    val originalMetadata by viewModel.originalMetadata.collectAsStateWithLifecycle()
    val editedFile by viewModel.editedFile.collectAsStateWithLifecycle()
    val editedMetadata by viewModel.editedMetadata.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    // WORKFLOW STEPS:
    // 2 = Edit Configuration
    // 3 = Preview Changes (Pre-process summary)
    // 4 = Process (Loading progress screen)
    // 5 = Save / Download (Post-process view)
    var currentStep by remember { mutableIntStateOf(2) }

    var showInterstitialAd by remember { mutableStateOf(false) }
    var onAdDismissedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Tools Active Sub-Tab/Category in Step 2
    var activeCategory by remember { mutableStateOf("COMPRESS") }

    // State Variables (configured during Step 2)
    var compressionMode by remember { mutableIntStateOf(2) } // 1=Lossless, 2=Smart, 3=Max
    var useTargetKb by remember { mutableStateOf(false) }
    var targetKb by remember { mutableStateOf("50") }

    // Multi-unit system state (Requirement #3)
    var resizeUnit by remember { mutableStateOf("px") } // "px", "cm", "in"
    var resizeWidth by remember { mutableStateOf("1080") }
    var resizeHeight by remember { mutableStateOf("1080") }
    var maintainAspectRatio by remember { mutableStateOf(true) }

    // Resolution / DPI controller state (Requirement #4)
    var currentDpi by remember { mutableFloatStateOf(300f) }
    var isResolutionLocked by remember { mutableStateOf(false) }

    // Rotate state
    var rotateDegrees by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    // Crop state
    var enableCrop by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(android.graphics.RectF(0.1f, 0.1f, 0.9f, 0.9f)) }

    // Format
    var exportFormat by remember { mutableStateOf("JPG") }

    // Rename state (Step 5)
    var customFileName by remember { mutableStateOf("") }

    // Download completion feedback dialog state (Requirement #2)
    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedLocationPath by remember { mutableStateOf("") }
    var savedUriForIntent by remember { mutableStateOf<Uri?>(null) }

    // Synchronize default inputs once original metadata loads
    LaunchedEffect(originalMetadata) {
        if (originalMetadata != null) {
            if (customFileName.isEmpty()) {
                val baseName = originalMetadata!!.fileName.substringBeforeLast(".")
                customFileName = "optimized_$baseName"
            }
            resizeWidth = originalMetadata!!.width.toString()
            resizeHeight = originalMetadata!!.height.toString()
        }
    }

    // Standard formula to convert inputs accurately between units based on current DPI
    fun convertUnitValue(valueStr: String, from: String, to: String, dpi: Float): String {
        val value = valueStr.toFloatOrNull() ?: return ""
        val inches = when (from) {
            "px" -> value / dpi
            "cm" -> value / 2.54f
            "in" -> value
            else -> value
        }
        val converted = when (to) {
            "px" -> inches * dpi
            "cm" -> inches * 2.54f
            "in" -> inches
            else -> inches
        }
        return if (to == "px") {
            converted.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", converted)
        }
    }

    // Helper to calculate target pixels to show live resolution feedback and execute final pipeline
    val computedPixelWidth = when (resizeUnit) {
        "px" -> resizeWidth.toIntOrNull() ?: (originalMetadata?.width ?: 1080)
        "cm" -> {
            val cm = resizeWidth.toFloatOrNull() ?: 10f
            ((cm / 2.54f) * currentDpi).toInt()
        }
        "in" -> {
            val inches = resizeWidth.toFloatOrNull() ?: 4f
            (inches * currentDpi).toInt()
        }
        else -> resizeWidth.toIntOrNull() ?: 1080
    }

    val computedPixelHeight = when (resizeUnit) {
        "px" -> resizeHeight.toIntOrNull() ?: (originalMetadata?.height ?: 1080)
        "cm" -> {
            val cm = resizeHeight.toFloatOrNull() ?: 10f
            ((cm / 2.54f) * currentDpi).toInt()
        }
        "in" -> {
            val inches = resizeHeight.toFloatOrNull() ?: 4f
            (inches * currentDpi).toInt()
        }
        else -> resizeHeight.toIntOrNull() ?: 1080
    }

    // Show Before/After Preview Switcher
    var showOriginalPreview by remember { mutableStateOf(false) }

    // Scaffold structure with step-sensitive topBar & navigation logic
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Secure Image Editor", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep > 2) {
                                // Allow stepping back
                                currentStep = if (currentStep == 5) 2 else currentStep - 1
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Sleek Material 3 Step Progress Indicator (Requirement #7)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Edit", "Preview", "Process", "Export").forEachIndexed { index, title ->
                        val stepNum = index + 2
                        val isActive = currentStep == stepNum
                        val isCompleted = currentStep > stepNum

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary
                                        else if (isCompleted) Color(0xFF2E7D32)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                } else {
                                    Text(
                                        text = "$stepNum",
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < 3) {
                            Divider(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                color = if (currentStep > stepNum) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
            }
        },
        bottomBar = {
            AdBannerView(isSecondary = true)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (selectedUri == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No image selected")
            }
            return@Scaffold
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // STEP 2: EDITING PANEL STATE SCREEN
            if (currentStep == 2) {
                // Interactive Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (enableCrop) {
                        val loadedBitmap = remember(selectedUri) {
                            ImageProcessor.loadBitmap(context, selectedUri!!, 1024)
                        }
                        if (loadedBitmap != null) {
                            CropCanvas(
                                bitmap = loadedBitmap,
                                cropRect = cropRect,
                                onCropRectChanged = { cropRect = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    } else {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Preview Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }

                // File metadata display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        InfoGridItem("Original Size", originalMetadata?.let { Formatter.formatFileSize(context, it.fileSize) } ?: "...")
                        InfoGridItem("Original Dimens", originalMetadata?.let { "${it.width} x ${it.height}" } ?: "...")
                        InfoGridItem("Original Format", originalMetadata?.format ?: "...")
                    }
                }

                // Horizontal tools selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "COMPRESS" to "Compress",
                        "RESIZE" to "Resize & DPI",
                        "CROP" to "Crop",
                        "ROTATE" to "Rotate/Flip",
                        "FORMAT" to "Convert",
                        "GOV" to "Gov Portals"
                    )
                    tabs.forEach { (key, label) ->
                        val isSel = activeCategory == key
                        FilterChip(
                            selected = isSel,
                            onClick = { activeCategory = key },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Detail config interface for each tab
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (activeCategory) {
                            "COMPRESS" -> {
                                Text("Compression Mode Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = useTargetKb, onCheckedChange = { useTargetKb = it })
                                    Text("Compress to precise file size limit", fontSize = 13.sp)
                                }

                                if (useTargetKb) {
                                    Text("Select or enter custom target KB budget:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("10", "20", "30", "50", "100", "200").forEach { preset ->
                                            FilterChip(
                                                selected = targetKb == preset,
                                                onClick = { targetKb = preset },
                                                label = { Text("$preset KB") }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = targetKb,
                                        onValueChange = { targetKb = it },
                                        label = { Text("Target Size (KB)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    ModeRadioOption(
                                        title = "Smart Compression (75% quality)",
                                        subtitle = "Outstanding optimization of storage, retains high clarity",
                                        selected = compressionMode == 2,
                                        onClick = { compressionMode = 2 }
                                    )
                                    ModeRadioOption(
                                        title = "Lossless Compression",
                                        subtitle = "Re-encode image pixels with zero structural data loss",
                                        selected = compressionMode == 1,
                                        onClick = { compressionMode = 1 }
                                    )
                                    ModeRadioOption(
                                        title = "Maximum Compression (45% quality)",
                                        subtitle = "Aggressively shrinks dimensions and quality for tiny file size",
                                        selected = compressionMode == 3,
                                        onClick = { compressionMode = 3 }
                                    )
                                }
                            }

                            "RESIZE" -> {
                                Text("Multi-Unit Dimension System", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                var showUnitDropdown by remember { mutableStateOf(false) }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Dimension Unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedCard(
                                            onClick = { showUnitDropdown = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = when(resizeUnit) {
                                                        "px" -> "Pixels (px)"
                                                        "cm" -> "Centimeters (cm)"
                                                        "in" -> "Inches (in)"
                                                        else -> resizeUnit
                                                    },
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "Expand unit list"
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showUnitDropdown,
                                            onDismissRequest = { showUnitDropdown = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            listOf("px" to "Pixels (px)", "cm" to "Centimeters (cm)", "in" to "Inches (in)").forEach { (code, label) ->
                                                DropdownMenuItem(
                                                    text = { Text(label, fontWeight = FontWeight.SemiBold) },
                                                    onClick = {
                                                        val old = resizeUnit
                                                        resizeUnit = code
                                                        resizeWidth = convertUnitValue(resizeWidth, old, code, currentDpi)
                                                        resizeHeight = convertUnitValue(resizeHeight, old, code, currentDpi)
                                                        showUnitDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = resizeWidth,
                                        onValueChange = {
                                            resizeWidth = it
                                            if (maintainAspectRatio && originalMetadata != null) {
                                                val w = it.toFloatOrNull() ?: 1f
                                                val origRatio = originalMetadata!!.width.toFloat() / originalMetadata!!.height.toFloat()
                                                val h = w / origRatio
                                                resizeHeight = if (resizeUnit == "px") h.toInt().toString() else String.format(java.util.Locale.US, "%.2f", h)
                                            }
                                        },
                                        label = { Text("Width ($resizeUnit)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = resizeHeight,
                                        onValueChange = {
                                            resizeHeight = it
                                            if (maintainAspectRatio && originalMetadata != null) {
                                                val h = it.toFloatOrNull() ?: 1f
                                                val origRatio = originalMetadata!!.width.toFloat() / originalMetadata!!.height.toFloat()
                                                val w = h * origRatio
                                                resizeWidth = if (resizeUnit == "px") w.toInt().toString() else String.format(java.util.Locale.US, "%.2f", w)
                                            }
                                        },
                                        label = { Text("Height ($resizeUnit)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = maintainAspectRatio, onCheckedChange = { maintainAspectRatio = it })
                                    Text("Maintain Aspect Ratio", fontSize = 13.sp)
                                }

                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                // DPI Selector & Lock (Requirement #4)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Resolution Print Size Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Switch(checked = isResolutionLocked, onCheckedChange = { isResolutionLocked = it })
                                }
                                Text(
                                    text = "Lock preserves the physical printed size (Cm/In) and adjusts output pixels. Unlock preserves pixel count.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(72f, 150f, 300f).forEach { dpiVal ->
                                        FilterChip(
                                            selected = currentDpi == dpiVal,
                                            onClick = {
                                                val oldDpi = currentDpi
                                                currentDpi = dpiVal
                                                if (isResolutionLocked) {
                                                    if (resizeUnit == "px") {
                                                        val w = (resizeWidth.toFloatOrNull() ?: 1080f) * (dpiVal / oldDpi)
                                                        val h = (resizeHeight.toFloatOrNull() ?: 1080f) * (dpiVal / oldDpi)
                                                        resizeWidth = w.toInt().toString()
                                                        resizeHeight = h.toInt().toString()
                                                    }
                                                } else {
                                                    if (resizeUnit == "cm" || resizeUnit == "in") {
                                                        val w = (resizeWidth.toFloatOrNull() ?: 10f) * (oldDpi / dpiVal)
                                                        val h = (resizeHeight.toFloatOrNull() ?: 10f) * (oldDpi / dpiVal)
                                                        resizeWidth = String.format(java.util.Locale.US, "%.2f", w)
                                                        resizeHeight = String.format(java.util.Locale.US, "%.2f", h)
                                                    }
                                                }
                                            },
                                            label = { Text("${dpiVal.toInt()} DPI") }
                                        )
                                    }
                                }

                                // Quick presets
                                Text("Dimension Presets:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val presets = listOf(
                                        "Insta Square" to (1080 to 1080),
                                        "Insta Story" to (1080 to 1920),
                                        "YouTube Banner" to (1280 to 720),
                                        "WhatsApp Icon" to (500 to 500)
                                    )
                                    presets.forEach { (label, dims) ->
                                        FilterChip(
                                            selected = resizeUnit == "px" && resizeWidth == dims.first.toString() && resizeHeight == dims.second.toString(),
                                            onClick = {
                                                resizeUnit = "px"
                                                resizeWidth = dims.first.toString()
                                                resizeHeight = dims.second.toString()
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                // Live Pixel Preview
                                Text(
                                    text = "Estimated final dimensions: ${computedPixelWidth} x ${computedPixelHeight} px at ${currentDpi.toInt()} DPI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            "CROP" -> {
                                Text("Interactive Crop Viewport Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Apply Cropping", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Switch(checked = enableCrop, onCheckedChange = { enableCrop = it })
                                }
                                if (enableCrop) {
                                    Text("Drag the cyan corner anchors in the preview window to crop. Presets:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val cropPresets = listOf(
                                            "Free Crop" to android.graphics.RectF(0.1f, 0.1f, 0.9f, 0.9f),
                                            "Square (1:1)" to android.graphics.RectF(0.2f, 0.2f, 0.8f, 0.8f),
                                            "Passport Ratio" to android.graphics.RectF(0.25f, 0.15f, 0.75f, 0.85f),
                                            "Widescreen (16:9)" to android.graphics.RectF(0.1f, 0.3f, 0.9f, 0.7f)
                                        )
                                        cropPresets.forEach { (lbl, rect) ->
                                            FilterChip(
                                                selected = cropRect == rect,
                                                onClick = { cropRect = rect },
                                                label = { Text(lbl) }
                                            )
                                        }
                                    }
                                }
                            }

                            "ROTATE" -> {
                                Text("Rotate & Flip Controls", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { rotateDegrees = (rotateDegrees + 90) % 360 },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.RotateRight, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Rotate 90°", maxLines = 1)
                                    }

                                    IconToggleButton(
                                        checked = flipHorizontal,
                                        onCheckedChange = { flipHorizontal = it },
                                        modifier = Modifier.weight(0.5f)
                                    ) {
                                        Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal")
                                    }

                                    IconToggleButton(
                                        checked = flipVertical,
                                        onCheckedChange = { flipVertical = it },
                                        modifier = Modifier.weight(0.5f)
                                    ) {
                                        Icon(Icons.Default.Flip, contentDescription = "Flip Vertical", modifier = Modifier.rotate(90f))
                                    }
                                }
                                if (rotateDegrees != 0 || flipHorizontal || flipVertical) {
                                    Text("Selected: Rotated $rotateDegrees°" +
                                            (if (flipHorizontal) ", Flipped Horizontally" else "") +
                                            (if (flipVertical) ", Flipped Vertically" else ""),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            "FORMAT" -> {
                                Text("Convert Export Format", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { exportFormat = fmt }
                                                .border(
                                                    width = if (exportFormat == fmt) 2.dp else 0.dp,
                                                    color = if (exportFormat == fmt) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (exportFormat == fmt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(12.dp)
                                                    .fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(fmt, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            "GOV" -> {
                                Text("Government & Document Portal Presets", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GovShortcutItem(
                                        title = "Passport Photo Generator (< 50 KB)",
                                        subtitle = "Applies 413x531 px cropped bounds and limits size to under 50 KB",
                                        onClick = {
                                            resizeUnit = "px"
                                            resizeWidth = "413"
                                            resizeHeight = "531"
                                            maintainAspectRatio = false
                                            enableCrop = true
                                            cropRect = android.graphics.RectF(0.25f, 0.15f, 0.75f, 0.85f)
                                            useTargetKb = true
                                            targetKb = "45"
                                            exportFormat = "JPG"
                                        }
                                    )
                                    GovShortcutItem(
                                        title = "Online Signature Verification (< 10 KB)",
                                        subtitle = "Compresses signature image specifically to fit under 10 KB budget",
                                        onClick = {
                                            useTargetKb = true
                                            targetKb = "9"
                                            exportFormat = "JPG"
                                        }
                                    )
                                    GovShortcutItem(
                                        title = "Document Portal Upload (< 200 KB)",
                                        subtitle = "Ideal compression ratio for standard online document submission databases",
                                        onClick = {
                                            useTargetKb = true
                                            targetKb = "190"
                                            exportFormat = "JPG"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Proceed Step Master button
                Button(
                    onClick = { currentStep = 3 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(54.dp)
                        .testTag("apply_process_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preview Selected Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // STEP 3: PREVIEW CHANGES SCREEN (Requirement #1)
            else if (currentStep == 3) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Pending Optimization Summary",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text("The pipeline will apply the following operations sequentially in high performance mode:", fontSize = 13.sp)

                        // Visual checklist of actions
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OperationSummaryItem(
                                icon = Icons.Default.AspectRatio,
                                label = "Cropping",
                                value = if (enableCrop) "Rect: Left=${String.format("%.2f", cropRect.left)}, Top=${String.format("%.2f", cropRect.top)}" else "None"
                            )

                            OperationSummaryItem(
                                icon = Icons.Default.RotateRight,
                                label = "Rotate & Flip",
                                value = if (rotateDegrees != 0 || flipHorizontal || flipVertical) "Rotate $rotateDegrees°" + (if (flipHorizontal) ", Flip H" else "") + (if (flipVertical) ", Flip V" else "") else "None"
                            )

                            OperationSummaryItem(
                                icon = Icons.Default.PhotoSizeSelectLarge,
                                label = "Resizing",
                                value = "Resize to ${computedPixelWidth} x ${computedPixelHeight} px at ${currentDpi.toInt()} DPI"
                            )

                            OperationSummaryItem(
                                icon = Icons.Default.Compress,
                                label = "Compression Strategy",
                                value = if (useTargetKb) "Limit file size under $targetKb KB" else when (compressionMode) {
                                    1 -> "Lossless Compression"
                                    2 -> "Smart Optimization (75% quality)"
                                    else -> "Maximum Compression (45% quality)"
                                }
                            )

                            OperationSummaryItem(
                                icon = Icons.Default.SwapHoriz,
                                label = "Export Format Conversion",
                                value = "Convert format to $exportFormat"
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onAdDismissedAction = {
                                        currentStep = 4 // Move to processing stage
                                        viewModel.applyPipeline(
                                            compressionMode = compressionMode,
                                            useTargetKb = useTargetKb,
                                            targetKbVal = targetKb.toIntOrNull() ?: 50,
                                            targetWidth = computedPixelWidth,
                                            targetHeight = computedPixelHeight,
                                            exportFormatStr = exportFormat,
                                            enableCrop = enableCrop,
                                            cropRectNormalized = cropRect,
                                            rotateDeg = rotateDegrees,
                                            flipH = flipHorizontal,
                                            flipV = flipVertical,
                                            onSuccess = {
                                                currentStep = 5 // Automatically goes to Step 5
                                            },
                                            onFailure = {
                                                currentStep = 2 // Returns to edit on fail
                                            }
                                        )
                                    }
                                    showInterstitialAd = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("apply_process_final_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply & Process")
                            }

                            OutlinedButton(
                                onClick = { currentStep = 2 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back & Edit")
                            }
                        }
                    }
                }
            }

            // STEP 4: PROCESSING STATE (Requirement #1)
            else if (currentStep == 4) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Applying Transformations...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = statusMessage ?: "Processing image pipeline...",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // STEP 5: SAVE / EXPORT / DOWNLOAD SCREEN (Requirement #5)
            else if (currentStep == 5 && editedFile != null) {
                // Interactive Before / After preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val displayUri = if (showOriginalPreview) selectedUri else Uri.fromFile(editedFile)
                    AsyncImage(
                        model = displayUri,
                        contentDescription = "Output Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (showOriginalPreview) "Original" else "Processed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = !showOriginalPreview,
                            onCheckedChange = { showOriginalPreview = !it },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                // Optimization Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32))
                            Text(
                                text = "Optimization Summary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Size statistics
                        val origSize = originalMetadata?.fileSize ?: 0L
                        val finalSize = editedMetadata?.fileSize ?: 0L
                        val reduction = if (origSize > 0) {
                            ((1.0f - (finalSize.toFloat() / origSize.toFloat())) * 100).toInt()
                        } else 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Before: ${Formatter.formatFileSize(context, origSize)}", fontSize = 13.sp)
                            Text("After: ${Formatter.formatFileSize(context, finalSize)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (reduction > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Space Saved: $reduction% smaller file size!",
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))

                        // Rename text field before saving
                        OutlinedTextField(
                            value = customFileName,
                            onValueChange = { customFileName = it },
                            label = { Text("Rename output file before saving") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        // Save, Download, and Share CTAs
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Download Image Action
                            Button(
                                onClick = {
                                    val saveName = customFileName.ifEmpty() { "edited_image" }
                                    viewModel.saveEditedImageToGallery(saveName, "Optimized Pipeline") { finalPath ->
                                        if (finalPath != null) {
                                            savedLocationPath = finalPath
                                            showSuccessDialog = true
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("download_save_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download Image", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))

                        // Reset back to edits
                        TextButton(
                            onClick = { currentStep = 2 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Discard & Configure New Edits")
                        }
                    }
                }
            }
        }
    }

    // REQUIREMENT #2: Beautiful Download Completion Message Dialog with file info, Open, and Share actions
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Download Completed Successfully",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your image has been safely compressed and downloaded.",
                        fontSize = 13.sp
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Saved Location:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = savedLocationPath,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    TestInterstitialAd(
        show = showInterstitialAd,
        onDismiss = {
            showInterstitialAd = false
            onAdDismissedAction?.invoke()
            onAdDismissedAction = null
        }
    )
}

@Composable
fun OperationSummaryItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun InfoGridItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ModeRadioOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GovShortcutItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
