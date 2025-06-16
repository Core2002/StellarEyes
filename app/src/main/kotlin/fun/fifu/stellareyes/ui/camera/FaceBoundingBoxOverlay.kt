package `fun`.fifu.stellareyes.ui.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.face.Face
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.geometry.Size as ComposeSize

private const val TAG = "FaceBoundingBoxOverlay"

@Composable
fun FaceBoundingBoxOverlay(
    faces: List<Face>,
    imageAnalysisWidth: Int,    // e.g., 480 (ML Kit's analysis width)
    imageAnalysisHeight: Int,   // e.g., 640 (ML Kit's analysis height)
    previewViewWidthPx: Float,  // e.g., 1080 (Canvas/PreviewView width)
    previewViewHeightPx: Float, // e.g., 2016 (Canvas/PreviewView height)
    lensFacing: Int,
    previewViewScaleType: PreviewView.ScaleType
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // ... (之前的空检查不变)

        // ML Kit 分析的图像的宽高比
        val mlKitImageAspectRatio =
            imageAnalysisWidth.toFloat() / imageAnalysisHeight.toFloat() // e.g., 480/640 = 0.75

        // PreviewView (Canvas) 的宽高比
        val canvasAspectRatio = previewViewWidthPx / previewViewHeightPx // e.g., 1080/2016 = 0.5357

        var scaleX: Float
        var scaleY: Float
        var canvasOffsetX = 0f // 缩放后的图像在 Canvas 上的左上角 X 偏移
        var canvasOffsetY = 0f // 缩放后的图像在 Canvas 上的左上角 Y 偏移

        if (previewViewScaleType == PreviewView.ScaleType.FIT_CENTER ||
            previewViewScaleType == PreviewView.ScaleType.FIT_START ||
            previewViewScaleType == PreviewView.ScaleType.FIT_END
        ) {

            // FIT_CENTER 逻辑:
            // 目标是将 imageAnalysisWidth x imageAnalysisHeight 的图像完整地放入 previewViewWidthPx x previewViewHeightPx 的区域内，
            // 保持宽高比，并居中。

            if (mlKitImageAspectRatio > canvasAspectRatio) {
                // ML Kit 图像比 Canvas 更“宽” (或说 Canvas 比 ML Kit 图像更“高瘦”)
                // 例如: ML Kit 16:9 (1.77), Canvas 9:16 (0.56)
                // 此时，图像的宽度应该填满 Canvas 的宽度，高度按比例缩放，垂直居中。
                scaleX = previewViewWidthPx / imageAnalysisWidth.toFloat()
                scaleY = scaleX // 保持宽高比
                val scaledImageActualHeight = imageAnalysisHeight.toFloat() * scaleY
                canvasOffsetY = (previewViewHeightPx - scaledImageActualHeight) / 2f
            } else {
                // ML Kit 图像比 Canvas 更“高” (或说 Canvas 比 ML Kit 图像更“矮胖”)
                // 例如: ML Kit 9:16 (0.56) (我们的情况: 480x640 AR=0.75)
                //        Canvas 9:18.5 (0.48) (我们的情况: 1080x2016 AR=0.5357)
                //        在您的例子中: mlKitImageAspectRatio (0.75) > canvasAspectRatio (0.5357)
                //        所以，上面的 if 会执行。让我们重新检查这个逻辑。

                // 重新思考 FIT_CENTER:
                // 我们要比较的是，如果按宽度缩放，高度是否会超出；如果按高度缩放，宽度是否会超出。
                // 选择那个使得图像 *恰好* 适应一个维度，而另一个维度不超出的缩放方式。

                val scaleToFitWidth = previewViewWidthPx / imageAnalysisWidth.toFloat()
                val heightIfScaledToFitWidth = imageAnalysisHeight.toFloat() * scaleToFitWidth

                val scaleToFitHeight = previewViewHeightPx / imageAnalysisHeight.toFloat()
                val widthIfScaledToFitHeight = imageAnalysisWidth.toFloat() * scaleToFitHeight

                if (heightIfScaledToFitWidth <= previewViewHeightPx) {
                    // 按宽度缩放图像后，其高度仍在 Canvas 内，这是可行的。
                    // 这意味着图像的宽高比使得它在宽度上是限制因素 (或者说，它相对 Canvas 更 "宽")
                    scaleX = scaleToFitWidth
                    scaleY = scaleX
                    canvasOffsetY = (previewViewHeightPx - heightIfScaledToFitWidth) / 2f
                } else {
                    // 按宽度缩放会导致高度超出，所以必须按高度缩放。
                    // 这意味着图像的宽高比使得它在高度上是限制因素 (或者说，它相对 Canvas 更 "高")
                    scaleY = scaleToFitHeight
                    scaleX = scaleY
                    canvasOffsetX = (previewViewWidthPx - widthIfScaledToFitHeight) / 2f
                }
            }
        } else { // FILL_CENTER (之前工作良好的逻辑)
            // ... (可以保留之前的 FILL_CENTER 计算逻辑)
            if (mlKitImageAspectRatio > canvasAspectRatio) { // Image is wider or less tall than the view
                scaleX = previewViewWidthPx / imageAnalysisWidth.toFloat()
                scaleY = scaleX
                canvasOffsetY =
                    (previewViewHeightPx - (imageAnalysisHeight.toFloat() * scaleY)) / 2f
            } else { // Image is taller or less wide than the view
                scaleY = previewViewHeightPx / imageAnalysisHeight.toFloat()
                scaleX = scaleY
                canvasOffsetX = (previewViewWidthPx - (imageAnalysisWidth.toFloat() * scaleX)) / 2f
            }
        }

        // 实际显示的相机图像在 Canvas 上的边界
        val displayedImageLeft = canvasOffsetX
        val displayedImageTop = canvasOffsetY
        val displayedImageWidth = imageAnalysisWidth.toFloat() * scaleX
        val displayedImageHeight = imageAnalysisHeight.toFloat() * scaleY
        val displayedImageRight = displayedImageLeft + displayedImageWidth
        val displayedImageBottom = displayedImageTop + displayedImageHeight

//        if (System.currentTimeMillis() % 2000 < 50) {
//            Log.d(
//                TAG,
//                "Overlay (ScaleType: $previewViewScaleType) -> MLKit Image: ${imageAnalysisWidth}x${imageAnalysisHeight} (AR: $mlKitImageAspectRatio)"
//            )
//            Log.d(
//                TAG,
//                "Overlay -> Canvas: ${previewViewWidthPx.toInt()}x${previewViewHeightPx.toInt()} (AR: $canvasAspectRatio)"
//            )
//            Log.d(
//                TAG,
//                "Overlay -> ScaleX: $scaleX, ScaleY: $scaleY, OffsetX: $canvasOffsetX, OffsetY: $canvasOffsetY"
//            )
//            Log.d(
//                TAG,
//                "Overlay -> Displayed Image Rect: LTRB[${displayedImageLeft.toInt()}, ${displayedImageTop.toInt()}, ${displayedImageRight.toInt()}, ${displayedImageBottom.toInt()}]"
//            )
//        }

        for (face in faces) {
            val boundingBox = face.boundingBox // 相对于 imageAnalysisWidth, imageAnalysisHeight

            // 1. 将 ML Kit 坐标缩放到 Canvas 上的图像尺寸
            var l = boundingBox.left.toFloat() * scaleX
            var t = boundingBox.top.toFloat() * scaleY
            var r = boundingBox.right.toFloat() * scaleX
            var b = boundingBox.bottom.toFloat() * scaleY

            // 2. 应用偏移，将坐标原点移到 Canvas 上显示的图像的左上角
            l += canvasOffsetX
            t += canvasOffsetY
            r += canvasOffsetX
            b += canvasOffsetY

            // 3. 裁剪到实际显示的相机图像边界 (这是关键，防止绘制到黑边)
            var cl = max(displayedImageLeft, l)
            var ct = max(displayedImageTop, t)
            var cr = min(displayedImageRight, r)
            var cb = min(displayedImageBottom, b)

            // 4. 前置摄像头镜像 (作用于裁剪后的坐标)
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                // 镜像轴应该是实际显示图像的中心轴
                val mirrorAxis = displayedImageLeft + displayedImageWidth / 2f
                val oldCl = cl
                cl = mirrorAxis + (mirrorAxis - cr) // cl = 2 * mirrorAxis - cr
                cr = mirrorAxis + (mirrorAxis - oldCl) // cr = 2 * mirrorAxis - oldCl

                // 再次裁剪以防镜像操作导致超出 (理论上如果镜像轴正确，不应大幅超出)
                cl = max(displayedImageLeft, cl)
                cr = min(displayedImageRight, cr)
            }

            // 5. 绘制
            if (cr > cl && cb > ct) {
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(cl, ct),
                    size = ComposeSize(cr - cl, cb - ct),
                    style = Stroke(width = 3f)
                )
            } else {
                if (System.currentTimeMillis() % 1000 < 50) {
                    Log.w(
                        TAG,
                        "Invalid/Clipped BBox: CLI[${cl.toInt()},${ct.toInt()},${cr.toInt()},${cb.toInt()}] RAW_OFFSET[${l.toInt()},${t.toInt()},${r.toInt()},${b.toInt()}] RAW_BOX[${boundingBox.left},${boundingBox.top},${boundingBox.right},${boundingBox.bottom}]"
                    )
                }
            }
        }
    }
}
