package `fun`.fifu.stellareyes.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import `fun`.fifu.stellareyes.FaceNet
import `fun`.fifu.stellareyes.ui.camera.base64UrlToBitmap

@Serializable
enum class CatalogFieldType {
    Text,
    Number,
    Select,
    Tags,
    Textarea,
    Image
}

@Serializable
data class CatalogFieldConfig(
    val key: String,
    val label: String,
    val type: CatalogFieldType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val searchable: Boolean = false,
    val display: Boolean = false,
    val priority: Int = 999
)

@Serializable
data class FaceCatalogItem(
    val id: String,
    val fields: Map<String, JsonElement>
)

@Serializable
private data class FaceCatalogStore(
    val items: List<FaceCatalogItem> = emptyList(),
    val fields: List<CatalogFieldConfig> = emptyList()
)

enum class ImportMode {
    Replace,
    Merge
}

object FaceCatalogRepository {
    private const val FILE_NAME = "face_catalog.json"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_AVATAR = "_avatar"
    private val allowedFieldKeys = setOf(KEY_ID, KEY_NAME, KEY_TIMESTAMP, KEY_DESCRIPTION, KEY_AVATAR)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val defaultCatalogFields = listOf(
        CatalogFieldConfig(KEY_ID, "ID", CatalogFieldType.Text, required = true, searchable = false, display = true, priority = 0),
        CatalogFieldConfig(KEY_NAME, "姓名", CatalogFieldType.Text, required = true, searchable = true, display = true, priority = 1),
        CatalogFieldConfig(KEY_TIMESTAMP, "时间戳", CatalogFieldType.Number, searchable = false, display = true, priority = 2),
        CatalogFieldConfig(KEY_DESCRIPTION, "描述", CatalogFieldType.Textarea, searchable = true, display = true, priority = 3),
    )

