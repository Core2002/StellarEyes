package `fun`.fifu.stellareyes

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.Executor


// Make sure TAG is defined in your class/file
private const val TAG = "ScreenShotUtil"

fun createBitmapFromView(view: View, window: android.view.Window?): Bitmap? {
    if (view.width <= 0 || view.height <= 0) {
        Log.e(TAG, "View has no dimensions (width or height is 0). Cannot create bitmap.")
        // Return a small transparent bitmap or throw an exception
        return null
    }

    val bitmap = createBitmap(view.width, view.height)
    val canvas = Canvas(bitmap)

    // If the view is not attached to a window, or if you want to capture the view
    // as it is currently laid out and drawn (including any transformations),
    // directly drawing the view to the canvas is preferred.
    if (view.isAttachedToWindow && window != null) {
        // More robust method, especially for complex views or when Hardware Acceleration is involved
        // This method requires a window argument to capture the view content correctly.
        // For Jetpack Compose, you might need to find the underlying Android View that hosts the Composable.
        // Or if you are capturing a Composable directly, different techniques would be needed.
        // PixelCopy is a more modern API for this but requires API 26+.
        // Use PixelCopy for API 26+ for more accurate captures
        val locationOfViewInWindow = IntArray(2)
        view.getLocationInWindow(locationOfViewInWindow)
        try {
            android.view.PixelCopy.request(
                window,
                android.graphics.Rect(
                    locationOfViewInWindow[0],
                    locationOfViewInWindow[1],
                    locationOfViewInWindow[0] + view.width,
                    locationOfViewInWindow[1] + view.height
                ),
                bitmap,
                { copyResult ->
                    if (copyResult == android.view.PixelCopy.SUCCESS) {
                        Log.d(TAG, "PixelCopy successful")
                    } else {
                        Log.e(TAG, "PixelCopy failed with error: $copyResult")
                        // Fallback to drawing cache or simple draw if PixelCopy fails
                        drawViewOnCanvas(view, canvas)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            // Note: PixelCopy is asynchronous. For simplicity in this example,
            // we are not handling the async nature directly for the return value.
            // In a real app, you'd use the callback to proceed with the bitmap.
            // For this synchronous function signature, the bitmap might not be updated yet.
            // If synchronous behavior is strictly needed and PixelCopy is used,
            // you'd need to block or use a different approach.
            // Given the original function's synchronous nature, falling back to draw for now.
            drawViewOnCanvas(view, canvas) // Fallback for simplicity here
        } catch (e: IllegalArgumentException) {
            // This can happen if the window is not valid, etc.
            Log.e(TAG, "PixelCopy request failed", e)
            drawViewOnCanvas(view, canvas) // Fallback
        }
    } else {
        // If the view is not attached or no window is provided,
        // use the simpler draw method. This might not capture everything
        // correctly for complex views (e.g., SurfaceViews, hardware overlays).
        drawViewOnCanvas(view, canvas)
    }
    return bitmap
}

private fun drawViewOnCanvas(view: View, canvas: Canvas) {
    val bgDrawable = view.background
    if (bgDrawable != null) {
        bgDrawable.draw(canvas)
    } else {
        canvas.drawColor(android.graphics.Color.WHITE) // Or any default background
    }
    view.draw(canvas)
}


private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format == ImageFormat.JPEG) {
        val buffer = image.planes[0].buffer
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        // Rotate bitmap if necessary
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    } else if (image.format == ImageFormat.YUV_420_888) {
        // YUV_420_888 to Bitmap conversion (more complex)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize) // Note: U and V order might differ (UV or VU)
        uBuffer.get(nv21, ySize + vSize, uSize)


        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    } else {
        Log.e(TAG, "Unsupported image format: ${image.format}")
        return null
    }
}

fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, displayName: String): Boolean {
    val imageCollection =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.IS_PENDING, 1)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/StellarEyes-Photos")
    }

    val contentResolver = context.contentResolver
    var uri = contentResolver.insert(imageCollection, contentValues)
    uri?.let {
        try {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    Log.e(TAG, "Failed to save bitmap.")
                    contentResolver.delete(uri!!, null, null) // Clean up if compress fails
                    uri = null // Indicate failure
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(it, contentValues, null, null)
            Log.d(TAG, "Bitmap saved to MediaStore: $uri")
            return uri != null
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to MediaStore", e)
            if (uri != null) {
                contentResolver.delete(uri, null, null) // Clean up on error
            }
            return false
        }
    }
    return false
}

