package `fun`.fifu.stellareyes.ui.managefaces

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import `fun`.fifu.stellareyes.data.CatalogFieldConfig
import `fun`.fifu.stellareyes.data.CatalogFieldType
import `fun`.fifu.stellareyes.data.FaceCatalogItem
import `fun`.fifu.stellareyes.data.FaceCatalogRepository
import `fun`.fifu.stellareyes.data.ImportMode
import `fun`.fifu.stellareyes.data.asDisplayString
import `fun`.fifu.stellareyes.data.asNumberOrNull
import `fun`.fifu.stellareyes.data.isImageLike
import `fun`.fifu.stellareyes.ui.camera.base64UrlToBitmap
import `fun`.fifu.stellareyes.ui.camera.uriToBitmap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManageFacesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageFacesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingItem by remember { mutableStateOf<FaceCatalogItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<FaceCatalogItem?>(null) }
    var detailItemId by remember { mutableStateOf<String?>(null) }
    var pendingImportMode by remember { mutableStateOf<ImportMode?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val mode = pendingImportMode
        pendingImportMode = null
        if (uri != null && mode != null) viewModel.importJson(uri, mode)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("人脸管理系统", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        pendingImportMode = ImportMode.Replace
                        importLauncher.launch("application/json")
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "导入并替换")
                    }
                    IconButton(onClick = viewModel::exportJson) {
                        Icon(Icons.Filled.Upload, contentDescription = "导出数据")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加人脸")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            CatalogToolbar(
                searchTerm = state.searchTerm,
                viewMode = state.viewMode,
                onSearchChange = viewModel::setSearchTerm,
                onViewModeChange = viewModel::setViewMode,
                onMergeImport = {
                    pendingImportMode = ImportMode.Merge
                    importLauncher.launch("application/json")
                }
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.filteredItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.searchTerm.isBlank()) "暂无数据" else "没有找到匹配的人脸信息")
                }
            } else if (state.viewMode == CatalogViewMode.Grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.filteredItems, key = { it.id }) { item ->
                        FaceGridTile(
                            item = item,
                            fields = state.fields,
                            onClick = { detailItemId = item.id }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.filteredItems, key = { it.id }) { item ->
                        FaceCatalogCard(
                            item = item,
                            fields = state.fields,
                            compact = false,
                            onEdit = { editingItem = item },
                            onDelete = { deletingItem = item },
                            onQuickEdit = viewModel::quickEdit
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        DynamicFaceDialog(
            title = "添加新人脸",
            item = null,
            fields = state.fields,
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.saveItem(null, it)
                showAddDialog = false
            }
        )
    }

    editingItem?.let { item ->
        DynamicFaceDialog(
            title = "编辑人脸信息",
            item = item,
            fields = state.fields,
            onDismiss = { editingItem = null },
            onSave = {
                viewModel.saveItem(item.id, it)
                editingItem = null
            }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除 ${catalogTitle(item, state.fields)} 吗？此操作无法撤销。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteItem(item.id)
                    deletingItem = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("取消") } }
        )
    }

    detailItemId?.let { id ->
        val item = state.items.firstOrNull { it.id == id }
        if (item == null) {
            detailItemId = null
            return@let
        }
        FaceDetailDialog(
            item = item,
            fields = state.fields,
            onDismiss = { detailItemId = null },
            onEdit = {
                detailItemId = null
                editingItem = item
            },
            onDelete = {
                detailItemId = null
                deletingItem = item
            },
            onQuickEdit = viewModel::quickEdit
        )
    }
}

@Composable
private fun CatalogToolbar(
    searchTerm: String,
    viewMode: CatalogViewMode,
    onSearchChange: (String) -> Unit,
    onViewModeChange: (CatalogViewMode) -> Unit,
    onMergeImport: () -> Unit
) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchTerm,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text("搜索姓名、标签或描述") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = viewMode == CatalogViewMode.Grid,
                onClick = { onViewModeChange(CatalogViewMode.Grid) },
                label = { Text("网格") },
                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = viewMode == CatalogViewMode.List,
                onClick = { onViewModeChange(CatalogViewMode.List) },
                label = { Text("列表") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onMergeImport) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("合并导入")
            }
        }
    }
}

