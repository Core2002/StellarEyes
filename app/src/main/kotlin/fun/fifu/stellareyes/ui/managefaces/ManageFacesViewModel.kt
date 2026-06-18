package `fun`.fifu.stellareyes.ui.managefaces

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.fifu.stellareyes.data.CatalogFieldConfig
import `fun`.fifu.stellareyes.data.FaceCatalogItem
import `fun`.fifu.stellareyes.data.FaceCatalogRepository
import `fun`.fifu.stellareyes.data.ImportMode
import `fun`.fifu.stellareyes.data.VectorSearchEngine
import `fun`.fifu.stellareyes.data.asDisplayString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

data class ManageFacesUiState(
    val items: List<FaceCatalogItem> = emptyList(),
    val fields: List<CatalogFieldConfig> = FaceCatalogRepository.defaultCatalogFields,
    val searchTerm: String = "",
    val viewMode: CatalogViewMode = CatalogViewMode.Grid,
    val loading: Boolean = true,
    val message: String? = null
) {
    val searchableFields: List<CatalogFieldConfig> = fields.filter { it.searchable }
    val filteredItems: List<FaceCatalogItem> =
        if (searchTerm.isBlank()) items else items.filter { item ->
            searchableFields.any { field ->
                item.fields[field.key]
                    ?.asDisplayString()
                    ?.contains(searchTerm, ignoreCase = true) == true
            }
        }
}

enum class CatalogViewMode {
    Grid,
    List
}

class ManageFacesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ManageFacesUiState())
    val uiState: StateFlow<ManageFacesUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

   fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val (items, fields) = FaceCatalogRepository.load(getApplication())
            val reconciled = FaceCatalogRepository.reconcileWithVectors(items)
            val cleanItems = reconciled.map { FaceCatalogRepository.sanitizeItem(it) }
            FaceCatalogRepository.save(getApplication(), cleanItems, fields)
            _uiState.value = ManageFacesUiState(items = cleanItems, fields = FaceCatalogRepository.defaultCatalogFields, loading = false)
        }
    }

    fun setSearchTerm(value: String) {
        _uiState.update { it.copy(searchTerm = value) }
    }

    fun setViewMode(mode: CatalogViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun saveItem(itemId: String?, values: Map<String, JsonElement>) {
        val id = itemId ?: values["id"]?.asDisplayString()?.takeIf { it.isNotBlank() }
            ?: System.currentTimeMillis().toString()
        _uiState.update { state ->
            val previous = state.items.firstOrNull { it.id == itemId }
            val item = FaceCatalogRepository.sanitizeItem(
                FaceCatalogItem(
                    id,
                    previous.orEmptyFields()
                        .plus(values)
                        .plus("id" to JsonPrimitive(id))
                )
            )
            val nextItems = if (itemId == null) {
                state.items + item
            } else {
                state.items.map { if (it.id == itemId) item else it }
            }
            syncVectorName(item)
            persist(nextItems, FaceCatalogRepository.defaultCatalogFields)
            state.copy(items = nextItems, fields = FaceCatalogRepository.defaultCatalogFields, message = "已保存")
        }
    }

    fun quickEdit(itemId: String, fieldKey: String, value: JsonElement) {
        _uiState.update { state ->
            val nextItems = state.items.map { item ->
                if (item.id == itemId) {
                    FaceCatalogRepository.sanitizeItem(item.copy(fields = item.fields.plus(fieldKey to value))).also { updated ->
                        syncVectorName(updated)
                    }
                } else {
                    item
                }
            }
            persist(nextItems, FaceCatalogRepository.defaultCatalogFields)
            state.copy(items = nextItems, message = "已更新")
        }
    }

    fun deleteItem(itemId: String) {
        _uiState.update { state ->
            val nextItems = state.items.filterNot { it.id == itemId }
            VectorSearchEngine.removeById(itemId)
            VectorSearchEngine.saveToFile(getApplication())
            persist(nextItems, FaceCatalogRepository.defaultCatalogFields)
            state.copy(items = nextItems, message = "已删除")
        }
    }

    fun saveFields(fields: List<CatalogFieldConfig>) {
        _uiState.update { state ->
            persist(state.items, FaceCatalogRepository.defaultCatalogFields)
            state.copy(fields = FaceCatalogRepository.defaultCatalogFields, message = "字段配置已固定")
        }
    }

    fun importJson(uri: Uri, mode: ImportMode) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
            val raw = FaceCatalogRepository.readTextFromUri(getApplication(), uri)
            val imported = FaceCatalogRepository.parseItems(raw)
            FaceCatalogRepository.syncCatalogToVectorStore(getApplication(), imported)
            _uiState.update { state ->
                    val nextItems = FaceCatalogRepository.applyImport(state.items, imported, mode)
                        .map { FaceCatalogRepository.sanitizeItem(it) }
                    persist(nextItems, FaceCatalogRepository.defaultCatalogFields)
                    state.copy(items = nextItems, fields = FaceCatalogRepository.defaultCatalogFields, message = "已导入 ${imported.size} 条数据")
                }
            }.getOrElse { error ->
                _uiState.update { it.copy(message = "导入失败：${error.localizedMessage}") }
            }
        }
    }

    fun exportJson() {
        viewModelScope.launch(Dispatchers.IO) {
            val message = FaceCatalogRepository.exportToDownloads(getApplication(), _uiState.value.items)
            _uiState.update { it.copy(message = message) }
        }
    }

    private fun persist(items: List<FaceCatalogItem>, fields: List<CatalogFieldConfig>) {
        FaceCatalogRepository.save(getApplication(), items.map { FaceCatalogRepository.sanitizeItem(it) }, fields)
    }

    private fun syncVectorName(item: FaceCatalogItem) {
        val name = item.fields["name"]?.asDisplayString()?.takeIf { it.isNotBlank() } ?: return
        if (VectorSearchEngine.getEntrieById(item.id) != null) {
            VectorSearchEngine.updateName(item.id, name)
            VectorSearchEngine.saveToFile(getApplication())
        }
    }
}

private fun FaceCatalogItem?.orEmptyFields(): Map<String, JsonElement> = this?.fields.orEmpty()