    fun load(context: Context): Pair<List<FaceCatalogItem>, List<CatalogFieldConfig>> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val bootstrapped = bootstrapFromVectors()
            return bootstrapped to defaultCatalogFields
        }

        return runCatching {
            val content = file.readText()
            val store = json.decodeFromString<FaceCatalogStore>(content)
            store.items.map { sanitizeItem(it) } to defaultCatalogFields
        }.getOrElse {
            emptyList<FaceCatalogItem>() to defaultCatalogFields
        }
    }

    fun save(context: Context, items: List<FaceCatalogItem>, fields: List<CatalogFieldConfig>) {
        File(context.filesDir, FILE_NAME).writeText(
            json.encodeToString(FaceCatalogStore(items.map { sanitizeItem(it) }, defaultCatalogFields))
        )
    }

    fun parseItems(rawJson: String): List<FaceCatalogItem> {
        val root = json.parseToJsonElement(rawJson)
        require(root is JsonArray) { "导入的数据必须是数组格式" }

        return root.mapIndexed { index, element ->
            require(element is JsonObject) { "第 ${index + 1} 项不是对象" }
            val id = element["id"]?.asDisplayString()?.takeIf { it.isNotBlank() }
                ?: "imported-${System.currentTimeMillis()}-$index"
            sanitizeItem(FaceCatalogItem(id = id, fields = element.toMutableMap().plus(KEY_ID to JsonPrimitive(id))))
        }
    }

    fun readTextFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("无法读取所选文件")
    }

    fun exportToDownloads(context: Context, items: List<FaceCatalogItem>): String {
        val fileName = "faces_export_${System.currentTimeMillis()}.json"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return "导出失败：无法创建下载文件"

        return runCatching {
            val payload = JsonArray(items.map { JsonObject(exportFields(it)) })
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            } ?: error("无法写入文件")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            "已导出到 Downloads/$fileName"
        }.getOrElse {
            runCatching { context.contentResolver.delete(uri, null, null) }
            "导出失败：${it.localizedMessage}"
        }
    }

    fun applyImport(
        currentItems: List<FaceCatalogItem>,
        importedItems: List<FaceCatalogItem>,
        mode: ImportMode
    ): List<FaceCatalogItem> {
        if (mode == ImportMode.Replace) return importedItems
        val existingIds = currentItems.map { it.id }.toSet()
        return currentItems + importedItems.filterNot { it.id in existingIds }
    }

    fun detectFields(
        items: List<FaceCatalogItem>,
        baseConfig: List<CatalogFieldConfig> = emptyList()
    ): List<CatalogFieldConfig> {
        return defaultCatalogFields
    }

    fun sanitizeItem(item: FaceCatalogItem): FaceCatalogItem {
        val id = item.fields[KEY_ID]?.asDisplayString()?.takeIf { it.isNotBlank() } ?: item.id
        val avatar = item.fields[KEY_AVATAR]
            ?: item.fields["image"]
            ?: item.fields["imageUri"]
            ?: item.fields["avatar"]
            ?: item.fields["photo"]

        val sanitized = buildMap {
            put(KEY_ID, JsonPrimitive(id))
            put(KEY_NAME, item.fields[KEY_NAME] ?: JsonPrimitive(""))
            put(KEY_TIMESTAMP, item.fields[KEY_TIMESTAMP] ?: JsonPrimitive(System.currentTimeMillis()))
            put(KEY_DESCRIPTION, item.fields[KEY_DESCRIPTION] ?: JsonPrimitive(""))
            if (avatar != null) put(KEY_AVATAR, avatar)
        }
        return FaceCatalogItem(id = id, fields = sanitized)
    }

    fun avatarValue(item: FaceCatalogItem): String? {
        return item.fields[KEY_AVATAR]?.takeIf { it.isImageLike() }?.asDisplayString()
    }

    fun reconcileWithVectors(currentItems: List<FaceCatalogItem>): List<FaceCatalogItem> {
        val existingIds = currentItems.map { it.id }.toSet()
        val vectorEntries = VectorSearchEngine.getAllEntries()
        val missing = vectorEntries.filter { it.id !in existingIds }
        if (missing.isEmpty()) return currentItems
        val newItems = missing.map { face -> sanitizeItem(vectorToItem(face)) }
        return currentItems + newItems
    }

    fun syncVectorEntry(context: Context, id: String) {
        val storedFace = VectorSearchEngine.getEntryById(id) ?: return
        val (items, fields) = load(context)
        if (items.any { it.id == id }) return
        val newItem = sanitizeItem(vectorToItem(storedFace))
        save(context, items + newItem, fields)
    }

    /**
     * Sync catalog items into VectorSearchEngine.
     * Creates StoredFace entries for catalog items that have avatar images
     * but no corresponding vector entry, then triggers vector recomputation.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun syncCatalogToVectorStore(context: Context, items: List<FaceCatalogItem>) {
        var changed = false
        for (item in items) {
            if (VectorSearchEngine.getEntryById(item.id) != null) continue
            val avatarUrl = avatarValue(item) ?: continue
            val name = item.fields[KEY_NAME]?.asDisplayString().orEmpty()
            val timestamp = item.fields[KEY_TIMESTAMP]?.asNumberOrNull()?.toLong()
                ?: System.currentTimeMillis()
            val vector = withContext(FaceNet.tfliteThread) {
                val bitmap = base64UrlToBitmap(avatarUrl)
                if (bitmap != null) FaceNet.getFaceEmbedding(bitmap) else FloatArray(512)
            }
            VectorSearchEngine.add(
                id = item.id,
                vector = vector,
                name = name,
                imageUri = avatarUrl,
                timestamp = timestamp
            )
            changed = true
        }
        if (changed) {
            VectorSearchEngine.saveToFile(context)
        }
    }

    private fun vectorToItem(face: StoredFace): FaceCatalogItem {
        return FaceCatalogItem(
            id = face.id,
            fields = mapOf(
                KEY_ID to JsonPrimitive(face.id),
                KEY_NAME to JsonPrimitive(face.name),
                KEY_AVATAR to JsonPrimitive(face.imageUri),
                KEY_TIMESTAMP to JsonPrimitive(face.timestamp),
                KEY_DESCRIPTION to JsonPrimitive("")
            )
        )
    }

    private fun exportFields(item: FaceCatalogItem): Map<String, JsonElement> {
        val sanitized = sanitizeItem(item)
        return sanitized.fields.filterKeys { it in allowedFieldKeys }
    }

    private fun bootstrapFromVectors(): List<FaceCatalogItem> {
        return VectorSearchEngine.getAllEntries().map { face ->
            FaceCatalogItem(
                id = face.id,
                fields = mapOf(
                    KEY_ID to JsonPrimitive(face.id),
                    KEY_NAME to JsonPrimitive(face.name),
                    KEY_AVATAR to JsonPrimitive(face.imageUri),
                    KEY_TIMESTAMP to JsonPrimitive(face.timestamp),
                    KEY_DESCRIPTION to JsonPrimitive("")
                )
            )
        }
    }

    private fun inferFieldType(values: List<JsonElement>): CatalogFieldType {
        if (values.any { it.isImageLike() }) return CatalogFieldType.Image
        if (values.isNotEmpty() && values.all { it.asNumberOrNull() != null }) return CatalogFieldType.Number
        if (values.any { it is JsonArray }) return CatalogFieldType.Tags

        val unique = values.mapNotNull { it.asDisplayString() }.distinct()
        if (unique.size in 2..5 && unique.all { it.length <= 10 && it.all { ch -> ch.isLetter() } }) {
            return CatalogFieldType.Select
        }
        return CatalogFieldType.Text
    }
}

fun JsonElement.asDisplayString(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> contentOrNull ?: booleanOrNull?.toString() ?: doubleOrNull?.toString()
    is JsonArray -> jsonArray.joinToString(", ") { it.asDisplayString().orEmpty() }
    is JsonObject -> jsonObject.toString()
}

fun JsonElement.asNumberOrNull(): Double? = when (this) {
    is JsonPrimitive -> doubleOrNull ?: contentOrNull?.toDoubleOrNull()
    else -> null
}

fun JsonElement.isImageLike(): Boolean {
    val value = asDisplayString()?.lowercase() ?: return false
    return value.startsWith("http://") ||
        value.startsWith("https://") ||
        value.startsWith("data:image/") ||
        value.startsWith("content://") ||
        value.endsWith(".jpg") ||
        value.endsWith(".jpeg") ||
        value.endsWith(".png") ||
        value.endsWith(".gif") ||
        value.endsWith(".webp") ||
        value.endsWith(".svg")
}