@SuppressLint("RestrictedApi")
fun captureAndSaveImage(context: Context, imageCapture: ImageCapture) {
    // 获取 MediaStore 图片路径 URI
    val imageCollection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    // 生成文件的 ContentValues
    val contentValues = ContentValues().apply {
        val displayName = "photo_${System.currentTimeMillis()}"
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.IS_PENDING, 1)  // 设置为 IS_PENDING 状态
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/StellarEyes-Photos")
    }

    // 尝试插入新的文件到 MediaStore
    val contentUri: Uri? = context.contentResolver.insert(imageCollection, contentValues)
    if (contentUri == null) {
        Log.e(TAG, "Failed to insert image into MediaStore.")
        return
    }

    // 获取 OutputStream
    val outputStream: OutputStream? = context.contentResolver.openOutputStream(contentUri)
    if (outputStream == null) {
        Log.e(TAG, "Failed to create output stream.")
        return
    }

    Log.d(TAG, "OutputStream created successfully")

    // 捕捉图片并保存
    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(outputStream).build(),
        CameraXExecutors.mainThreadExecutor(),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(TAG, "Image saved callback triggered")

                // 获取保存的 URI
                val uri = output.savedUri ?: contentUri
                Log.d(TAG, "Image URI: $uri")

                // 更新 IS_PENDING 状态为 0，表示文件保存完成
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                try {
                    val rowsUpdated = context.contentResolver.update(uri, contentValues, null, null)
                    Log.d(TAG, "Rows updated: $rowsUpdated")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating MediaStore: ${e.message}")
                }

                // 强制扫描新文件，确保它出现在图库中
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(uri.toString()),
                    null
                ) { path, uri ->
                    Log.d(TAG, "Scanned $path: $uri")
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Image capture failed: ${exc.message}")
            }
        }
    )
}


/**
 * Captures an image using the provided ImageCapture use case and returns a Bitmap.
 * This function is asynchronous and invokes a callback with the resulting Bitmap or an error.
 *
 * @param context The application context.
 * @param imageCapture The ImageCapture use case instance.
 * @param callback A lambda function that will be called with the captured Bitmap
 *                 (or null if an error occurs or format is unsupported) and an optional Exception.
 */
@SuppressLint("RestrictedApi")
fun captureImageToBitmap(
    context: Context, // Context might be needed for executor
    imageCapture: ImageCapture,
    callback: (bitmap: Bitmap?, error: Exception?) -> Unit
) {
    // We need an executor to define where the callback runs.
    // Using ContextCompat.getMainExecutor(context) ensures the callback is on the main thread,
    // which is often useful for UI updates.
    val executor: Executor = ContextCompat.getMainExecutor(context)
    // 或者，如果你想使用 CameraXExecutors.mainThreadExecutor() 也可以，
    // 但通常 CameraX 的回调已经设计为在合适的线程上执行。
    // val executor = CameraXExecutors.mainThreadExecutor()


    imageCapture.takePicture(
        executor, // Executor for the callback
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                Log.d(TAG, "Image capture success. Format: ${image.format}")
                val bitmap = imageProxyToBitmap(image) // imageProxyToBitmap will close the image
                image.close()
                if (bitmap != null) {
                    callback(bitmap, null)
                } else {
                    Log.e(TAG, "Failed to convert ImageProxy to Bitmap.")
                    image.close() // imageProxyToBitmap should handle closing
                    callback(null, IllegalStateException("Failed to convert ImageProxy to Bitmap or unsupported format."))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                callback(null, exception)
            }
        }
    )
}