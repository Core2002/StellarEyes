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
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.FlipCameraAndroid
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.tasks.await
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
    
    // Scoped ViewModel
    val faceRecognitionViewModel: FaceRecognitionViewModel = viewModel()

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    var detectedFaces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var imageAnalysisConfiguredSize by remember { mutableStateOf(Size(0, 0)) }
    var previewViewSizePx by remember { mutableStateOf(IntSize.Zero) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

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
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showImportProgress by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    showImportProgress = true
                    importProgress = 0f
                    try {
                        importFacesFromUris(uris, context, faceDetector, faceRecognitionViewModel) { progress ->
                            importProgress = progress
                        }
                        snackbarHostState.showSnackbar("${uris.size} 张图片已选择，导入完成。")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during import process", e)
                        snackbarHostState.showSnackbar("导入失败: ${e.localizedMessage}")
                    } finally {
                        showImportProgress = false
                    }
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("没有选择图片。")
                }
            }
        }
    )

    Scaffold(
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
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
                            imageVector = Icons.AutoMirrored.Filled.ManageSearch,
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
                                        faceRecognitionViewModel.processAllFaces(
                                            faces,
                                            inputImage,
                                            context,
                                            viewModel
                                        )
                                    } else {
                                        faceRecognitionViewModel.processLargestFace(
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
                    },
                executor = cameraExecutor,
                faceDetector = faceDetector,
                lensFacing = lensFacing,
                scaleType = previewScaleType,
                imageCapture = imageCapture,
                onFacesDetected = { faces, analysisWidth, analysisHeight ->
                    detectedFaces = faces
                    imageAnalysisConfiguredSize = Size(analysisWidth, analysisHeight)
                },
                viewModel = viewModel,
                faceRecognitionViewModel = faceRecognitionViewModel
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
                    progress = { importProgress },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
            FaceRecognitionScreen(viewModel = faceRecognitionViewModel, context = context)
        }
    }
}

suspend fun importFacesFromUris(
    uris: List<Uri>,
    context: Context,
    faceDetector: FaceDetector,
    recognitionViewModel: FaceRecognitionViewModel,
    onProgress: (Float) -> Unit
) {
    uris.forEachIndexed { index, uri ->
        val uriBitmap = uriToBitmap(context, uri)
        if (uriBitmap == null) {
            onProgress((index + 1).toFloat() / uris.size)
            return@forEachIndexed
        }
        val inputImage = InputImage.fromBitmap(uriBitmap, 0)

        try {
            val faces = faceDetector.process(inputImage).await()
            recognitionViewModel.processPicFace(
                faces,
                inputImage,
                context
            )
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed for URI: $uri", e)
        } finally {
            onProgress((index + 1).toFloat() / uris.size)
        }
    }
}
