@file:OptIn(ExperimentalCoroutinesApi::class)

package `fun`.fifu.stellareyes

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import kotlinx.coroutines.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import org.tensorflow.lite.support.common.TensorOperator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object FaceNet {
    private const val TAG = "FACENET"
    private const val MODEL_FILENAME = "facenet_512.tflite"
    private const val IMG_SIZE = 160
    private const val EMBEDDING_DIM = 512

    // 用单线程协程调度器保证GPU delegate初始化和推理都在同一线程
    val tfliteThread = newSingleThreadContext("TFLiteThread")

    private lateinit var interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(IMG_SIZE, IMG_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    suspend fun initFaceNet(context: Context, useGpu: Boolean = true, useXNNPack: Boolean = true) {
        withContext(tfliteThread) {
            val options = Interpreter.Options().apply {
                useXNNPACK = useXNNPack
                useNNAPI = true
                if (useGpu) {
                    try {
                        val compatList = CompatibilityList()
                        if (compatList.isDelegateSupportedOnThisDevice) {
                            gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                            addDelegate(gpuDelegate)
                            Log.d(TAG, "GPU delegate enabled")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "GPU delegate init failed, fallback to CPU", e)
                    }
                } else {
                    numThreads = 4
                }
            }
            interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILENAME), options)
            Log.d(TAG, "Interpreter initialized")
        }
    }

    suspend fun getFaceEmbedding(image: Bitmap): FloatArray {
        return withContext(tfliteThread) {
            runFaceNet(convertBitmapToBuffer(image))[0]
        }
    }

    private fun runFaceNet(inputBuffer: ByteBuffer): Array<FloatArray> {
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        if (::interpreter.isInitialized) {
            interpreter.run(inputBuffer, output)
//            Log.d(TAG, "runFaceNet output: ${output.contentDeepToString()}")
        }
        return output
    }

    private fun convertBitmapToBuffer(image: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val scaledBitmap = if (image.width != IMG_SIZE || image.height != IMG_SIZE) {
            image.scale(IMG_SIZE, IMG_SIZE)
        } else {
            image
        }

        for (y in 0 until IMG_SIZE) {
            for (x in 0 until IMG_SIZE) {
                val pixel = scaledBitmap[x, y]
                val r = (pixel shr 16 and 0xFF).toFloat()
                val g = (pixel shr 8 and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()

                // 归一化到 [-1,1]
                byteBuffer.putFloat((r - 127.5f) / 128f)
                byteBuffer.putFloat((g - 127.5f) / 128f)
                byteBuffer.putFloat((b - 127.5f) / 128f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    class StandardizeOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt(pixels.map { (it - mean).pow(2) }.sum() / pixels.size)
            std = max(std, 1f / sqrt(pixels.size.toFloat()))
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] - mean) / std
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(pixels)
            return output
        }
    }

    fun close() {
        gpuDelegate?.close()
        interpreter.close()
        tfliteThread.close()
    }

    private var lastInferenceTime = 0L
    private val minIntervalMs = 100L  // 限制10FPS -> 1000ms/10 = 100ms每次推理

    suspend fun throttledGetFaceEmbedding(bitmap: Bitmap): FloatArray? {
        val now = System.currentTimeMillis()
        if (now - lastInferenceTime < minIntervalMs) {
            // 距离上次推理时间不足，跳过这次推理
            return null
        }
        lastInferenceTime = now
        return getFaceEmbedding(bitmap)
    }
}
