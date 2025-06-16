package `fun`.fifu.stellareyes.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import `fun`.fifu.stellareyes.FaceNet
import `fun`.fifu.stellareyes.data.StoredFace
import `fun`.fifu.stellareyes.data.VectorSearchEngine
import `fun`.fifu.stellareyes.data.VectorSearchEngine.cosineSimilarity
import `fun`.fifu.stellareyes.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "FaceRecognition"

// Helper data class to store candidate faces
data class FaceCandidate(val bitmap: Bitmap, val embedding: FloatArray) {
    // Auto-generated equals and hashCode for FloatArray are based on identity,
    // so we need to override them for proper comparison if these objects are stored in sets/maps.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceCandidate

        if (bitmap != other.bitmap) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

sealed class RecognitionState {
    data class Recognized(val sf: StoredFace, val faceBitmap: Bitmap, val cosineSimilarity: Float) :
        RecognitionState()

    data class PendingSelection(val candidates: List<FaceCandidate>) :
        RecognitionState() // New state

    object NewFaceAdded : RecognitionState()
    object Idle : RecognitionState()
}

@Composable
fun FaceRecognitionResultUI(viewModel: FaceRecognitionViewModel, context: Context) {
    val state = viewModel.recognitionState
    var selectedCandidateIndex by remember { mutableStateOf<Int?>(null) }

    // Reset selected index when dialog is dismissed or state changes away from PendingSelection
    if (state !is RecognitionState.PendingSelection && selectedCandidateIndex != null) {
        selectedCandidateIndex = null
    }


    when (state) {
        is RecognitionState.Recognized -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = {
                    Text(
                        "Cosine Similarity ${
                            String.format(
                                "%.3f",
                                state.cosineSimilarity
                            )
                        }"
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("识别到：${state.sf.name}", modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("当前人脸", modifier = Modifier.padding(bottom = 4.dp))
                                Image(
                                    bitmap = state.faceBitmap.asImageBitmap(),
                                    contentDescription = "当前捕获的人脸",
                                    modifier = Modifier.size(100.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("库中人脸", modifier = Modifier.padding(bottom = 4.dp))
                                val bitmapInDb = base64UrlToBitmap(state.sf.imageUri)
                                if (bitmapInDb != null) {
                                    Image(
                                        bitmap = bitmapInDb.asImageBitmap(),
                                        contentDescription = "数据库中的匹配人脸",
                                        modifier = Modifier.size(100.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("无法加载库中图片")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("确定")
                    }
                }
            )
        }

        RecognitionState.NewFaceAdded -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("新用户") },
                text = { Text("新的人脸已添加到数据库") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("确定")
                    }
                }
            )
        }

        is RecognitionState.PendingSelection -> {
            AlertDialog(
                onDismissRequest = {
                    viewModel.resetState()
                    selectedCandidateIndex = null
                },
                title = { Text("选择一张人脸进行保存") },
                text = {
                    Column {
                        Text(
                            "收集到 ${state.candidates.size} 张新的人脸。请选择一张进行保存，或取消。",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.height(100.dp), // Adjust height as needed
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.candidates) { index, candidate ->
                                Image(
                                    bitmap = candidate.bitmap.asImageBitmap(),
                                    contentDescription = "Candidate face $index",
                                    modifier = Modifier
                                        .size(80.dp) // Adjust size as needed
                                        .clickable { selectedCandidateIndex = index }
                                        .border(
                                            width = 2.dp,
                                            color = if (selectedCandidateIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .padding(2.dp), // Padding inside the border
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedCandidateIndex?.let { index ->
                                if (index < state.candidates.size) { // defensive check
                                    viewModel.saveSelectedFace(context, state.candidates[index])
                                }
                            }
                            viewModel.resetState()
                            selectedCandidateIndex = null
                        },
                        enabled = selectedCandidateIndex != null
                    ) {
                        Text("保存选择")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.resetState()
                        selectedCandidateIndex = null
                    }) {
                        Text("取消")
                    }
                }
            )
        }

        RecognitionState.Idle -> {
            // 不显示弹窗
        }
    }
}

@Composable
fun FaceRecognitionScreen(viewModel: FaceRecognitionViewModel, context: Context) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 你的摄像头预览和识别按钮等 UI
        FaceRecognitionResultUI(viewModel, context)
    }
}

object FaceRecognitionViewModel : ViewModel() {

    private val newFaceCandidates = mutableListOf<FaceCandidate>()
    private const val MAX_CANDIDATES = 3 // Threshold for prompting user selection

    var recognitionState by mutableStateOf<RecognitionState>(RecognitionState.Idle)
        private set

