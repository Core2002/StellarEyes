package `fun`.fifu.stellareyes.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import `fun`.fifu.stellareyes.FaceNet
import `fun`.fifu.stellareyes.ui.camera.base64UrlToBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt
import `fun`.fifu.stellareyes.NcnnController as ncnnController

@Serializable
data class StoredFace(
    val id: String,
    var vector: FloatArray,
    var name: String,
    val imageUri: String, // 示例：使用图片 URI
    val timestamp: Long
)

val TAG = "VectorSearchEngine"


object VectorSearchEngine {

    private const val DIM = 512
    private const val FILE_NAME = "vectors.json"

    private val vectors = mutableListOf<StoredFace>()

    fun clear() {
        vectors.clear()
    }

    fun add(
        id: String,
        vector: FloatArray,
        name: String,
        imageUri: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        require(vector.size == DIM) { "Vector must be $DIM-dimensional" }
        val safeVector = vector.map { v ->
            if (v.isNaN() || v.isInfinite()) 0f else v
        }.toFloatArray()
        vectors.add(StoredFace(id, normalize(safeVector), name, imageUri, timestamp))
    }

    fun searchTop1(query: FloatArray): Pair<String?, Float> {
        require(query.size == DIM) { "Query vector must be $DIM-dimensional" }
        if (vectors.isEmpty()) return Pair(null, -1.0f)

        val queryNorm = normalize(query)
        var bestScore = -1.0f
        var bestId: String? = null

        for (entry in vectors) {
            val score = cosineSimilarity(entry.vector, queryNorm)
//            Log.d("VectorSearchEngine", "score: $score")
            if (score >= bestScore) {
                bestScore = score
                bestId = entry.id
            }
        }

        return if (bestScore > 0.84f) Pair(bestId!!, bestScore) else Pair(null, bestScore)
    }

    fun saveToFile(context: Context) {
        val json = Json.encodeToString(vectors)
        File(context.filesDir, FILE_NAME).writeText(json)
    }

    fun loadFromFile(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            val json = file.readText()
            val restored = Json.decodeFromString<List<StoredFace>>(json)
            vectors.clear()
            vectors.addAll(restored)
        }
    }

    private fun cosineSimilarityFast(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }

    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        require(vec1.size == vec2.size) { "Vectors must be the same length" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]      // 向量点积
            normA += vec1[i] * vec1[i]           // vec1 的模长平方
            normB += vec2[i] * vec2[i]           // vec2 的模长平方
        }

        return if (normA == 0f || normB == 0f) 0f else dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(
            normB
        ))
    }

    fun normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.map { it * it }.sum())
        return if (norm == 0f || norm.isNaN()) {
            FloatArray(vec.size)
        } else {
            vec.map { it / norm }.toFloatArray()
        }
    }

    fun getAllEntries(): List<StoredFace> {
        return vectors.toList()
    }

    fun getEntrieById(id: String): StoredFace? {
        return vectors.find { it.id == id }
    }

    fun removeById(id: String) {
        vectors.removeAll { it.id == id }
    }

    fun getVectorById(id: String): FloatArray? {
        return vectors.find { it.id == id }?.vector
    }

    fun getNameById(id: String): String? {
        return vectors.find { it.id == id }?.name
    }

    fun updateName(faceId: String, newName: String) {
        val vector = vectors.find { it.id == faceId }
        if (vector != null) {
            vector.name = newName
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun updateVectors() {
        CoroutineScope(FaceNet.tfliteThread).launch {
            for (face in vectors) {
                val img_db = base64UrlToBitmap(face.imageUri)
                if (img_db != null) {
                    face.vector = FaceNet.getFaceEmbedding(img_db)
                    Log.i(TAG,face.id)
                }
            }
        }
    }

    fun getCount(): Int {
        return vectors.size
    }

    fun exportVectorsJsonToDownloads(context: Context): String {
        if (vectors.isEmpty()) {
            return "No vector data to export."
        }
        val jsonString = try {
            Json.encodeToString(vectors)
        } catch (e: Exception) {
            Log.e("VectorSearchEngine", "Error serializing vectors to JSON: ${e.message}", e)
            return "Error: Could not serialize data to JSON. ${e.localizedMessage}"
        }

        val fileName = "vectors_export_${System.currentTimeMillis()}.json"
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending until written
        }

        val uri = try {
            // For Android Q and above, MediaStore.Downloads.EXTERNAL_CONTENT_URI is preferred.
            // For older versions, this might still work or you might need a different approach if targeting them specifically.
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Log.e("VectorSearchEngine", "Error inserting file into MediaStore: ${e.message}", e)
            // Fallback for older devices if DIRECTORY_DOWNLOADS wasn't handled by insert or specific URIs are needed.
            // This fallback is basic; a more robust one might try different MediaStore URIs or direct path with checks.
            return "Error: Could not create file entry in MediaStore. ${e.localizedMessage}"
        }


        if (uri == null) {
            Log.e("VectorSearchEngine", "MediaStore URI is null, cannot save file.")
            return "Error: Could not get URI for saving the file."
        }

        return try {
            contentResolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) {
                    throw IOException("Failed to get output stream for URI: $uri")
                }
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0) // Mark as not pending anymore
            contentResolver.update(uri, contentValues, null, null)
            Log.d(
                "VectorSearchEngine",
                "Successfully exported vectors to Downloads using MediaStore. URI: $uri"
            )
            "Successfully exported to Downloads (via MediaStore)."
        } catch (e: IOException) {
            Log.e("VectorSearchEngine", "Error writing to MediaStore URI: ${e.message}", e)
            // Clean up if pending and failed
            try {
                contentResolver.delete(uri, null, null)
            } catch (deleteEx: Exception) { /* ignore */
            }
            "Error: Could not write to file via MediaStore. ${e.localizedMessage}"
        } catch (e: Exception) {
            Log.e(
                "VectorSearchEngine",
                "An unexpected error occurred during MediaStore export: ${e.message}",
                e
            )
            try {
                contentResolver.delete(uri, null, null)
            } catch (deleteEx: Exception) { /* ignore */
            }
            "Error: An unexpected error occurred during export. ${e.localizedMessage}"
        }
    }
}
