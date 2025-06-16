package `fun`.fifu.stellareyes.ui.managefaces

import FaceRepository
import ManageFacesViewModel
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import `fun`.fifu.stellareyes.data.StoredFace
import `fun`.fifu.stellareyes.data.VectorSearchEngine
import `fun`.fifu.stellareyes.ui.camera.base64UrlToBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FaceRepositoryImpl"

class FaceRepositoryImpl(private val context: Context) : FaceRepository {

    private val facesFlow = MutableStateFlow<List<StoredFace>>(emptyList())

    init {
        loadFaces()
    }

    private fun loadFaces() {
        VectorSearchEngine.loadFromFile(context)
        val allFaces = getFacesFromEngine()
        facesFlow.value = allFaces
    }

    private fun getFacesFromEngine(): List<StoredFace> {
        return VectorSearchEngine.getAllEntries().map {
            StoredFace(
                id = it.id,
                vector = it.vector,
                name = it.name,
                imageUri = it.imageUri,
                timestamp = it.timestamp
            )
        }
    }

    override fun getAllStoredFaces(): Flow<List<StoredFace>> {
        return facesFlow.asStateFlow()
    }

    override fun deleteFace(faceId: String) {
        VectorSearchEngine.removeById(faceId)
        VectorSearchEngine.saveToFile(context)
        facesFlow.value = getFacesFromEngine()
    }

    override fun findMostSimilarFace(vector: FloatArray): StoredFace? {
        val bestId = VectorSearchEngine.searchTop1(vector).first ?: return null
        return VectorSearchEngine.getAllEntries().find { it.id == bestId }
    }

    override fun addFace(face: StoredFace) {
        VectorSearchEngine.add(face.id, face.vector, face.name, face.imageUri)
        VectorSearchEngine.saveToFile(context)
        facesFlow.value = getFacesFromEngine()
    }

    override fun updateFaceName(faceId: String, newName: String) {
        VectorSearchEngine.updateName(faceId, newName)
        VectorSearchEngine.saveToFile(context)
        facesFlow.value = getFacesFromEngine()
    }

    override fun updateVectors() {
        VectorSearchEngine.updateVectors()
        VectorSearchEngine.saveToFile(context)
        loadFaces()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFacesScreen(
    viewModel: ManageFacesViewModel = ManageFacesViewModel(FaceRepositoryImpl(LocalContext.current)),
    onNavigateBack: () -> Unit
) {
    val storedFaces by viewModel.storedFaces.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showEditDialogFor by remember { mutableStateOf<StoredFace?>(null) }
    var showConfirmDeleteDialogFor by remember { mutableStateOf<StoredFace?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("管理已存储的数据") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                viewModel.updateVectors()
                                snackbarHostState.showSnackbar("数据已重载")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("重载失败: ${e.localizedMessage}")
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Replay,
                            contentDescription = "数据重载"
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val resultMessage =
                                VectorSearchEngine.exportVectorsJsonToDownloads(context)
                            snackbarHostState.showSnackbar(resultMessage)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "导出数据"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (storedFaces.isEmpty()) {
                Text(
                    text = "没有人脸数据",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(storedFaces, key = { it.id }) { face ->
                        StoredFaceItem(
                            face = face,
                            onEditClick = { showEditDialogFor = face },
                            onDeleteClick = { showConfirmDeleteDialogFor = face }
                        )
                        HorizontalDivider()
                    }
                }
            }

            // 编辑名称对话框
            showEditDialogFor?.let { faceToEdit ->
                EditFaceNameDialog(
                    face = faceToEdit,
                    onDismiss = { showEditDialogFor = null },
                    onConfirm = { newName ->
                        viewModel.updateFaceName(faceToEdit.id, newName)
                        showEditDialogFor = null
                    }
                )
            }

            // 确认删除对话框
            showConfirmDeleteDialogFor?.let { faceToDelete ->
                ConfirmDeleteDialog(
                    faceName = faceToDelete.name,
                    onDismiss = { showConfirmDeleteDialogFor = null },
                    onConfirm = {
                        viewModel.deleteFace(faceToDelete.id)
                        showConfirmDeleteDialogFor = null
                    }
                )
            }
        }
    }
}

@Composable
fun StoredFaceItem(
    face: StoredFace,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用 Coil 或 Glide 等库异步加载图片
            Image(
                painter = rememberAsyncImagePainter(model = base64UrlToBitmap(face.imageUri)),
                contentDescription = "人脸 ${face.name}",
                modifier = Modifier
                    .size(80.dp)
                    .padding(end = 12.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = face.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "ID: ${face.id}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
                Text(
                    text = "存储于: ${
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                            Date(face.timestamp)
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑名称",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除人脸",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun EditFaceNameDialog(
    face: StoredFace,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(face.name) { mutableStateOf(face.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑人脸名称") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    faceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("你确定要删除 $faceName 吗？此操作无法撤销。") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
