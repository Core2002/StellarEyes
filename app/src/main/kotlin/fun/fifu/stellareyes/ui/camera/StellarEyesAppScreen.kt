@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package `fun`.fifu.stellareyes.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Portrait
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dev.shreyaspatil.capturable.capturable
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import `fun`.fifu.stellareyes.captureAndSaveImage
import `fun`.fifu.stellareyes.captureImageToBitmap
import `fun`.fifu.stellareyes.saveBitmapToMediaStore
import `fun`.fifu.stellareyes.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "StellarEyesAppScreen"

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("ViewModelConstructorInComposable")
@Composable
fun StellarEyesAppScreen(navController: NavHostController, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val captureController = rememberCaptureController()
    val scope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    var detectedFaces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var imageAnalysisConfiguredSize by remember { mutableStateOf(Size(0, 0)) }
    var previewViewSizePx by remember { mutableStateOf(IntSize.Zero) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // Define the desired scale type for the PreviewView
    val previewScaleType = PreviewView.ScaleType.FIT_CENTER

    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }
    DisposableEffect(faceDetector) {
        onDispose {
            faceDetector.close()
            Log.d(TAG, "FaceDetector closed")
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(ImageCapture.FLASH_MODE_OFF) // Or FLASH_MODE_ON, FLASH_MODE_OFF
            .build()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showImportProgress by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0f) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            Log.d(TAG, "Image picker result received. URI count: ${uris.size}")
            if (uris.isNotEmpty()) {
                Log.d(TAG, "URIs are not empty, launching import job.")
                scope.launch {
                    Log.d(TAG, "Coroutine for import started.") // 新增日志
                    showImportProgress = true
                    importProgress = 0f
                    try {
                        importFacesFromUris(uris, context, faceDetector) { progress ->
                            importProgress = progress
                        }
                        Log.d(TAG, "importFacesFromUris completed.") // 新增日志
                        snackbarHostState.showSnackbar("${uris.size} 张图片已选择，导入完成。")
                        Log.d(TAG, "Import job launched and Snackbar shown for non-empty URIs.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during import process in coroutine", e) // 捕获并记录异常
                        snackbarHostState.showSnackbar("导入失败: ${e.localizedMessage}")
                    } finally {
                        showImportProgress = false
                    }
                }
            } else {
                Log.d(TAG, "URIs are empty.")
                scope.launch {
                    snackbarHostState.showSnackbar("没有选择图片。")
                    Log.d(TAG, "Snackbar shown for empty URIs.")
                }
            }
        }
    )

    Scaffold(
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(52.dp) // 在这里设置您想要的高度，例如 52.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(), // 让 Row 填充整个宽度
                    horizontalArrangement = Arrangement.SpaceAround, // 或 Arrangement.Center，根据您的偏好选择
                    verticalAlignment = Alignment.CenterVertically // 确保图标在垂直方向上也居中
                ) {
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.FlipCameraAndroid,
                            contentDescription = "Flip Camera"
                        )
                    }
                    IconButton(onClick = {
                        captureAndSaveImage(context, imageCapture)
                        scope.launch {
                            val bitmapAsync = captureController.captureAsync()
                            try {
                                val capturedFaceBitmap = bitmapAsync.await().asAndroidBitmap()
                                saveBitmapToMediaStore(context, capturedFaceBitmap, "awa")
                                // Do something with `bitmap`.
                            } catch (error: Throwable) {
                                error.printStackTrace()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Take Photo"
                        )
                    }
                    IconButton(onClick = {
                        navController.navigate("settings_screen")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                    IconButton(onClick = {
                        navController.navigate("manage_faces_screen_route")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ManageSearch,
                            contentDescription = "管理人脸数据"
                        )
                    }
                    IconButton(onClick = {
                        captureImageToBitmap(context, imageCapture) { capturedFaceBitmap, error ->
                            if (capturedFaceBitmap == null) return@captureImageToBitmap
                            val inputImage = InputImage.fromBitmap(capturedFaceBitmap, 0)
                            faceDetector.process(inputImage)
                                .addOnSuccessListener { faces ->
                                    if (viewModel.isProcessAllFacesEnabled.value) {
                                        FaceRecognitionViewModel.processAllFaces(
                                            faces,
                                            inputImage,
                                            context,
                                            viewModel
                                        )
                                    } else {
                                        FaceRecognitionViewModel.processLargestFace(
                                            faces,
                                            inputImage,
                                            context,
                                            viewModel
                                        )
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Face detection failed: ${e.message}", e)
                                }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Portrait,
                            contentDescription = "识别"
                        )
                    }
                    IconButton(onClick = {
                        imagePickerLauncher.launch("image/*")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "从相册导入"
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .capturable(captureController)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CameraPreview(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        previewViewSizePx = size
                        // Log.d(TAG, "PreviewView Composable size updated: ${size.width}x${size.height}") // Keep for debugging if needed
                    },
                executor = cameraExecutor,
                faceDetector = faceDetector,
                lensFacing = lensFacing,
                scaleType = previewScaleType, // Pass the scale type to CameraPreview
                imageCapture = imageCapture,
                onFacesDetected = { faces, analysisWidth, analysisHeight ->
                    detectedFaces = faces
                    imageAnalysisConfiguredSize = Size(analysisWidth, analysisHeight)
                    // Log.d(TAG, "FaceBoundingBoxOverlay IS USING Analysis Size: ${analysisWidth}x${analysisHeight}") // Keep for debugging
                },
                viewModel = viewModel
            )

            if (detectedFaces.isNotEmpty() && imageAnalysisConfiguredSize.width > 0 && imageAnalysisConfiguredSize.height > 0 && previewViewSizePx.width > 0 && previewViewSizePx.height > 0) {
                FaceBoundingBoxOverlay(
                    faces = detectedFaces,
                    imageAnalysisWidth = imageAnalysisConfiguredSize.width,
                    imageAnalysisHeight = imageAnalysisConfiguredSize.height,
                    previewViewWidthPx = previewViewSizePx.width.toFloat(),
                    previewViewHeightPx = previewViewSizePx.height.toFloat(),
                    lensFacing = lensFacing,
                    previewViewScaleType = previewScaleType
                )
            }
            if (showImportProgress) {
                LinearProgressIndicator(
                    progress = { importProgress }, // Ensure this is a lambda returning the state value
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
            FaceRecognitionScreen(viewModel = FaceRecognitionViewModel, context = context)
        }
    }
}

// Assume this function is in the same file or imported correctly
fun importFacesFromUris(
    uris: List<Uri>,
    context: Context,
    faceDetector: FaceDetector,
    onProgress: (Float) -> Unit // Callback to update progress
) {
    Log.d(TAG, "importFacesFromUris started with ${uris.size} URIs.")
    uris.forEachIndexed { index, uri ->
        Log.d(TAG, "Processing URI ${index + 1}: $uri")
        val uriBitmap = uriToBitmap(context, uri) // Assume uriToBitmap is defined elsewhere
        if (uriBitmap == null) {
            Log.w(TAG, "Failed to convert URI to Bitmap: $uri")
            // Update progress even if an item fails, or decide how to handle partial failures
            onProgress((index + 1).toFloat() / uris.size)
            return@forEachIndexed // Continue with the next URI
        }
        Log.d(TAG, "Bitmap created successfully for URI: $uri")
        val inputImage = InputImage.fromBitmap(uriBitmap, 0)

        Log.i(TAG, "读取到 批量输入 - Calling faceDetector.process for URI: $uri")
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                Log.i(TAG, "Face detection success for URI: $uri. Found ${faces.size} faces.")
                FaceRecognitionViewModel.processPicFace( // Assume FaceRecognitionViewModel is accessible
                    faces,
                    inputImage,
                    context
                )
                Log.i(TAG, "processPicFace called for URI: $uri")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed for URI: $uri. Error: ${e.message}", e)
            }
            .addOnCompleteListener { // This will be called after success or failure
                Log.d(
                    TAG,
                    "Face detection task completed for URI: $uri. Success: ${it.isSuccessful}"
                )
                // Update progress after each item is processed
                onProgress((index + 1).toFloat() / uris.size)
            }
    }
    Log.d(TAG, "importFacesFromUris finished.")
}