    fun resetState() {
        newFaceCandidates.clear() // Make sure to clear candidates if they were copied to PendingSelection
        recognitionState = RecognitionState.Idle
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveSelectedFace(context: Context, candidate: FaceCandidate) {
        VectorSearchEngine.add(
            Uuid.random().toString(),
            candidate.embedding,
            "p${System.currentTimeMillis()}", // Consider allowing user to name it
            bitmapToBase64Url(candidate.bitmap) // Assuming bitmapToBase64Url is defined
        )
        VectorSearchEngine.saveToFile(context)
        recognitionState = RecognitionState.NewFaceAdded
        // After showing NewFaceAdded, reset to Idle. This could be handled by the dialog's onDismiss or a timer.
        // For simplicity now, the dialog's own actions will call resetState.
    }


    @OptIn(ExperimentalUuidApi::class)
    suspend fun runFaceNetModel(
        context: Context,
        viewModel: SettingsViewModel,
        faceBitmap: Bitmap
    ) {
        val performInference = FaceNet.getFaceEmbedding(faceBitmap)
        val top1 = VectorSearchEngine.searchTop1(performInference)
        if (!top1.first.isNullOrEmpty()) {
            val entre = VectorSearchEngine.getEntrieById(top1.first!!)
            if (entre != null) {
                // Assuming base64UrlToBitmap is available for full Recognized state display
                val img_db = base64UrlToBitmap(entre.imageUri)
                if (img_db != null) {
                    val performInference_db = FaceNet.getFaceEmbedding(img_db)
                    val cosineSimilarity =
                        cosineSimilarity(performInference_db, performInference)
                    recognitionState =
                        RecognitionState.Recognized(entre, faceBitmap, cosineSimilarity)
                } else { // Fallback if DB image can't be loaded for comparison
                    recognitionState = RecognitionState.Recognized(entre, faceBitmap, top1.second ?: 0f)
                }
            }
        } else if (viewModel.isAutoRecordEnabled.value) {
            newFaceCandidates.add(FaceCandidate(faceBitmap, performInference))
            if (newFaceCandidates.size >= MAX_CANDIDATES) {
                recognitionState = RecognitionState.PendingSelection(newFaceCandidates.toList())
                newFaceCandidates.clear()
            } else {
                Log.d(
                    TAG,
                    "Collected new face candidate. Total: ${newFaceCandidates.size}/${MAX_CANDIDATES}"
                )
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun runOnlyPic(
        context: Context,
        faceBitmap: Bitmap
    ) {
        val performInference = FaceNet.getFaceEmbedding(faceBitmap)
        val top1 = VectorSearchEngine.searchTop1(performInference)
        if (top1.first.isNullOrEmpty()) {
            VectorSearchEngine.add(
                Uuid.random().toString(),
                performInference,
                "P${System.currentTimeMillis()}",
                bitmapToBase64Url(faceBitmap) // Assuming bitmapToBase64Url is defined
            )
            VectorSearchEngine.saveToFile(context)
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    fun processAllFaces(
        faces: List<Face>,
        inputImage: InputImage,
        context: Context,
        viewModel: SettingsViewModel
    ) {
        for (face in faces) {
            val bitmapInternal = inputImage.bitmapInternal
            if (bitmapInternal == null) continue
            // Assuming cropFaceFromBitmapSquare is defined
            val faceBitmap = cropFaceFromBitmapSquare(
                bitmapInternal,
                face.boundingBox
            )
            if (faceBitmap == null) continue
            val resized = faceBitmap.scale(160, 160)
            CoroutineScope(FaceNet.tfliteThread).launch {
                val executionTime = measureTimeMillis {
                    runFaceNetModel(context, viewModel, resized)
                }
                Log.d(
                    TAG,
                    "runFaceNetModel execution time: $executionTime ms"
                )
            }
        }
        if (faces.isEmpty()) {
            Log.d(TAG, "No faces detected.")
            return
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun processLargestFace(
        faces: List<Face>,
        inputImage: InputImage,
        context: Context,
        viewModel: SettingsViewModel
    ) {
        val largestFace =
            faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        largestFace?.let { face ->
            val bitmapInternal = inputImage.bitmapInternal
            if (bitmapInternal == null) {
                Log.e(TAG, "bitmapInternal is null, cannot crop face.")
                return@let
            }
            // Assuming cropFaceFromBitmapSquare is defined
            val faceBitmap = cropFaceFromBitmapSquare(
                bitmapInternal,
                face.boundingBox
            )
            if (faceBitmap == null) {
                Log.e(TAG, "Failed to crop the largest face.")
                return@let
            }
            val resized = faceBitmap.scale(160, 160)
            CoroutineScope(FaceNet.tfliteThread).launch {
                val executionTime = measureTimeMillis {
                    runFaceNetModel(context, viewModel, resized)
                }
                Log.d(
                    TAG,
                    "runFaceNetModel for largest face execution time: $executionTime ms"
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun processPicFace(
        faces: List<Face>,
        inputImage: InputImage,
        context: Context
    ) {
        val largestFace =
            faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        largestFace?.let { face ->
            val bitmapInternal = inputImage.bitmapInternal
            if (bitmapInternal == null) {
                Log.e(TAG, "bitmapInternal is null, cannot crop face.")
                return@let
            }
            // Assuming cropFaceFromBitmapSquare is defined
            val faceBitmap = cropFaceFromBitmapSquare(
                bitmapInternal,
                face.boundingBox
            )
            if (faceBitmap == null) {
                Log.e(TAG, "Failed to crop the largest face.")
                return@let
            }
            val resized = faceBitmap.scale(160, 160)
            CoroutineScope(FaceNet.tfliteThread).launch {
                val executionTime = measureTimeMillis {
                    runOnlyPic(context, resized)
                }
                Log.d(
                    TAG,
                    "runFaceNetModel for largest face execution time: $executionTime ms"
                )
            }
        }
    }
}
