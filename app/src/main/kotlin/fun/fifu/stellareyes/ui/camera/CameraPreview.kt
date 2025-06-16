@file:kotlin.OptIn(ExperimentalCoroutinesApi::class)

package `fun`.fifu.stellareyes.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import `fun`.fifu.stellareyes.FaceNet
import `fun`.fifu.stellareyes.captureImageToBitmap
import `fun`.fifu.stellareyes.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import androidx.camera.core.Preview as CameraXPreview

private const val TAG = "CameraPreview"

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    executor: ExecutorService,
    faceDetector: FaceDetector,
    lensFacing: Int,
    scaleType: PreviewView.ScaleType,
    imageCapture: ImageCapture,
    onFacesDetected: (faces: List<Face>, width: Int, height: Int) -> Unit,
    viewModel: SettingsViewModel,
    onCaptureProcessed: (() -> Unit)? = null // 可选回调
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply { this.scaleType = scaleType }
    }

    // 初始化 FaceNet
    LaunchedEffect(Unit) {
        CoroutineScope(FaceNet.tfliteThread).launch {
            FaceNet.initFaceNet(context, false)
        }
    }

    val lastCaptureTime = remember { mutableLongStateOf(0L) }

    // CameraX 绑定
    LaunchedEffect(lensFacing, scaleType) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val cameraXPreview = CameraXPreview.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture.targetRotation = previewView.display.rotation


        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build().apply {
                setAnalyzer(executor) { imageProxy ->
                    processImageProxy(
                        imageProxy,
                        faceDetector,
                        onFacesDetected,
                        imageCapture,
                        context,
                        viewModel,
                        lastCaptureTime
                    )
                }
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                cameraXPreview,
                imageAnalyzer,
                imageCapture
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    AndroidView({ previewView }, modifier = modifier)
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    faceDetector: FaceDetector,
    onFacesDetected: (faces: List<Face>, width: Int, height: Int) -> Unit,
    imageCapture: ImageCapture,
    context: Context,
    viewModel: SettingsViewModel,
    lastCaptureTime: MutableState<Long>
) {
    val mediaImage = imageProxy.image ?: return imageProxy.close()

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

    val (width, height) = if (rotationDegrees == 90 || rotationDegrees == 270) {
        imageProxy.height to imageProxy.width
    } else {
        imageProxy.width to imageProxy.height
    }

    faceDetector.process(inputImage)
        .addOnSuccessListener { faces ->
            onFacesDetected(faces, width, height)

            if (viewModel.isContinuousInferenceEnabled.value){
                val now = System.currentTimeMillis()
                if (faces.isNotEmpty() && now - lastCaptureTime.value > 1000) {
                    lastCaptureTime.value = now

                    captureImageToBitmap(context, imageCapture) { capturedBitmap, error ->
                        if (capturedBitmap != null) {
                            val capturedImage = InputImage.fromBitmap(capturedBitmap, 0)
                            faceDetector.process(capturedImage)
                                .addOnSuccessListener { detectedFaces ->
                                    if (viewModel.isProcessAllFacesEnabled.value) {
                                        FaceRecognitionViewModel.processAllFaces(
                                            detectedFaces, capturedImage, context, viewModel
                                        )
                                    } else {
                                        FaceRecognitionViewModel.processLargestFace(
                                            detectedFaces, capturedImage, context, viewModel
                                        )
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Face detection failed: ${e.message}", e)
                                }
                        }
                    }
                }
            }
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Face detection failed", e)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

