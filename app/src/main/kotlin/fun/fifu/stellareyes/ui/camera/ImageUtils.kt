package `fun`.fifu.stellareyes.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import java.io.FileNotFoundException
import java.io.IOException

fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
    return try {
        context.assets.open(fileName).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun bitmapToBase64Url(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 90
): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(format, quality, outputStream)
    val byteArray = outputStream.toByteArray()
    val base64 = Base64.encode(byteArray)

    val mimeType = when (format) {
        Bitmap.CompressFormat.PNG -> "image/png"
        Bitmap.CompressFormat.JPEG -> "image/jpeg"
        Bitmap.CompressFormat.WEBP -> "image/webp"
        else -> "image/jpeg"
    }

    return "data:$mimeType;base64,$base64"
}

fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream).also {
            inputStream?.close()
        }
    } catch (e: FileNotFoundException) {
        Log.e("uriToBitmap", "File not found for URI: $uri", e)
        null
    } catch (e: IOException) {
        Log.e("uriToBitmap", "IOException when opening URI: $uri", e)
        null
    } catch (e: SecurityException) {
        Log.e("uriToBitmap", "SecurityException, permission denied for URI: $uri", e)
        null
    } catch (e: Exception) {
        Log.e("uriToBitmap", "Error decoding bitmap from URI: $uri", e)
        null
    }
}

fun base64UrlToBitmap(base64Url: String): Bitmap? {
    val base64Data = base64Url.substringAfter("base64,", missingDelimiterValue = "")
    if (base64Data.isEmpty()) return null

    return try {
        val decodedBytes = Base64.decode(base64Data)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        null
    }
}

fun imageToBitmap(image: Image?): Bitmap? {
    if (image == null) return null
    if (image.format != ImageFormat.YUV_420_888) {
        return null
    }

    val nv21 = yuv420ToNv21(image)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val jpegBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}

@OptIn(ExperimentalGetImage::class)
fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val image = imageProxy.image ?: return null

    val nv21 = yuv420ToNv21(image)
    val yuvImage = YuvImage(
        nv21,
        ImageFormat.NV21,
        image.width,
        image.height,
        null
    )

    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun yuv420ToNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 2

    val nv21 = ByteArray(ySize + uvSize)

    val yBuffer = image.planes[0].buffer // Y
    val uBuffer = image.planes[1].buffer // U
    val vBuffer = image.planes[2].buffer // V

    val rowStride = image.planes[0].rowStride
    assert(image.planes[0].pixelStride == 1)

    var pos = 0
    if (rowStride == width) {
        yBuffer.get(nv21, 0, ySize)
        pos += ySize
    } else {
        var yBufferPos = yBuffer.position()
        for (row in 0 until height) {
            yBuffer.position(yBufferPos)
            yBuffer.get(nv21, pos, width)
            yBufferPos += rowStride
            pos += width
        }
    }

    val uvRowStride = image.planes[1].rowStride
    val uvPixelStride = image.planes[1].pixelStride

    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {
            val uvIndex = row * uvRowStride + col * uvPixelStride
            nv21[pos++] = vBuffer.get(uvIndex)
            nv21[pos++] = uBuffer.get(uvIndex)
        }
    }

    return nv21
}


fun cropFaceFromBitmap(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
    val safeRect = Rect(
        boundingBox.left.coerceAtLeast(0),
        boundingBox.top.coerceAtLeast(0),
        boundingBox.right.coerceAtMost(bitmap.width),
        boundingBox.bottom.coerceAtMost(bitmap.height)
    )

    return try {
        Bitmap.createBitmap(
            bitmap,
            safeRect.left,
            safeRect.top,
            safeRect.width(),
            safeRect.height()
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun cropFaceFromBitmapSquare(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
    val centerX = boundingBox.centerX()
    val centerY = boundingBox.centerY()

    val width = boundingBox.width()
    val height = boundingBox.height()

    val side = maxOf(width, height)

    val halfSide = side / 2
    val left = (centerX - halfSide).coerceAtLeast(0)
    val top = (centerY - halfSide).coerceAtLeast(0)

    val right = (left + side).coerceAtMost(bitmap.width)
    val bottom = (top + side).coerceAtMost(bitmap.height)

    val finalWidth = right - left
    val finalHeight = bottom - top

    if (finalWidth <= 0 || finalHeight <= 0) return null

    return try {
        Bitmap.createBitmap(bitmap, left, top, finalWidth, finalHeight)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
    val matrix = android.graphics.Matrix()
    matrix.postRotate(rotationDegrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun toGrayscale(src: Bitmap): Bitmap {
    val width = src.width
    val height = src.height
    val grayBitmap = createBitmap(width, height)

    val canvas = Canvas(grayBitmap)

    val colorMatrix = ColorMatrix()
    colorMatrix.setSaturation(0f)

    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

    canvas.drawBitmap(src, 0f, 0f, paint)

    return grayBitmap
}

fun bitmapToFaceNetInput(bitmap: Bitmap): FloatArray {
    val width = 160
    val height = 160
    val input = FloatArray(width * height * 3)

    var index = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = bitmap[x, y]

            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            input[index++] = (r - 127.5f) / 128.0f
            input[index++] = (g - 127.5f) / 128.0f
            input[index++] = (b - 127.5f) / 128.0f
        }
    }

    return input
}
