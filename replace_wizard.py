import re

path = 'app/src/main/java/com/example/ui/screens/WizardScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# -------------------------------------------------------------
# PATCH 1: IMAGE_COMPRESS SETTINGS BLOCK
# We will find from '"IMAGE_COMPRESS" -> {' to '"IMAGE_CONVERT" -> {'
# -------------------------------------------------------------
image_compress_settings = """"IMAGE_COMPRESS" -> {
                                         // ------------------------------------------------------------------
                                         // ORIGINAL IMAGE INFO DISPLAY
                                         // ------------------------------------------------------------------
                                         Card(
                                             modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                         ) {
                                             Column(modifier = Modifier.padding(12.dp)) {
                                                 Text("ORIGINAL IMAGE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                 Spacer(modifier = Modifier.height(6.dp))
                                                 originalMetadata?.let { meta ->
                                                     Text("Name: ${meta.fileName}", fontSize = 12.sp, maxLines = 1)
                                                     Text("Format: ${meta.format}", fontSize = 12.sp)
                                                     Text("Dimensions: ${meta.width} × ${meta.height} px", fontSize = 12.sp)
                                                     val sizeMb = meta.fileSize / (1024.0 * 1024.0)
                                                     val sizeStr = if (sizeMb >= 1.0) String.format("%.2f MB", sizeMb) else String.format("%.1f KB", meta.fileSize / 1024.0)
                                                     Text("File Size: $sizeStr", fontSize = 12.sp)
                                                 } ?: Text("No image selected", fontSize = 12.sp)
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(8.dp))

                                         // ------------------------------------------------------------------
                                         // A. RESIZE / IMAGE DIMENSIONS
                                         // ------------------------------------------------------------------
                                         Text("RESIZE / IMAGE DIMENSIONS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         
                                         // Option: Resize Image
                                         listOf(
                                             "ORIGINAL" to "Keep Original Dimensions",
                                             "PRESET" to "Preset Dimensions (%)",
                                             "CUSTOM" to "Custom Dimensions"
                                         ).forEach { (mode, label) ->
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 modifier = Modifier.fillMaxWidth().clickable { compressResizeMode = mode }
                                             ) {
                                                 RadioButton(selected = compressResizeMode == mode, onClick = { compressResizeMode = mode })
                                                 Text(label, fontSize = 14.sp)
                                             }
                                         }

                                         if (compressResizeMode == "PRESET") {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 listOf("25%", "50%", "75%", "100%", "Custom").forEach { pct ->
                                                     FilterChip(
                                                         selected = compressPresetPct == pct,
                                                         onClick = { compressPresetPct = pct },
                                                         label = { Text(pct) }
                                                     )
                                                 }
                                             }
                                             if (compressPresetPct == "Custom") {
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Text("Scale percentage: ${compressCustomPresetVal.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 Slider(
                                                     value = compressCustomPresetVal,
                                                     onValueChange = { compressCustomPresetVal = it },
                                                     valueRange = 10f..200f
                                                 )
                                             }
                                         } else if (compressResizeMode == "CUSTOM") {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(12.dp)
                                             ) {
                                                 OutlinedTextField(
                                                     value = compressWidthInput,
                                                     onValueChange = { newW ->
                                                         compressWidthInput = newW
                                                         if (compressMaintainAspectRatio && originalMetadata != null) {
                                                             val wVal = newW.toDoubleOrNull()
                                                             if (wVal != null && wVal > 0) {
                                                                 val aspect = originalMetadata!!.width.toDouble() / originalMetadata!!.height.toDouble()
                                                                 compressHeightInput = (wVal / aspect).toInt().toString()
                                                             }
                                                         }
                                                     },
                                                     label = { Text("Width (px)") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                                 OutlinedTextField(
                                                     value = compressHeightInput,
                                                     onValueChange = { newH ->
                                                         compressHeightInput = newH
                                                         if (compressMaintainAspectRatio && originalMetadata != null) {
                                                             val hVal = newH.toDoubleOrNull()
                                                             if (hVal != null && hVal > 0) {
                                                                 val aspect = originalMetadata!!.width.toDouble() / originalMetadata!!.height.toDouble()
                                                                 compressWidthInput = (hVal * aspect).toInt().toString()
                                                             }
                                                         }
                                                     },
                                                     label = { Text("Height (px)") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                             }

                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.SpaceBetween,
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Text("Maintain Aspect Ratio", fontSize = 13.sp)
                                                 Switch(checked = compressMaintainAspectRatio, onCheckedChange = { compressMaintainAspectRatio = it })
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(16.dp))

                                         // ------------------------------------------------------------------
                                         // B. IMAGE FORMAT
                                         // ------------------------------------------------------------------
                                         Text("OUTPUT FORMAT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                             listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                                                 FilterChip(
                                                     selected = compressFormat == fmt,
                                                     onClick = { compressFormat = fmt },
                                                     label = { Text(fmt) }
                                                 )
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(16.dp))

                                         // ------------------------------------------------------------------
                                         // C. IMAGE QUALITY
                                         // ------------------------------------------------------------------
                                         if (compressFormat != "PNG") {
                                             Text("IMAGE QUALITY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                             Spacer(modifier = Modifier.height(4.dp))
                                             Row(
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 listOf(
                                                     "LOW" to "Low (30%)",
                                                     "MEDIUM" to "Medium (60%)",
                                                     "HIGH" to "High (85%)",
                                                     "CUSTOM" to "Custom"
                                                 ).forEach { (mode, label) ->
                                                     FilterChip(
                                                         selected = compressQualityMode == mode,
                                                         onClick = {
                                                             compressQualityMode = mode
                                                             compressQualitySlider = when (mode) {
                                                                 "LOW" -> 30f
                                                                 "MEDIUM" -> 60f
                                                                 "HIGH" -> 85f
                                                                 else -> compressQualitySlider
                                                             }
                                                         },
                                                         label = { Text(label) }
                                                     )
                                                 }
                                             }
                                             
                                             if (compressQualityMode == "CUSTOM") {
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Text("Quality: ${compressQualitySlider.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 Slider(
                                                     value = compressQualitySlider,
                                                     onValueChange = { compressQualitySlider = it },
                                                     valueRange = 10f..100f
                                                 )
                                             }
                                             Text(
                                                 "Higher quality = better image quality but larger file size.",
                                                 fontSize = 11.sp,
                                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                                             )
                                             Spacer(modifier = Modifier.height(16.dp))
                                         }

                                         // ------------------------------------------------------------------
                                         // D. TARGET FILE SIZE
                                         // ------------------------------------------------------------------
                                         Text("TARGET FILE SIZE (OPTIONAL)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.SpaceBetween,
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Text("Enable Target File Size", fontSize = 13.sp)
                                             Switch(checked = compressTargetEnabled, onCheckedChange = { compressTargetEnabled = it })
                                         }
                                         if (compressTargetEnabled) {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 OutlinedTextField(
                                                     value = compressTargetInput,
                                                     onValueChange = { compressTargetInput = it },
                                                     label = { Text("Target Size") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                                 Row(modifier = Modifier.weight(1f)) {
                                                     listOf("KB", "MB").forEach { unit ->
                                                         FilterChip(
                                                             selected = compressTargetUnit == unit,
                                                             onClick = { compressTargetUnit = unit },
                                                             label = { Text(unit) },
                                                             modifier = Modifier.padding(horizontal = 4.dp)
                                                         )
                                                     }
                                                 }
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(16.dp))

                                         // ------------------------------------------------------------------
                                         // E. ESTIMATED RESULT
                                         // ------------------------------------------------------------------
                                         Card(
                                             modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                             border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                         ) {
                                             Column(modifier = Modifier.padding(12.dp)) {
                                                 Text("ESTIMATED RESULT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                 Spacer(modifier = Modifier.height(6.dp))
                                                 originalMetadata?.let { meta ->
                                                     val finalW = when (compressResizeMode) {
                                                         "ORIGINAL" -> meta.width
                                                         "PRESET" -> {
                                                             val factor = when (compressPresetPct) {
                                                                 "25%" -> 0.25f
                                                                 "50%" -> 0.50f
                                                                 "75%" -> 0.75f
                                                                 "100%" -> 1.00f
                                                                 else -> compressCustomPresetVal / 100f
                                                             }
                                                             (meta.width * factor).toInt().coerceAtLeast(1)
                                                         }
                                                         else -> compressWidthInput.toIntOrNull() ?: meta.width
                                                     }
                                                     val finalH = when (compressResizeMode) {
                                                         "ORIGINAL" -> meta.height
                                                         "PRESET" -> {
                                                             val factor = when (compressPresetPct) {
                                                                 "25%" -> 0.25f
                                                                 "50%" -> 0.50f
                                                                 "75%" -> 0.75f
                                                                 "100%" -> 1.00f
                                                                 else -> compressCustomPresetVal / 100f
                                                             }
                                                             (meta.height * factor).toInt().coerceAtLeast(1)
                                                         }
                                                         else -> compressHeightInput.toIntOrNull() ?: meta.height
                                                     }
                                                     Text("Est. Dimensions: $finalW × $finalH px", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                     Text("Est. Format: $compressFormat", fontSize = 12.sp, fontWeight = FontWeight.Medium)

                                                     val sizeFactor = (finalW.toDouble() * finalH.toDouble()) / (meta.width.toDouble() * meta.height.toDouble())
                                                     val qVal = if (compressFormat == "PNG") 100f else {
                                                         when (compressQualityMode) {
                                                             "LOW" -> 30f
                                                             "MEDIUM" -> 60f
                                                             "HIGH" -> 85f
                                                             else -> compressQualitySlider
                                                         }
                                                     }
                                                     val qFactor = qVal / 90.0
                                                     val formatMultiplier = when (compressFormat) {
                                                         "PNG" -> 1.5
                                                         "WEBP" -> 0.7
                                                         else -> 1.0
                                                     }
                                                     val estSizeInBytes = (meta.fileSize * sizeFactor * qFactor * formatMultiplier).toLong().coerceIn(1024, meta.fileSize)
                                                     val estSizeMb = estSizeInBytes / (1024.0 * 1024.0)
                                                     val estSizeStr = if (estSizeMb >= 1.0) String.format("%.2f MB", estSizeMb) else String.format("%.1f KB", estSizeInBytes / 1024.0)
                                                     
                                                     if (compressTargetEnabled && compressTargetInput.isNotEmpty()) {
                                                         Text("Est. File Size: Max Target (~$compressTargetInput $compressTargetUnit)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                     } else {
                                                         Text("Est. File Size: ~$estSizeStr", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                     }
                                                 } ?: Text("Calculating estimation...", fontSize = 12.sp)
                                             }
                                         }
                                     }

                                     """