@Composable
private fun FaceGridTile(
    item: FaceCatalogItem,
    fields: List<CatalogFieldConfig>,
    onClick: () -> Unit
) {
    val title = catalogTitle(item, fields)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CatalogAvatar(
                url = catalogImageValue(item, fields),
                title = title,
                size = 80.dp,
                previewEnabled = false
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FaceCatalogCard(
    item: FaceCatalogItem,
    fields: List<CatalogFieldConfig>,
    compact: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickEdit: (String, String, JsonElement) -> Unit
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    var quickField by remember { mutableStateOf<CatalogFieldConfig?>(null) }
    val avatarUrl = catalogImageValue(item, fields)
    val visible = fields.filter { it.display && it.type != CatalogFieldType.Image && item.hasValue(it) }
    val collapsed = fields.filter { !it.display && item.hasValue(it) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)
        ) {
            if (avatarUrl != null) {
                CatalogAvatar(
                    url = avatarUrl,
                    title = catalogTitle(item, fields),
                    size = 56.dp,
                    previewEnabled = false
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        catalogTitle(item, fields),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    visible.filterNot { it.key == "id" }.take(if (compact) 2 else 4).forEach { field ->
                        CatalogFieldRow(field, item.fields[field.key], onQuickEdit = { quickField = field })
                    }
                }
                if (collapsed.isNotEmpty()) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            if (expanded) "收起" else "展开 ${collapsed.size} 个字段",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (expanded) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        collapsed.forEach { field ->
                            CatalogFieldRow(field, item.fields[field.key], onQuickEdit = { quickField = field })
                        }
                    }
                }
            }
        }
    }

    quickField?.let { field ->
        QuickEditDialog(
            field = field,
            value = item.fields[field.key],
            onDismiss = { quickField = null },
            onSave = {
                onQuickEdit(item.id, field.key, it)
                quickField = null
            }
        )
    }
}

@Composable
private fun FaceDetailDialog(
    item: FaceCatalogItem,
    fields: List<CatalogFieldConfig>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickEdit: (String, String, JsonElement) -> Unit
) {
    var quickField by remember(item.id) { mutableStateOf<CatalogFieldConfig?>(null) }
    val title = catalogTitle(item, fields)
    val visibleFields = fields
        .filter { it.type != CatalogFieldType.Image && item.hasValue(it) }
        .sortedBy { it.priority }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
                .widthIn(max = 640.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CatalogAvatar(
                        url = catalogImageValue(item, fields),
                        title = title,
                        size = 156.dp,
                        previewEnabled = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("编辑")
                        }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleFields.forEach { field ->
                            DetailFieldRow(
                                field = field,
                                value = item.fields[field.key],
                                onQuickEdit = { quickField = field }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    quickField?.let { field ->
        QuickEditDialog(
            field = field,
            value = item.fields[field.key],
            onDismiss = { quickField = null },
            onSave = {
                onQuickEdit(item.id, field.key, it)
                quickField = null
            }
        )
    }
}

@Composable
private fun DetailFieldRow(
    field: CatalogFieldConfig,
    value: JsonElement?,
    onQuickEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    field.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    fieldValueText(field, value),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (field.type != CatalogFieldType.Image) {
                IconButton(onClick = onQuickEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "快速编辑 ${field.label}")
                }
            }
        }
    }
}

