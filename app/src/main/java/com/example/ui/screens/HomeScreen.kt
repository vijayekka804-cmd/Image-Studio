package com.example.ui.screens

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HistoryEntity
import com.example.viewmodel.ImageCompressorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ImageCompressorViewModel,
    onNavigateToWizard: (String) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val history by viewModel.historyList.collectAsStateWithLifecycle()
    
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Δ",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = "Image Studio",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                                Text(
                                    text = "Image & PDF Tools",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.onBackground
                             )
                        }

                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("menu_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Privacy Policy") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showPrivacyDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Contact Us") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showContactDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("About") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showAboutDialog = true
                                    }
                                )
                            }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Hero Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                                    )
                                )
                             )
                    )
                    
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "IMAGE STUDIO",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FAST • SECURE • PRIVATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Image Tools Section
            item {
                Text(
                    text = "IMAGE TOOLS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Image To PDF & Image Resize
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Image to PDF",
                            desc = "Convert images into a PDF",
                            icon = Icons.Default.PictureAsPdf,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("IMAGE_TO_PDF")
                            }
                        )

                        HomeToolCard(
                            title = "Resize Image",
                            desc = "Change image dimensions",
                            icon = Icons.Default.AspectRatio,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("IMAGE_RESIZE")
                            }
                        )
                    }

                    // Row 2: Compress Image & Convert Image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Compress Image",
                            desc = "Reduce image file size",
                            icon = Icons.Default.Compress,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("IMAGE_COMPRESS")
                            }
                        )

                        HomeToolCard(
                            title = "Convert Image",
                            desc = "Change image file format",
                            icon = Icons.Default.Transform,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("IMAGE_CONVERT")
                            }
                        )
                    }

                    // Row 3: Crop Image, Rotate & Flip, and Batch Compressor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Crop Image",
                            desc = "Cut out image portions",
                            icon = Icons.Default.Crop,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("IMAGE_CROP")
                            }
                        )

                        HomeToolCard(
                            title = "Rotate & Flip",
                            desc = "Rotate or flip orientation",
                            icon = Icons.Default.RotateRight,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("IMAGE_ROTATE")
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Batch Compressor",
                            desc = "Compress multiple images at once",
                            icon = Icons.Default.PhotoSizeSelectActual,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("BATCH_COMPRESS")
                            }
                        )
                    }
                }
            }

            // PDF Tools Section
            item {
                Text(
                    text = "PDF TOOLS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        letterSpacing = 1.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: PDF to Image & Compress PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "PDF to Image",
                            desc = "Convert PDF pages to JPG/PNG",
                            icon = Icons.Default.Collections,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_TO_IMAGE")
                            }
                        )

                        HomeToolCard(
                            title = "Compress PDF",
                            desc = "Reduce PDF file size",
                            icon = Icons.Default.Compress,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_COMPRESS")
                            }
                        )
                    }

                    // Row 2: Merge PDF & Split PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Merge PDF",
                            desc = "Combine multiple PDFs into one",
                            icon = Icons.Default.MergeType,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_MERGE")
                            }
                        )

                        HomeToolCard(
                            title = "Split PDF",
                            desc = "Split or extract PDF ranges",
                            icon = Icons.Default.CallSplit,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_SPLIT")
                            }
                        )
                    }

                    // Row 3: Extract Pages & Rotate PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Extract Pages",
                            desc = "Extract precise pages to new PDF",
                            icon = Icons.Default.ContentCopy,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_EXTRACT")
                            }
                        )

                        HomeToolCard(
                            title = "Rotate PDF",
                            desc = "Rotate PDF page orientations",
                            icon = Icons.Default.RotateRight,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_ROTATE")
                            }
                        )
                    }

                    // Row 4: Reorder Pages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeToolCard(
                            title = "Reorder Pages",
                            desc = "Change PDF page positions",
                            icon = Icons.Default.Menu,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).clickable {
                                onNavigateToWizard("PDF_REORDER")
                            }
                        )
                    }
                }
            }

            // History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT FILES / HISTORY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // History List
            if (history.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "No files",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No history available",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Your processed files will show up here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(history, key = { it.id }) { item ->
                    HistoryItemRow(
                        item = item,
                        onDelete = { viewModel.deleteHistoryItem(item) },
                        onShare = {
                            try {
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(item.editedUri))
                                    type = if (item.format == "PDF") "application/pdf" else "image/*"
                                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share File"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onOpen = {
                            try {
                                val intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_VIEW
                                    setDataAndType(Uri.parse(item.editedUri), if (item.format == "PDF") "application/pdf" else "image/*")
                                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "No viewer app found!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }
    if (showContactDialog) {
        ContactUsDialog(onDismiss = { showContactDialog = false })
    }
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun HomeToolCard(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(115.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    item: HistoryEntity,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }
    
    val compressionSavings = remember(item.originalSize, item.editedSize) {
        if (item.originalSize > 0) {
            val ratio = (1.0f - (item.editedSize.toFloat() / item.originalSize.toFloat())) * 100f
            ratio.toInt().coerceIn(0, 99)
        } else {
            0
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.format,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = Formatter.formatFileSize(context, item.editedSize),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (item.originalSize > 0) {
                        Text(
                            text = "(${Formatter.formatFileSize(context, item.originalSize)})",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (compressionSavings > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "-$compressionSavings%",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