# Find bounds of IMAGE_COMPRESS block in settings
start_set = content.find('"IMAGE_COMPRESS" -> {')
end_set = content.find('"IMAGE_CONVERT" -> {')

if start_set != -1 and end_set != -1:
    content = content[:start_set] + image_compress_settings + content[end_set:]
    print("Patched IMAGE_COMPRESS settings block successfully!")
else:
    print("Could not find IMAGE_COMPRESS settings bounds!")

# -------------------------------------------------------------
# PATCH 2: BATCH_COMPRESS SETTINGS BLOCK
# We will find from '"BATCH_COMPRESS" -> {' to '"PDF_TO_IMAGE" -> {'
# -------------------------------------------------------------
batch_compress_settings = """"BATCH_COMPRESS" -> {
                                         // ------------------------------------------------------------------
                                         // BATCH IMAGES OVERVIEW DISPLAY
                                         // ------------------------------------------------------------------
                                         Card(
                                             modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                         ) {
                                             Column(modifier = Modifier.padding(12.dp)) {
                                                 Text("BATCH COMPRESSION OVERVIEW", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                 Spacer(modifier = Modifier.height(6.dp))
                                                 Text("Total Images Selected: ${selectedUris.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                 Text("These common settings will be applied to all selected images.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(8.dp))

                                         // ------------------------------------------------------------------
                                         // A. RESIZE / IMAGE DIMENSIONS
                                         // ------------------------------------------------------------------
                                         Text("RESIZE / IMAGE DIMENSIONS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         
                                         listOf(
                                             "ORIGINAL" to "Keep Original Dimensions",
                                             "PRESET" to "Preset Dimensions (%)",
                                             "CUSTOM" to "Custom Dimensions"
                                         ).forEach { (mode, label) ->
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 modifier = Modifier.fillMaxWidth().clickable { compressResizeMode = mode }
                                             ) {
                                                 RadioButton(selected = compressResizeMode == mode, onClick = { compressResizeMode = mode })
                                                 Text(label, fontSize = 14.sp)
                                             }
                                         }

                                         if (compressResizeMode == "PRESET") {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 listOf("25%", "50%", "75%", "100%", "Custom").forEach { pct ->
                                                     FilterChip(
                                                         selected = compressPresetPct == pct,
                                                         onClick = { compressPresetPct = pct },
                                                         label = { Text(pct) }
                                                     )
                                                 }
                                             }
                                             if (compressPresetPct == "Custom") {
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Text("Scale percentage: ${compressCustomPresetVal.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 Slider(
                                                     value = compressCustomPresetVal,
                                                     onValueChange = { compressCustomPresetVal = it },
                                                     valueRange = 10f..200f
                                                 )
                                             }
                                         } else if (compressResizeMode == "CUSTOM") {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(12.dp)
                                             ) {
                                                 OutlinedTextField(
                                                     value = compressWidthInput,
                                                     onValueChange = { compressWidthInput = it },
                                                     label = { Text("Width (px)") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                                 OutlinedTextField(
                                                     value = compressHeightInput,
                                                     onValueChange = { compressHeightInput = it },
                                                     label = { Text("Height (px)") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                             }

                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.SpaceBetween,
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Text("Maintain Aspect Ratio (based on each image's ratio)", fontSize = 13.sp)
                                                 Switch(checked = compressMaintainAspectRatio, onCheckedChange = { compressMaintainAspectRatio = it })
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(16.dp))

                                         // ------------------------------------------------------------------
                                         // B. IMAGE FORMAT
                                         // ------------------------------------------------------------------
                                         Text("OUTPUT FORMAT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                             listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                                                 FilterChip(
                                                     selected = compressFormat == fmt,
                                                     onClick = { compressFormat = fmt },
                                                     label = { Text(fmt) }
                                                 )
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(16.dp))

                                         // ------------------------------------------------------------------
                                         // C. IMAGE QUALITY
                                         // ------------------------------------------------------------------
                                         if (compressFormat != "PNG") {
                                             Text("IMAGE QUALITY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                             Spacer(modifier = Modifier.height(4.dp))
                                             Row(
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 listOf(
                                                     "LOW" to "Low (30%)",
                                                     "MEDIUM" to "Medium (60%)",
                                                     "HIGH" to "High (85%)",
                                                     "CUSTOM" to "Custom"
                                                 ).forEach { (mode, label) ->
                                                     FilterChip(
                                                         selected = compressQualityMode == mode,
                                                         onClick = {
                                                             compressQualityMode = mode
                                                             compressQualitySlider = when (mode) {
                                                                 "LOW" -> 30f
                                                                 "MEDIUM" -> 60f
                                                                 "HIGH" -> 85f
                                                                 else -> compressQualitySlider
                                                             }
                                                         },
                                                         label = { Text(label) }
                                                     )
                                                 }
                                             }
                                             
                                             if (compressQualityMode == "CUSTOM") {
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Text("Quality: ${compressQualitySlider.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 Slider(
                                                     value = compressQualitySlider,
                                                     onValueChange = { compressQualitySlider = it },
                                                     valueRange = 10f..100f
                                                 )
                                             }
                                             Spacer(modifier = Modifier.height(16.dp))
                                         }

                                         // ------------------------------------------------------------------
                                         // D. TARGET FILE SIZE
                                         // ------------------------------------------------------------------
                                         Text("TARGET FILE SIZE (OPTIONAL)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.SpaceBetween,
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Text("Enable Target File Size per image", fontSize = 13.sp)
                                             Switch(checked = compressTargetEnabled, onCheckedChange = { compressTargetEnabled = it })
                                         }
                                         if (compressTargetEnabled) {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 OutlinedTextField(
                                                     value = compressTargetInput,
                                                     onValueChange = { compressTargetInput = it },
                                                     label = { Text("Target Size") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                                 Row(modifier = Modifier.weight(1f)) {
                                                     listOf("KB", "MB").forEach { unit ->
                                                         FilterChip(
                                                             selected = compressTargetUnit == unit,
                                                             onClick = { compressTargetUnit = unit },
                                                             label = { Text(unit) },
                                                             modifier = Modifier.padding(horizontal = 4.dp)
                                                         )
                                                     }
                                                 }
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(16.dp))

                                         // ------------------------------------------------------------------
                                         // E. BEFORE PROCESSING SUMMARY
                                         // ------------------------------------------------------------------
                                         Card(
                                             modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                                             border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                                         ) {
                                             Column(modifier = Modifier.padding(16.dp)) {
                                                 Text("BEFORE PROCESSING SUMMARY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Text("Selected Images: ${selectedUris.size}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 
                                                 var totalBytes = 0L
                                                 selectedUris.forEach { u ->
                                                     try {
                                                         context.contentResolver.openFileDescriptor(u, "r")?.use { fd ->
                                                             totalBytes += fd.statSize
                                                         }
                                                     } catch (e: Exception) {}
                                                 }
                                                 val totalMb = totalBytes / (1024.0 * 1024.0)
                                                 val sizeStr = if (totalMb >= 1.0) String.format("%.2f MB", totalMb) else String.format("%.1f KB", totalBytes / 1024.0)
                                                 Text("Original Total Size: $sizeStr", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 Text("Output Format: $compressFormat", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 
                                                 val resizeStr = when (compressResizeMode) {
                                                     "ORIGINAL" -> "Keep Original Dimensions"
                                                     "PRESET" -> "Resize to $compressPresetPct"
                                                     else -> "${compressWidthInput.ifEmpty { "Auto" }} × ${compressHeightInput.ifEmpty { "Auto" }}"
                                                 }
                                                 Text("Resize: $resizeStr", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 
                                                 val qVal = if (compressFormat == "PNG") "N/A (Lossless)" else {
                                                     when (compressQualityMode) {
                                                         "LOW" -> "30%"
                                                         "MEDIUM" -> "60%"
                                                         "HIGH" -> "85%"
                                                         else -> "${compressQualitySlider.toInt()}%"
                                                     }
                                                 }
                                                 Text("Quality: $qVal", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                 
                                                 val targetStr = if (compressTargetEnabled && compressTargetInput.isNotEmpty()) "$compressTargetInput $compressTargetUnit per image" else "No target"
                                                 Text("Target Size: $targetStr", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                             }
                                         }
                                     }

                                     """