@Composable
private fun CatalogAvatar(url: String?, title: String, size: Dp = 88.dp, previewEnabled: Boolean = true) {
    var preview by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bitmap = remember(url) {
        when {
            url?.startsWith("data:image/", ignoreCase = true) == true -> base64UrlToBitmap(url)
            url?.startsWith("content://", ignoreCase = true) == true -> uriToBitmap(context, url.toUri())
            else -> null
        }
    }
    val imageModifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(8.dp))
        .then(if (previewEnabled && !url.isNullOrBlank()) Modifier.clickable { preview = true } else Modifier)

    if (url.isNullOrBlank()) {
        Box(
            modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title.take(1).ifBlank { "?" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = imageModifier
        )
    } else {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = imageModifier
        )
    }
    if (preview && !url.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { preview = false },
            confirmButton = { TextButton(onClick = { preview = false }) { Text("关闭") } },
            text = {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(url),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        )
    }
}

@Composable
private fun CatalogFieldRow(
    field: CatalogFieldConfig,
    value: JsonElement?,
    onQuickEdit: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            "${field.label}:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            fieldValueText(field, value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (field.type != CatalogFieldType.Image) {
            IconButton(onClick = onQuickEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "快速编辑 ${field.label}", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DynamicFaceDialog(
    title: String,
    item: FaceCatalogItem?,
    fields: List<CatalogFieldConfig>,
    onDismiss: () -> Unit,
    onSave: (Map<String, JsonElement>) -> Unit
) {
    val values = remember(item?.id, fields) {
        mutableStateMapOf<String, String>().apply {
            fields.filterNot { it.key == "id" }.forEach { field ->
                this[field.key] = item?.fields?.get(field.key)?.let { fieldValueText(field, it) } ?: defaultValue(field)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item?.let {
                    EditingFaceHeader(
                        item = it,
                        displayName = values["name"]?.takeIf { name -> name.isNotBlank() } ?: catalogTitle(it, fields)
                    )
                }
                fields.filterNot { it.key == "id" }.forEach { field ->
                    CatalogInput(field, values[field.key].orEmpty()) { values[field.key] = it }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(values.mapValues { (key, value) ->
                    val field = fields.first { it.key == key }
                    inputToJson(field, value)
                })
            }) { Text(if (item == null) "添加数据" else "保存修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EditingFaceHeader(
    item: FaceCatalogItem,
    displayName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatalogAvatar(
                url = FaceCatalogRepository.avatarValue(item),
                title = displayName,
                size = 72.dp,
                previewEnabled = true
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "正在编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "ID: ${item.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CatalogInput(field: CatalogFieldConfig, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = if (field.type == CatalogFieldType.Textarea) 3 else 1,
        maxLines = if (field.type == CatalogFieldType.Textarea) 5 else 1,
        label = { Text(field.label + if (field.required) " *" else "") },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field.type == CatalogFieldType.Number) KeyboardType.Decimal else KeyboardType.Text
        ),
        supportingText = {
            when (field.type) {
                CatalogFieldType.Tags -> Text("多个标签用逗号分隔")
                CatalogFieldType.Select -> Text("可选：${field.options.joinToString("、")}")
                CatalogFieldType.Image -> Text("支持 URL、data:image 或 content://")
                else -> {}
            }
        }
    )
}

@Composable
private fun QuickEditDialog(
    field: CatalogFieldConfig,
    value: JsonElement?,
    onDismiss: () -> Unit,
    onSave: (JsonElement) -> Unit
) {
    var text by remember(field.key, value) { mutableStateOf(fieldValueText(field, value)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速编辑 ${field.label}") },
        text = { CatalogInput(field, text) { text = it } },
        confirmButton = { Button(onClick = { onSave(inputToJson(field, text)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FieldConfigDialog(
    fields: List<CatalogFieldConfig>,
    onDismiss: () -> Unit,
    onSave: (List<CatalogFieldConfig>) -> Unit
) {
    val editable = remember(fields) { mutableStateMapOf<String, CatalogFieldConfig>().apply { fields.forEach { put(it.key, it) } } }
    var adding by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字段配置管理") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加字段")
                    }
                    OutlinedButton(onClick = { editable.clear(); FaceCatalogRepository.defaultCatalogFields.forEach { editable[it.key] = it } }) {
                        Text("重置默认")
                    }
                }
                if (adding) {
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(newKey, { newKey = it }, label = { Text("字段键名") }, singleLine = true)
                            OutlinedTextField(newLabel, { newLabel = it }, label = { Text("显示标签") }, singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { adding = false }) { Text("取消") }
                                Button(onClick = {
                                    if (newKey.isNotBlank() && newLabel.isNotBlank()) {
                                        editable[newKey] = CatalogFieldConfig(
                                            key = newKey,
                                            label = newLabel,
                                            type = CatalogFieldType.Text,
                                            searchable = true,
                                            priority = editable.size + 1
                                        )
                                        newKey = ""
                                        newLabel = ""
                                        adding = false
                                    }
                                }) { Text("保存") }
                            }
                        }
                    }
                }
                editable.values.sortedBy { it.priority }.forEach { field ->
                    FieldConfigRow(
                        field = field,
                        onChange = { editable[field.key] = it },
                        onDelete = { editable.remove(field.key) }
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(editable.values.toList()) }) { Text("保存配置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FieldConfigRow(
    field: CatalogFieldConfig,
    onChange: (CatalogFieldConfig) -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${field.key} (${field.label})", style = MaterialTheme.typography.bodySmall)
                    Text(field.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, enabled = field.key != "id") {
                    Icon(Icons.Filled.Delete, contentDescription = "删除字段")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(field.display, { onChange(field.copy(display = !field.display)) }, label = { Text("外显") })
                FilterChip(field.searchable, { onChange(field.copy(searchable = !field.searchable)) }, label = { Text("可搜索") })
                FilterChip(field.required, { onChange(field.copy(required = !field.required)) }, label = { Text("必填") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CatalogFieldType.entries.forEach { type ->
                    FilterChip(
                        selected = field.type == type,
                        onClick = { onChange(field.copy(type = type)) },
                        label = { Text(type.name) }
                    )
                }
            }
        }
    }
}

private fun catalogTitle(item: FaceCatalogItem, fields: List<CatalogFieldConfig>): String {
    val titleField = fields
        .filter { it.key != "id" && it.type == CatalogFieldType.Text }
        .sortedBy { it.priority }
        .firstOrNull { item.hasValue(it) }
    return titleField?.let { item.fields[it.key]?.asDisplayString() } ?: item.id
}

private fun catalogImageValue(item: FaceCatalogItem, fields: List<CatalogFieldConfig>): String? {
    return FaceCatalogRepository.avatarValue(item)
}

private fun FaceCatalogItem.hasValue(field: CatalogFieldConfig): Boolean {
    val value = fields[field.key] ?: return false
    val text = value.asDisplayString()?.trim().orEmpty()
    return when (field.type) {
        CatalogFieldType.Tags -> text.isNotBlank()
        CatalogFieldType.Number -> value.asNumberOrNull() != null || text.isNotBlank()
        else -> text.isNotBlank()
    }
}

private fun fieldValueText(field: CatalogFieldConfig, value: JsonElement?): String {
    if (value == null) return ""
    return when (field.type) {
        CatalogFieldType.Tags -> {
            if (value is JsonArray) value.joinToString(", ") { it.asDisplayString().orEmpty() } else value.asDisplayString().orEmpty()
        }
        CatalogFieldType.Number -> value.asNumberOrNull()?.let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        } ?: value.asDisplayString().orEmpty()
        else -> value.asDisplayString().orEmpty()
    }
}

private fun inputToJson(field: CatalogFieldConfig, value: String): JsonElement {
    return when (field.type) {
        CatalogFieldType.Number -> JsonPrimitive(value.toDoubleOrNull() ?: 0.0)
        CatalogFieldType.Tags -> JsonArray(value.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { JsonPrimitive(it) })
        else -> JsonPrimitive(value)
    }
}

private fun defaultValue(field: CatalogFieldConfig): String {
    return when (field.type) {
        CatalogFieldType.Number -> "0"
        CatalogFieldType.Select -> field.options.firstOrNull().orEmpty()
        else -> ""
    }
}



