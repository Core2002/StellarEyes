import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `fun`.fifu.stellareyes.FaceNet
import `fun`.fifu.stellareyes.data.StoredFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface FaceRepository {
    fun getAllStoredFaces(): Flow<List<StoredFace>>
    fun deleteFace(faceId: String)
    fun findMostSimilarFace(vector: FloatArray): StoredFace?
    fun addFace(face: StoredFace)
    fun updateFaceName(faceId: String, newName: String)
    fun updateVectors()
}

class ManageFacesViewModel(private val faceRepository: FaceRepository) : ViewModel() {

    private val _storedFaces = MutableStateFlow<List<StoredFace>>(emptyList())
    val storedFaces: StateFlow<List<StoredFace>> = _storedFaces.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStoredFaces()
    }

    fun loadStoredFaces() {
        viewModelScope.launch {
            _isLoading.value = true
            _storedFaces.value = faceRepository.getAllStoredFaces().first()
//            kotlinx.coroutines.delay(500) // 模拟网络延迟
//            _storedFaces.value = sampleFaces + _storedFaces.value
            _isLoading.value = false
        }
    }

    fun deleteFace(faceId: String) {
        viewModelScope.launch {
            faceRepository.deleteFace(faceId)
            loadStoredFaces()
        }
    }

    fun updateFaceName(faceId: String, newName: String) {
        viewModelScope.launch {
            faceRepository.updateFaceName(faceId, newName)
            loadStoredFaces()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun updateVectors() {
        _isLoading.value = true
        CoroutineScope(FaceNet.tfliteThread).launch {
            faceRepository.updateVectors()
            viewModelScope.launch {
                loadStoredFaces()
            }
        }
        _isLoading.value = false
    }
}