start_batch_set = content.find('"BATCH_COMPRESS" -> {')
end_batch_set = content.find('"PDF_TO_IMAGE" -> {')

if start_batch_set != -1 and end_batch_set != -1:
    content = content[:start_batch_set] + batch_compress_settings + content[end_batch_set:]
    print("Patched BATCH_COMPRESS settings block successfully!")
else:
    print("Could not find BATCH_COMPRESS settings bounds!")

# -------------------------------------------------------------
# PATCH 3: PDF_COMPRESS SETTINGS BLOCK
# We will find from '"PDF_COMPRESS" -> {' to the next case (which is '"PDF_MERGE" -> {')
# -------------------------------------------------------------
pdf_compress_settings = """"PDF_COMPRESS" -> {
                                         // Option list: Max, Balanced, High, Custom Target Size
                                         listOf(
                                             Triple("MAX", "Maximum Compression", "Smallest possible file size, low resolution images."),
                                             Triple("BALANCED", "Balanced Compression", "Optimal size reduction with great screen clarity."),
                                             Triple("HIGH", "High Quality", "Preserves details with lightweight text formatting."),
                                             Triple("CUSTOM", "Custom Target Size", "Compress to fit a specific maximum target size.")
                                         ).forEach { (mode, title, desc) ->
                                             Card(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .padding(vertical = 4.dp),
                                                 onClick = { pdfCompressMode = mode },
                                                 colors = CardDefaults.cardColors(
                                                     containerColor = if (pdfCompressMode == mode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                                 )
                                             ) {
                                                 Row(
                                                     modifier = Modifier.padding(12.dp),
                                                     verticalAlignment = Alignment.CenterVertically,
                                                     horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                 ) {
                                                     RadioButton(selected = pdfCompressMode == mode, onClick = { pdfCompressMode = mode })
                                                     Column {
                                                         Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                         Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                     }
                                                 }
                                             }
                                         }

                                         if (pdfCompressMode == "CUSTOM") {
                                             Spacer(modifier = Modifier.height(12.dp))
                                             Text("Target File Size", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                             Spacer(modifier = Modifier.height(6.dp))
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 OutlinedTextField(
                                                     value = pdfTargetInput,
                                                     onValueChange = { pdfTargetInput = it },
                                                     label = { Text("Target Size") },
                                                     modifier = Modifier.weight(1f),
                                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                     singleLine = true
                                                 )
                                                 Row(modifier = Modifier.weight(1f)) {
                                                     listOf("KB", "MB").forEach { unit ->
                                                         FilterChip(
                                                             selected = pdfTargetUnit == unit,
                                                             onClick = { pdfTargetUnit = unit },
                                                             label = { Text(unit) },
                                                             modifier = Modifier.padding(horizontal = 4.dp)
                                                         )
                                                     }
                                                 }
                                             }

                                             // Validation message
                                             selectedUri?.let { uri ->
                                                 val originalSize = try {
                                                     context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                                                 } catch (e: Exception) { 0L }
                                                 
                                                 val targetVal = pdfTargetInput.toDoubleOrNull() ?: 0.0
                                                 val multiplier = if (pdfTargetUnit == "MB") 1024.0 * 1024.0 else 1024.0
                                                 val targetBytes = (targetVal * multiplier).toLong()
                                                 
                                                 if (targetBytes > 0 && targetBytes >= originalSize) {
                                                     Spacer(modifier = Modifier.height(8.dp))
                                                     Text(
                                                         text = "The selected target size is not smaller than the original PDF. Please choose a smaller target size.",
                                                         color = MaterialTheme.colorScheme.error,
                                                         fontSize = 12.sp,
                                                         fontWeight = FontWeight.Medium
                                                     )
                                                 }
                                             }
                                         }
                                     }

                                     """

start_pdf_set = content.find('"PDF_COMPRESS" -> {')
end_pdf_set = content.find('"PDF_MERGE" -> {')

if start_pdf_set != -1 and end_pdf_set != -1:
    content = content[:start_pdf_set] + pdf_compress_settings + content[end_pdf_set:]
    print("Patched PDF_COMPRESS settings block successfully!")
else:
    print("Could not find PDF_COMPRESS settings bounds!")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
