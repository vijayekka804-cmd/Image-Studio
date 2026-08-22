package com.example.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.*
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.viewmodel.ImageCompressorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    viewModel: ImageCompressorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showInterstitialAd by remember { mutableStateOf(false) }

    val handleBackWithAd = {
        showInterstitialAd = true
    }

    val selectedUris by viewModel.selectedUrisForBatch.collectAsStateWithLifecycle()
    val isBatchProcessing by viewModel.batchIsProcessing.collectAsStateWithLifecycle()
    val progress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.batchStatusText.collectAsStateWithLifecycle()
    val finishedCount by viewModel.batchFinishedCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.batchTotalCount.collectAsStateWithLifecycle()
    val batchOutputs by viewModel.batchOutputFiles.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf("COMPRESS_SMART") }
    var targetKb by remember { mutableStateOf("50") }
    var selectedFormat by remember { mutableStateOf("JPG") }
    
    // States for custom size dimensions
    var resizeWidth by remember { mutableStateOf("1080") }
    var resizeHeight by remember { mutableStateOf("1080") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Batch Processing", fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp) },
                    navigationIcon = {
                        IconButton(onClick = { handleBackWithAd() }) {
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
            AdBannerView()
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image previews grid/row
                Text(
                    text = "SELECTED IMAGES (${selectedUris.size})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(selectedUris) { uri ->
                        Card(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Select Action UI
                if (batchOutputs.isEmpty() && !isBatchProcessing) {
                    Text(
                        text = "Choose Batch Action",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Mode selections List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            BatchOptionRow(
                                title = "Smart Compression",
                                subtitle = "Balanced quality and small size",
                                icon = Icons.Default.AutoAwesome,
                                isSelected = selectedMode == "COMPRESS_SMART",
                                onClick = { selectedMode = "COMPRESS_SMART" }
                            )
                        }

                        item {
                            BatchOptionRow(
                                title = "Lossless Compression",
                                subtitle = "Zero quality reduction",
                                icon = Icons.Default.HighQuality,
                                isSelected = selectedMode == "COMPRESS_LOSSLESS",
                                onClick = { selectedMode = "COMPRESS_LOSSLESS" }
                            )
                        }

                        item {
                            BatchOptionRow(
                                title = "Maximum Compression",
                                subtitle = "Smallest possible file size",
                                icon = Icons.Default.Compress,
                                isSelected = selectedMode == "COMPRESS_MAX",
                                onClick = { selectedMode = "COMPRESS_MAX" }
                            )
                        }

                        item {
                            Column {
                                BatchOptionRow(
                                    title = "Target Size (KB)",
                                    subtitle = "Compress each file under custom size",
                                    icon = Icons.Default.GpsFixed,
                                    isSelected = selectedMode == "TARGET_SIZE",
                                    onClick = { selectedMode = "TARGET_SIZE" }
                                )
                                if (selectedMode == "TARGET_SIZE") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, start = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        listOf("20", "50", "100", "200").forEach { preset ->
                                            FilterChip(
                                                selected = targetKb == preset,
                                                onClick = { targetKb = preset },
                                                label = { Text("${preset} KB") }
                                            )
                                        }
                                        OutlinedTextField(
                                            value = targetKb,
                                            onValueChange = { targetKb = it },
                                            modifier = Modifier.width(90.dp),
                                            label = { Text("Custom") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedContainerColor = Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Column {
                                BatchOptionRow(
                                    title = "Convert Image Format",
                                    subtitle = "Convert images all at once",
                                    icon = Icons.Default.SwapHoriz,
                                    isSelected = selectedMode == "CONVERT",
                                    onClick = { selectedMode = "CONVERT" }
                                )
                                if (selectedMode == "CONVERT") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, start = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                                            FilterChip(
                                                selected = selectedFormat == fmt,
                                                onClick = { selectedFormat = fmt },
                                                label = { Text(fmt) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column {
                                BatchOptionRow(
                                    title = "Batch Resize",
                                    subtitle = "Resize dimensions of all images",
                                    icon = Icons.Default.AspectRatio,
                                    isSelected = selectedMode == "RESIZE",
                                    onClick = { selectedMode = "RESIZE" }
                                )
                                if (selectedMode == "RESIZE") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, start = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = resizeWidth,
                                            onValueChange = { resizeWidth = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Width (px)") },
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = resizeHeight,
                                            onValueChange = { resizeHeight = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Height (px)") },
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CTA Process Button
                    Button(
                        onClick = {
                            val p1: Any? = when (selectedMode) {
                                "TARGET_SIZE" -> targetKb.toIntOrNull() ?: 50
                                "CONVERT" -> selectedFormat
                                "RESIZE" -> resizeWidth.toIntOrNull() ?: 1080
                                else -> null
                            }
                            val p2: Any? = when (selectedMode) {
                                "RESIZE" -> resizeHeight.toIntOrNull() ?: 1080
                                else -> null
                            }
                            viewModel.runBatchProcessing(selectedMode, p1, p2)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("run_batch_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Batch Processing", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isBatchProcessing) {
                    // Processing State (Visual Spinner + Progress Bar)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = statusText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$finishedCount / $totalCount completed",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // COMPLETED STATE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Batch Processing Complete!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Successfully optimized $finishedCount files on-device.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Space savings calculation if available
                        var totalOriginalSize = 0L
                        val resolver = context.contentResolver
                        selectedUris.forEach { uri ->
                            try {
                                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                    totalOriginalSize += pfd.statSize
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        var totalNewSize = 0L
                        batchOutputs.forEach { totalNewSize += it.length() }
                        
                        val spaceSaved = (totalOriginalSize - totalNewSize).coerceAtLeast(0L)
                        val savingsPercent = if (totalOriginalSize > 0) {
                            (spaceSaved.toFloat() / totalOriginalSize.toFloat() * 100f).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Total Space Saved",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${Formatter.formatFileSize(context, spaceSaved)} ($savingsPercent% saved)",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Original: ${Formatter.formatFileSize(context, totalOriginalSize)}  •  New: ${Formatter.formatFileSize(context, totalNewSize)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Actions
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.saveAllBatchToGallery() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save All to Gallery")
                            }

                            OutlinedButton(
                                onClick = { handleBackWithAd() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Back to Home")
                            }
                        }
                    }
                }
            }
        }
    }

    TestInterstitialAd(
        show = showInterstitialAd,
        onDismiss = {
            showInterstitialAd = false
            onNavigateBack()
        }
    )
}

@Composable
fun BatchOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSelected) 0.6f else 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
