package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun CropCanvas(
    bitmap: Bitmap,
    cropRect: RectF, // Normalized (0f..1f)
    onCropRectChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.9f))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        
                        // Scale drag amount to normalized coordinates
                        val normDx = dragAmount.x / size.width
                        val normDy = dragAmount.y / size.height
                        
                        // Simple logic: determine if dragging near center or edges.
                        // To make it super robust and easy to use without complex tap-target math:
                        // drag near edges resizes them, drag in center moves the whole window!
                        val touchX = change.position.x / size.width
                        val touchY = change.position.y / size.height
                        
                        val newRect = RectF(cropRect)
                        
                        val centerX = (cropRect.left + cropRect.right) / 2f
                        val centerY = (cropRect.top + cropRect.bottom) / 2f
                        val threshold = 0.15f
                        
                        val nearLeft = Math.abs(touchX - cropRect.left) < threshold
                        val nearRight = Math.abs(touchX - cropRect.right) < threshold
                        val nearTop = Math.abs(touchY - cropRect.top) < threshold
                        val nearBottom = Math.abs(touchY - cropRect.bottom) < threshold
                        
                        if (!nearLeft && !nearRight && !nearTop && !nearBottom) {
                            // Drag center -> move whole box
                            val width = cropRect.width()
                            val height = cropRect.height()
                            
                            newRect.left = (cropRect.left + normDx).coerceIn(0f, 1f - width)
                            newRect.right = newRect.left + width
                            newRect.top = (cropRect.top + normDy).coerceIn(0f, 1f - height)
                            newRect.bottom = newRect.top + height
                        } else {
                            // Drag edges -> resize box
                            if (nearLeft) {
                                newRect.left = (cropRect.left + normDx).coerceIn(0f, cropRect.right - 0.1f)
                            }
                            if (nearRight) {
                                newRect.right = (cropRect.right + normDx).coerceIn(cropRect.left + 0.1f, 1f)
                            }
                            if (nearTop) {
                                newRect.top = (cropRect.top + normDy).coerceIn(0f, cropRect.bottom - 0.1f)
                            }
                            if (nearBottom) {
                                newRect.bottom = (cropRect.bottom + normDy).coerceIn(cropRect.top + 0.1f, 1f)
                            }
                        }
                        
                        onCropRectChanged(newRect)
                    }
                }
        ) {
            // 1. Draw the image to fit canvas bounds
            val canvasW = size.width
            val canvasH = size.height
            
            val imgW = imageBitmap.width.toFloat()
            val imgH = imageBitmap.height.toFloat()
            
            val scale = Math.min(canvasW / imgW, canvasH / imgH)
            val destW = imgW * scale
            val destH = imgH * scale
            val offsetX = (canvasW - destW) / 2f
            val offsetY = (canvasH - destH) / 2f
            
            // Draw image centered
            drawImage(
                image = imageBitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(destW.toInt(), destH.toInt())
            )
            
            // 2. Map normalized crop rect to canvas drawing coordinates
            val rectLeft = offsetX + cropRect.left * destW
            val rectTop = offsetY + cropRect.top * destH
            val rectRight = offsetX + cropRect.right * destW
            val rectBottom = offsetY + cropRect.bottom * destH
            
            val rectW = rectRight - rectLeft
            val rectH = rectBottom - rectTop
            
            // 3. Draw translucent overlay around the crop area
            // Left block
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, 0f),
                size = Size(rectLeft, canvasH)
            )
            // Right block
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(rectRight, 0f),
                size = Size(canvasW - rectRight, canvasH)
            )
            // Top block (inside horizontal crop bounds)
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(rectLeft, 0f),
                size = Size(rectW, rectTop)
            )
            // Bottom block (inside horizontal crop bounds)
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(rectLeft, rectBottom),
                size = Size(rectW, canvasH - rectBottom)
            )
            
            // 4. Draw crop window borders
            drawRect(
                color = Color.White,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectW, rectH),
                style = Stroke(width = 2.dp.toPx())
            )
            
            // 5. Draw decorative corner handles
            val handleLen = 20.dp.toPx()
            val handleThick = 4.dp.toPx()
            
            // Top-Left corner
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectLeft - handleThick/2, rectTop - handleThick/2),
                size = Size(handleLen, handleThick)
            )
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectLeft - handleThick/2, rectTop - handleThick/2),
                size = Size(handleThick, handleLen)
            )
            
            // Top-Right corner
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectRight - handleLen + handleThick/2, rectTop - handleThick/2),
                size = Size(handleLen, handleThick)
            )
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectRight - handleThick/2, rectTop - handleThick/2),
                size = Size(handleThick, handleLen)
            )
            
            // Bottom-Left corner
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectLeft - handleThick/2, rectBottom - handleThick/2),
                size = Size(handleLen, handleThick)
            )
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectLeft - handleThick/2, rectBottom - handleLen + handleThick/2),
                size = Size(handleThick, handleLen)
            )
            
            // Bottom-Right corner
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectRight - handleLen + handleThick/2, rectBottom - handleThick/2),
                size = Size(handleLen, handleThick)
            )
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(rectRight - handleThick/2, rectBottom - handleLen + handleThick/2),
                size = Size(handleThick, handleLen)
            )
        }
    }
}
